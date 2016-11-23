import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public final class MyStrategy implements Strategy {
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
    private boolean shouldReturnFromBonus;
    private boolean centralWayPointIsPassed;

    // SELF
    private LivingUnit nearestEnemyUnit;
    private LivingUnit nearestFriendUnit;
    private Tree nearestTree;

    private Map<UnitType, ArrayDeque<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleFriendUnitsByMe;
    private ArrayDeque<LivingUnit> visibleTreesByMe;
    private ArrayDeque<LivingUnit> visibleNeutralMinionsByMe;

    private BraveryLevel braveryLevel;
    private Faction friendFaction;
    private Faction enemyFaction;

    private int previousTickLife;
    private int runAwayCountdown;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
        if (world.getTickIndex() < 120) return;
        initializeStrategy(self, game);
        initializeTick(self, world, game, move);
        handleSkillLearning();
        handleActionExecution();
        handleMovement();
        handleMasterManagement();
        previousTickLife = self.getLife();
        previousTickIndex = world.getTickIndex();
    }

    private void initializeStrategy(Wizard self, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());
            friendFaction = self.getFaction();
            if (friendFaction == Faction.ACADEMY) enemyFaction = Faction.RENEGADES;
            else enemyFaction = Faction.ACADEMY;
            shouldCheckForBonuses = false;
            wayPoints = new WayPoints(self, game);
            collisionHandler = new CollisionHandler();
        }
    }

    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;

        if (world.getTickIndex() - previousTickIndex > 1 || !wayPoints.isLaneDetermined()) {
            wayPoints.determineWayToGo(world, game, friendFaction);
            centralWayPointIsPassed = false;
            shouldReturnFromBonus = false;
        }

        wayPoints.findTickWayPoints(self, random);
        lookAround();
        braveryLevel = getBraveryLevel();

        if (world.getTickIndex() % game.getBonusAppearanceIntervalTicks() == 0) {
            shouldCheckForBonuses = true;
            canCheckBonus = false;
            centralWayPointIsPassed = false;
        }

        if (shouldCheckForBonuses && !canCheckBonus) canCheckBonus = areBonusesReachable();
        if (runAwayCountdown > 0) runAwayCountdown--;
    }

    private void handleSkillLearning() {
    }

    private void handleActionExecution() {
        if (isUnitInStaffSector(nearestEnemyUnit)
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
        ArrayDeque<LivingUnit> visibleUnitsByMe = new ArrayDeque<>();
        for (UnitType unitType : Constants.LIVING_UNIT_TYPES) {
            visibleUnitsByMe.addAll(visibleEnemyUnitsByMe.get(unitType));
            visibleUnitsByMe.addAll(visibleFriendUnitsByMe.get(unitType));
        }
        visibleUnitsByMe.addAll(visibleNeutralMinionsByMe);
        visibleUnitsByMe.addAll(visibleTreesByMe);

        Point2D wayPointToGo = getWayPointToGo();

        if (isUnitInCastRange(nearestEnemyUnit))
            moveAgainstUnit(wayPointToGo, nearestEnemyUnit);
        else if (isUnitInStaffRange(nearestTree)) moveAgainstUnit(wayPointToGo, nearestTree);
        else moveTowardsWayPoint(wayPointToGo);

        collisionHandler.handleSingleCollision(visibleUnitsByMe, self, move);
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

        for (UnitType unitType : Constants.LIVING_UNIT_TYPES) {
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
                            if (currentDistance + Constants.NEAREST_UNIT_ERROR < nearestFriendDistance) {
                                nearestFriendDistance = currentDistance;
                                nearestFriend = unit;
                            }
                        } else {
                            visibleEnemyUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + Constants.NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                                nearestEnemyDistance = currentDistance;
                                nearestEnemy = unit;
                            }
                        }
                        break;
                    case ACADEMY:
                        if (friendFaction == Faction.RENEGADES) {
                            visibleEnemyUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + Constants.NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                                nearestEnemyDistance = currentDistance;
                                nearestEnemy = unit;
                            }
                        } else {
                            visibleFriendUnitsByMe.get(unitType).add(unit);
                            if (currentDistance + Constants.NEAREST_UNIT_ERROR < nearestFriendDistance) {
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
                                    currentDistance + Constants.NEAREST_UNIT_ERROR < nearestTreeDistance) {
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
        double speedVectorNorm;
        if (!(unit instanceof Tree) &&
                (braveryLevel == BraveryLevel.I_AM_SUPERMAN || braveryLevel == BraveryLevel.ENEMY_IN_CAST_RANGE))
            speedVectorNorm = getRetreatDelta(wayPoint, unit);
        else speedVectorNorm = wayPoint.getDistanceTo(self);

        double speedVectorAngle = self.getAngleTo(wayPoint.getX(), wayPoint.getY());

        move.setTurn(self.getAngleTo(unit));
        move.setSpeed(cos(speedVectorAngle) * speedVectorNorm);
        move.setStrafeSpeed(sin(speedVectorAngle) * speedVectorNorm);
    }

    private void moveTowardsWayPoint(Point2D wayPoint) {
        double distanceToPoint = self.getDistanceTo(wayPoint.getX(), wayPoint.getY());
        double angleToPoint = self.getAngleTo(wayPoint.getX(), wayPoint.getY());
        move.setTurn(angleToPoint);
        move.setSpeed(cos(angleToPoint) * distanceToPoint);
        move.setStrafeSpeed(sin(angleToPoint) * distanceToPoint);
    }

    private boolean areBonusesReachable() {
        return ((wayPoints.getCurrentLane() != LaneType.MIDDLE &&
//                wayPoints.getNextWayPointIndex() - 1 == 10)
                wayPoints.getNextWayPointIndex() - 1 >= 9 &&
                wayPoints.getNextWayPointIndex() - 1 <= 15)
                || (wayPoints.getCurrentLane() == LaneType.MIDDLE &&
//                wayPoints.getNextWayPointIndex() - 1 == 9));
                wayPoints.getNextWayPointIndex() - 1 >= 9 &&
                wayPoints.getNextWayPointIndex() - 1 <= 12));
    }

    private Point2D getWayPointToGo() {
        Point2D pointToGo;
        if (braveryLevel == BraveryLevel.NEED_TO_GRAB_BONUS) pointToGo = getBonusWayPoint();
        else if (shouldReturnFromBonus) {
            pointToGo = wayPoints.getCentralPoint();
            if (pointToGo.getDistanceTo(self) <= Constants.WAY_POINT_RADIUS) shouldReturnFromBonus = false;
        } else if (braveryLevel != BraveryLevel.I_AM_SUPERMAN)
            pointToGo = getLastSafePoint();
        else pointToGo = wayPoints.getNextWayPoint();
        return pointToGo;
    }

    // TODO: check for both bonuses at middle lane
    private Point2D getBonusWayPoint() {
        Point2D wayPoint;
        Point2D centralPoint = wayPoints.getCentralPoint();
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
            if (wayPoint.getDistanceTo(wizard) +
                    Constants.VISION_ERROR + game.getBonusRadius() + 100.0 <= wizard.getVisionRange()) {
                if (bonusToTake == null) isBonusInPlace = false;
            }
        }

        if (!centralWayPointIsPassed &&
                centralPoint.getDistanceTo(self) <= Constants.WAY_POINT_RADIUS) centralWayPointIsPassed = true;

        if (!isBonusInPlace) {
            shouldCheckForBonuses = false;
            canCheckBonus = false;
            if (centralWayPointIsPassed) shouldReturnFromBonus = true;
            wayPoint = centralPoint;
        } else if (!centralWayPointIsPassed && centralPoint.getDistanceTo(self) > Constants.WAY_POINT_RADIUS) {
            wayPoint = centralPoint;
        }

        return wayPoint;
    }

    private Point2D getLastSafePoint() {
        if (braveryLevel == BraveryLevel.RUN_FOREST_RUN) {
            if (nearestEnemyUnit != null)
                return wayPoints.getCurrentLaneWayPoints()
                        [max(wayPoints.getNearestEnemyWayPointIndex(nearestEnemyUnit) - 2, 0)];
            else return wayPoints.getPreviousWayPoint();
        } else if (braveryLevel == BraveryLevel.BEWARE_OF_MINIONS_NEAR_BASE) {
            Point2D[] currentWayPoints = wayPoints.getCurrentLaneWayPoints();
            return currentWayPoints[currentWayPoints.length - 5];
        } else {
            return wayPoints.getPreviousWayPoint();
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
        return unit != null && self.getDistanceTo(unit) + Constants.VISION_ERROR <= self.getVisionRange();
    }

    private boolean isUnitInStaffSector(Unit unit) {
        return unit != null && abs(self.getAngleTo(unit)) <= game.getStaffSector() / 2.0;
    }

    private boolean isUnitInCastRange(Unit unit) {
        return unit != null && self.getDistanceTo(unit) + Constants.VISION_ERROR <= self.getCastRange();
    }

    private boolean isUnitInStaffRange(Unit unit) {
        if (unit != null && unit instanceof LivingUnit)
            return self.getDistanceTo(unit) - ((LivingUnit) unit).getRadius() <= 70.0;
        return unit != null && self.getDistanceTo(unit) <= 70.0;
    }

    private BraveryLevel getBraveryLevel() {
        if ((self.getLife() <= 25 || previousTickLife - self.getLife() >= 23)
                && wayPoints.getPreviousWayPointIndex() > 0) {
            runAwayCountdown = Constants.RUN_AWAY_COUNTDOWN;
            return BraveryLevel.RUN_FOREST_RUN;
        } else if (runAwayCountdown > 0 && wayPoints.getPreviousWayPointIndex() > 0)
            return BraveryLevel.RUN_FOREST_RUN;

        Point2D[] currentWayPoints = wayPoints.getCurrentLaneWayPoints();
        if (wayPoints.getClosestWayPointIndex() >= currentWayPoints.length - 5) {
            Point2D lastSafeWayPoint = currentWayPoints[currentWayPoints.length - 5];
            double distanceToLastSafePoint = self.getDistanceTo(lastSafeWayPoint.getX(), lastSafeWayPoint.getY());
            int minionAppearanceInterval = game.getFactionMinionAppearanceIntervalTicks();
            if ((minionAppearanceInterval - (world.getTickIndex() % minionAppearanceInterval) - 4.0
                    <= (distanceToLastSafePoint / 3.0)))
                return BraveryLevel.BEWARE_OF_MINIONS_NEAR_BASE;
        }

        if (nearestEnemyUnit != null) {
            double distanceToEnemy = self.getDistanceTo(nearestEnemyUnit) + Constants.BRAVERY_ERROR;
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
                    && closestDists[2] > distanceToEnemy) return BraveryLevel.BETTER_TO_GO_BACK;
        }

        if (isUnitInCastRange(nearestEnemyUnit)) return BraveryLevel.ENEMY_IN_CAST_RANGE;

        if (canCheckBonus) return BraveryLevel.NEED_TO_GRAB_BONUS;

        return BraveryLevel.I_AM_SUPERMAN;
    }

    enum BraveryLevel {
        I_AM_SUPERMAN, ENEMY_IN_CAST_RANGE,
        BEWARE_OF_MINIONS_NEAR_BASE, BETTER_TO_GO_BACK, RUN_FOREST_RUN, NEED_TO_GRAB_BONUS
    }
}