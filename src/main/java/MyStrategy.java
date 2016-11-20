import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public final class MyStrategy implements Strategy {
    private static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    private static final double NEAREST_UNIT_ERROR = 70.0;
    private static final double BRAVERY_ERROR = 100.0;
    private static final double VISION_ERROR = 0.1;

    private Random random;

    private Wizard self;
    private World world;
    private Game game;
    private Move move;

    // WORLD
    private WayPoints wayPoints;
    private CollisionHandler collisionHandler;
    private int previousTickIndex;
    private boolean shouldCheckForBonuses;
    private boolean canCheckBonus;

    // SELF
    private LivingUnit nearestEnemyUnit;
    private LivingUnit nearestFriendUnit;
    private Tree nearestTree;

    private Map<UnitType, ArrayDeque<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleFriendUnitsByMe;
    private ArrayDeque<LivingUnit> visibleTreesByMe;
    private ArrayDeque<LivingUnit> visibleNeutralMinionsByMe;

    private boolean isBraveEnough;
    private Faction friendFaction;
    private Faction enemyFaction;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
        if (world.getTickIndex() < 120) return;
        initializeStrategy(self, game);
        initializeTick(self, world, game, move);
        handleSkillLearning();
        handleActionExecution();
        handleMovement();
        handleMasterManagement();
    }

    private void initializeStrategy(Wizard self, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());
            friendFaction = self.getFaction();
            if (friendFaction == Faction.ACADEMY) enemyFaction = Faction.RENEGADES;
            else enemyFaction = Faction.ACADEMY;
            shouldCheckForBonuses = true;
            wayPoints = new WayPoints(self, game);
            collisionHandler = new CollisionHandler();
        }
    }

    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;

        if (world.getTickIndex() - previousTickIndex > 1 || !wayPoints.isLaneDetermined())
            wayPoints.determineWayToGo(world, game, friendFaction);
        previousTickIndex = world.getTickIndex();

        wayPoints.findNextWayPoint(self, random);
        wayPoints.findPreviousWayPoint(self, random);
        lookAround();
        isBraveEnough = isBraveEnough();

        if (world.getTickIndex() % game.getBonusAppearanceIntervalTicks() == 0) {
            shouldCheckForBonuses = true;
            canCheckBonus = false;
        }

        if (shouldCheckForBonuses && !canCheckBonus) canCheckBonus = areBonusesReachable();
    }

    private void handleSkillLearning() {
    }

    private void handleActionExecution() {
        if (isBraveEnough && isUnitInStaffSector(nearestEnemyUnit)
                && isUnitInCastRange(nearestEnemyUnit)) {
            move.setAction(ActionType.MAGIC_MISSILE);
            move.setCastAngle(self.getAngleTo(nearestEnemyUnit));
            move.setMinCastDistance(self.getDistanceTo(nearestEnemyUnit) -
                    nearestEnemyUnit.getRadius() + game.getMagicMissileRadius());
        } else if (isUnitInStaffSector(nearestTree) && isUnitInStaffRange(nearestTree)) {
            move.setAction(ActionType.STAFF);
            move.setCastAngle(self.getAngleTo(nearestTree));
        }
    }

    private void handleMovement() {
        ArrayDeque<LivingUnit> units = new ArrayDeque<>();
        for (UnitType unitType : LIVING_UNIT_TYPES) {
            units.addAll(visibleEnemyUnitsByMe.get(unitType));
            units.addAll(visibleFriendUnitsByMe.get(unitType));
        }
        units.addAll(visibleNeutralMinionsByMe);
        units.addAll(visibleTreesByMe);

        if (canCheckBonus) checkForBonuses();
        else if (isUnitInVisionRange(nearestEnemyUnit))
            moveAgainstUnit(wayPoints.getPreviousWayPoint(), nearestEnemyUnit);
        else if (isUnitInStaffRange(nearestTree)) moveAgainstUnit(wayPoints.getNextWayPoint(), nearestTree);
        else moveTowardsWayPoint(wayPoints.getNextWayPoint());

        collisionHandler.handleSingleCollision(units, self, move);
    }

    private void handleMasterManagement() {
    }

    /*
      ---------------------------------UTILS PART-----------------------------------------------------------------------
     */

    private void lookAround() {
        double currentDistance;
        double nearestEnemyDistance = Float.MAX_VALUE;
        LivingUnit nearestEnemy = null;
        double nearestFriendDistance = Float.MAX_VALUE;
        LivingUnit nearestFriend = null;
        double nearestTreeDistance = Float.MAX_VALUE;
        Tree nearestTree = null;
        boolean isNearestTreeStillVisible = false;

        visibleEnemyUnitsByMe = new EnumMap<>(UnitType.class);
        visibleFriendUnitsByMe = new EnumMap<>(UnitType.class);
        visibleTreesByMe = new ArrayDeque<>();
        visibleNeutralMinionsByMe = new ArrayDeque<>();

        LivingUnit[] units;

        for (UnitType unitType : LIVING_UNIT_TYPES) {
            visibleEnemyUnitsByMe.put(unitType, new ArrayDeque<>());
            visibleFriendUnitsByMe.put(unitType, new ArrayDeque<>());

            switch (unitType) {
                case WIZARD:
                    units = world.getWizards();
                    break;
                case MINION:
                    units = world.getMinions();
                    break;
                case BUILDING:
                    units = world.getBuildings();
                    break;
                case TREE:
                    units = world.getTrees();
                    break;
                default:
                    throw new IllegalArgumentException(String.format("%s is not living unit type.", unitType));
            }

            for (LivingUnit unit : units) {
                currentDistance = self.getDistanceTo(unit);
                if (!isUnitInVisionRange(unit)
                        || (unit instanceof Wizard && ((Wizard) unit).isMe())) continue;
                switch (unit.getFaction()) {
                    case RENEGADES:
                        if (friendFaction == Faction.RENEGADES) {
                            visibleFriendUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + NEAREST_UNIT_ERROR < nearestFriendDistance) {
                                nearestFriendDistance = currentDistance;
                                nearestFriend = unit;
                            }
                        } else {
                            visibleEnemyUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                                nearestEnemyDistance = currentDistance;
                                nearestEnemy = unit;
                            }
                        }
                        break;
                    case ACADEMY:
                        if (friendFaction == Faction.RENEGADES) {
                            visibleEnemyUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                                nearestEnemyDistance = currentDistance;
                                nearestEnemy = unit;
                            }
                        } else {
                            visibleFriendUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + NEAREST_UNIT_ERROR < nearestFriendDistance) {
                                nearestFriendDistance = currentDistance;
                                nearestFriend = unit;
                            }
                        }
                        break;
                    case NEUTRAL:
                        visibleNeutralMinionsByMe.add(unit);
                        break;
                    case OTHER:
                        if (unit instanceof Tree) {
                            visibleTreesByMe.add(unit);
                            if (!isNearestTreeStillVisible &&
                                    currentDistance + NEAREST_UNIT_ERROR < nearestTreeDistance) {
                                nearestTreeDistance = currentDistance;
                                nearestTree = (Tree) unit;
                            }
                            if (isUnitInStaffRange(this.nearestTree) &&
                                    this.nearestTree.getId() == unit.getId()) isNearestTreeStillVisible = true;
                        }
                        break;
                }
            }
        }
        this.nearestEnemyUnit = nearestEnemy;
        this.nearestFriendUnit = nearestFriend;
        if (!isNearestTreeStillVisible) this.nearestTree = nearestTree;
    }

    private double getRetreatDelta(Point2D pointForRetreat, LivingUnit unit) {
        double cosAlpha = cos(abs(self.getAngleTo(pointForRetreat.getX(), pointForRetreat.getY())));
        double a = self.getDistanceTo(unit);
        double b = self.getCastRange();
        return a * cosAlpha + sqrt(a * a * (cosAlpha * cosAlpha - 1) + b * b);
    }

    private void moveAgainstUnit(Point2D wayPoint, LivingUnit unit) {
        double retreatDelta = getRetreatDelta(wayPoint, unit);
        double retreatAngle = self.getAngleTo(wayPoint.getX(), wayPoint.getY());

        if ((isBraveEnough && isUnitInCastRange(unit)) || unit instanceof Tree)
            move.setStrafeSpeed(sin(retreatAngle) * retreatDelta);

        if (isBraveEnough || unit instanceof Tree) move.setTurn(self.getAngleTo(unit));
        else move.setTurn(retreatAngle);

        if ((isBraveEnough && isUnitInCastRange(unit)) || (unit instanceof Tree && isUnitInStaffRange(unit)))
            move.setSpeed(cos(retreatAngle) * retreatDelta);
        else if (isBraveEnough && !(unit instanceof Tree)) move.setSpeed(self.getDistanceTo(unit));
        else move.setSpeed(self.getDistanceTo(wayPoint.getX(), wayPoint.getY()));
    }

    private void moveTowardsWayPoint(Point2D wayPoint) {
        move.setTurn(self.getAngleTo(wayPoint.getX(), wayPoint.getY()));
        move.setSpeed(self.getDistanceTo(wayPoint.getX(), wayPoint.getY()));
    }

    private boolean areBonusesReachable() {
        return ((wayPoints.getCurrentLane() != LaneType.MIDDLE &&
                wayPoints.getNextWayPointIndex() - 1 == 10)
//                wayPoints.getNextWayPointIndex() - 1 >= 9 &&
//                wayPoints.getNextWayPointIndex() - 1 <= 13)
                || (wayPoints.getCurrentLane() == LaneType.MIDDLE &&
                wayPoints.getNextWayPointIndex() - 1 == 9));
//                wayPoints.getNextWayPointIndex() - 1 >= 8 &&
//                wayPoints.getNextWayPointIndex() - 1 <= 10);
    }

    // TODO: check for both bonuses at middle lane
    private void checkForBonuses() {
        Point2D wayPoint;
        Point2D[] bonusWayPoints = wayPoints.getBonusWayPoints();
        if (bonusWayPoints.length == 1) wayPoint = bonusWayPoints[0];
        else wayPoint = bonusWayPoints[0].getDistanceTo(self) < bonusWayPoints[1].getDistanceTo(self)
                ? bonusWayPoints[0] : bonusWayPoints[1];

        boolean isBonusInPlace = true;
        Bonus bonusToTake = null;
        for (Bonus visibleBonus : world.getBonuses()) {
            if (visibleBonus.getX() == wayPoint.getX()) {
                bonusToTake = visibleBonus;
                break;
            }
        }

        for (Wizard wizard : world.getWizards()) {
            if (wayPoint.getDistanceTo(wizard) + VISION_ERROR + game.getBonusRadius() + 100.0 <= wizard.getVisionRange()) {
                if (bonusToTake == null) isBonusInPlace = false;
            }
        }

        if (isBonusInPlace) {
            if (isUnitInCastRange(nearestEnemyUnit)) moveAgainstUnit(wayPoint, nearestEnemyUnit);
            else if (isUnitInStaffRange(nearestTree)) moveAgainstUnit(wayPoint, nearestTree);
            else moveTowardsWayPoint(wayPoint);
        } else {
            shouldCheckForBonuses = false;
            canCheckBonus = false;
        }
    }

//    // TODO: review
//    private double getSpeedVectorNorm() {
//        double speed = move.getSpeed();
//        double maxSpeed = speed >= 0.0 ? game.getWizardForwardSpeed() : game.getWizardBackwardSpeed();
//        double strafeSpeed = move.getStrafeSpeed();
//        double maxStrafeSpeed = game.getWizardStrafeSpeed();
//        double border = sqrt(pow(speed / maxSpeed, 2) + pow(strafeSpeed / maxStrafeSpeed, 2));
//        if (border > 1.0) {
//            speed /= border;
//            strafeSpeed /= border;
//        }
//        return hypot(strafeSpeed, speed);
//    }

    private boolean isUnitInVisionRange(Unit unit) {
        return unit != null && self.getDistanceTo(unit) + VISION_ERROR <= self.getVisionRange();
    }

    private boolean isUnitInStaffSector(Unit unit) {
        return unit != null && abs(self.getAngleTo(unit)) <= game.getStaffSector() / 2.0;
    }

    private boolean isUnitInCastRange(Unit unit) {
        return unit != null && self.getDistanceTo(unit) + VISION_ERROR <= self.getCastRange();
    }

    private boolean isUnitInStaffRange(Unit unit) {
        if (unit != null && unit instanceof LivingUnit)
            return self.getDistanceTo(unit) - ((LivingUnit) unit).getRadius() <= 70.0;
        return unit != null && self.getDistanceTo(unit) <= 70.0;
    }

    private boolean isBraveEnough() {
        boolean isBraveEnough = true;

        if (nearestEnemyUnit != null) {
            double distanceToEnemy = self.getDistanceTo(nearestEnemyUnit) + BRAVERY_ERROR;
            double[] closestDists = new double[3];
            Arrays.fill(closestDists, Double.MAX_VALUE);
            double currentDist;
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.WIZARD)) {
                currentDist = unit.getDistanceTo(nearestEnemyUnit);
                if (currentDist < closestDists[0]) {
                    closestDists[0] = currentDist;
                }
            }
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.MINION)) {
                currentDist = unit.getDistanceTo(nearestEnemyUnit);
                if (currentDist < closestDists[1]) {
                    closestDists[1] = currentDist;
                }
            }
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.BUILDING)) {
                currentDist = unit.getDistanceTo(nearestEnemyUnit);
                if (currentDist < closestDists[2]) {
                    closestDists[2] = currentDist;
                }
            }
            if (closestDists[0] > distanceToEnemy
                    && closestDists[1] > distanceToEnemy
                    && closestDists[2] > distanceToEnemy) isBraveEnough = false;
        }
        // TODO: think about this check
//            if (self.getLife() < self.getMaxLife() * LOW_HP_FACTOR && previousWayPoint != wayPoints[0])
//                isBraveEnough = false;
//        }

        return isBraveEnough;
    }
}