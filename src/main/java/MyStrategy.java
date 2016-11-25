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
    private boolean isInitialBonusCheck;
    private boolean shouldCheckForBonus;
    private boolean canCheckBonus;
    private boolean didSeeBonus;
    private boolean shouldReturnFromBonus;
    private boolean centralWayPointIsPassed;

    // SELF
    private LivingUnit nearestEnemyUnit;
    private LivingUnit nearestFriendUnit;
    private Tree nearestTree;
    private Minion nearestNeutralMinion;

    private Map<UnitType, ArrayDeque<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleFriendUnitsByMe;
    private ArrayDeque<LivingUnit> visibleTreesByMe;
    private ArrayDeque<LivingUnit> visibleNeutralMinionsByMe;

    private BraveryLevel braveryLevel;
    private Faction friendFaction;
    private Faction enemyFaction;

    private int previousTickLife;
    private int runAwayCountdown;
    private boolean shouldAttackNeutralMinion;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
        if (random == null) initializeStrategy(self, game);
        if (self.isMaster()) handleMasterManagement(self, world, game, move);
        if (world.getTickIndex() >= Constants.START_GAME_HOLD) {
            initializeTick(self, world, game, move);
            handleSkillLearning();
            handleActionExecution();
            handleMovement();
            previousTickLife = self.getLife();
            previousTickIndex = world.getTickIndex();
        }
    }

    private void initializeStrategy(Wizard self, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());
            friendFaction = self.getFaction();
            if (friendFaction == Faction.ACADEMY) enemyFaction = Faction.RENEGADES;
            else enemyFaction = Faction.ACADEMY;
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
            wayPoints.determineWayToGo(self, world, game, friendFaction);
            centralWayPointIsPassed = false;
            shouldReturnFromBonus = false;
        }

        int bonusInterval = game.getBonusAppearanceIntervalTicks();
        if (!shouldCheckForBonus &&
                bonusInterval - (world.getTickIndex() % bonusInterval) <= getTimeNeededToTakeBonus() - 60) {
            shouldCheckForBonus = true;
            canCheckBonus = false;
            didSeeBonus = false;
            shouldReturnFromBonus = false;
            centralWayPointIsPassed = false;
        }

        if (shouldCheckForBonus && !canCheckBonus) {
            canCheckBonus = areBonusesReachable();
            if (canCheckBonus) {
                if (wayPoints.getNextWayPointIndex() - 1 == 9 || wayPoints.getNextWayPointIndex() - 1 == 8) {
                    centralWayPointIsPassed = true;
                    wayPoints.setWayPointBeforeBonus(wayPoints.getClosestWayPoint());
                } else {
                    wayPoints.setWayPointBeforeBonus(wayPoints.getCentralPoint());
                }
            }
        }

        if (runAwayCountdown > 0) runAwayCountdown--;

        wayPoints.findTickWayPoints(self, random);
        lookAround();
        braveryLevel = getBraveryLevel();

        if (!shouldAttackNeutralMinion) shouldAttackNeutralMinion = shouldAttackNeutralMinion();
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
        } else if (shouldAttackNeutralMinion
                && isUnitInStaffRange(nearestNeutralMinion)
                && isUnitInStaffSector(nearestNeutralMinion)) {
            move.setAction(ActionType.STAFF);
            move.setCastAngle(self.getAngleTo(nearestNeutralMinion));
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

        switch (braveryLevel) {
            case RUN_FOREST_RUN:
                moveTowardsWayPointQuickly(wayPointToGo);
                break;
            case ENEMY_IN_CAST_RANGE:
                moveAgainstUnit(wayPointToGo, nearestEnemyUnit);
            default:
                moveTowardsWayPoint(wayPointToGo);
                break;
        }

//        else if (isUnitInStaffRange(nearestTree)) moveAgainstUnit(wayPointToGo, nearestTree);
        collisionHandler.handleSingleCollision(visibleUnitsByMe, self, move);
    }

    private void handleMasterManagement(Wizard self, World world, Game game, Move move) {
        Message[] messages = new Message[4];
        int index;
        for (Wizard wizard : world.getWizards()) {
            if (wizard.isMe() || wizard.getFaction() != self.getFaction()) continue;
            index = (int) wizard.getId() - 1;
            if (index >= self.getId()) index--;
            switch (index % 5) {
                case 0:
                case 1:
                    messages[index % 5] =
                            new Message(LaneType.TOP, SkillType.MAGICAL_DAMAGE_BONUS_PASSIVE_1, new byte[0]);
                    break;
                case 2:
                case 3:
                    messages[index % 5] =
                            new Message(LaneType.MIDDLE, SkillType.MAGICAL_DAMAGE_BONUS_PASSIVE_1, new byte[0]);
                    break;
                default:
                    messages[index % 5] =
                            new Message(LaneType.BOTTOM, SkillType.MAGICAL_DAMAGE_BONUS_PASSIVE_1, new byte[0]);
                    break;
            }
        }
        move.setMessages(messages);
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
        double nearestNeutralMinionDistance = Float.MAX_VALUE;
        Minion nearestNeutralMinion = null;
        boolean isNearestTreeStillVisible = false;
        boolean isNearestNeutralMinionIsStillVisible = false;

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
                        if (unit instanceof Minion) {
                            visibleNeutralMinionsByMe.add(unit);
                            if (!isNearestNeutralMinionIsStillVisible &&
                                    currentDistance + Constants.NEAREST_UNIT_ERROR < nearestNeutralMinionDistance) {
                                nearestNeutralMinionDistance = currentDistance;
                                nearestNeutralMinion = (Minion) unit;
                            }
                            if (isUnitInStaffRange(this.nearestNeutralMinion) &&
                                    this.nearestNeutralMinion.getId() == unit.getId())
                                isNearestNeutralMinionIsStillVisible = true;
                        }
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
        if (!isNearestNeutralMinionIsStillVisible) {
            this.nearestNeutralMinion = nearestNeutralMinion;
            shouldAttackNeutralMinion = false;
        }
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

    private void moveTowardsWayPointQuickly(Point2D wayPoint) {
        double distanceToPoint = self.getDistanceTo(wayPoint.getX(), wayPoint.getY());
        double angleToPoint = self.getAngleTo(wayPoint.getX(), wayPoint.getY());
        move.setTurn(angleToPoint);
        move.setSpeed(cos(angleToPoint) * distanceToPoint);
        move.setStrafeSpeed(sin(angleToPoint) * distanceToPoint);
    }

    private void moveTowardsWayPoint(Point2D wayPoint) {
        double distanceToPoint = self.getDistanceTo(wayPoint.getX(), wayPoint.getY());
        double angleToPoint = self.getAngleTo(wayPoint.getX(), wayPoint.getY());
        if (isUnitInCastRange(nearestEnemyUnit))
            move.setTurn(self.getAngleTo(nearestEnemyUnit));
        else if (isUnitInStaffRange(nearestTree))
            move.setTurn(self.getAngleTo(nearestTree));
        else if (shouldAttackNeutralMinion && isUnitInStaffRange(nearestNeutralMinion))
            move.setTurn(self.getAngleTo(nearestNeutralMinion));
        else move.setTurn(angleToPoint);
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
        if (braveryLevel == BraveryLevel.NEED_TO_GRAB_BONUS || shouldReturnFromBonus) pointToGo = getBonusWayPoint();
        else if (braveryLevel != BraveryLevel.I_AM_SUPERMAN)
            pointToGo = getLastSafePoint();
        else pointToGo = wayPoints.getNextWayPoint();
        return pointToGo;
    }

    // TODO: check for both bonuses at middle lane
    private Point2D getBonusWayPoint() {
        if (shouldReturnFromBonus) {
            if (wayPoints.getWayPointBeforeBonus().getDistanceTo(self) <= Constants.WAY_POINT_RADIUS)
                shouldReturnFromBonus = false;
            return wayPoints.getWayPointBeforeBonus();
        }

        Point2D wayPoint;
        Point2D centralPoint = wayPoints.getCentralPoint();
        Point2D[] bonusWayPoints = wayPoints.getBonusWayPoints();
        if (bonusWayPoints.length == 1) wayPoint = bonusWayPoints[0];
        else {
            if (bonusWayPoints[0].getDistanceTo(self) < bonusWayPoints[1].getDistanceTo(self))
                wayPoint = bonusWayPoints[0];
            else wayPoint = bonusWayPoints[1];
        }

        boolean isBonusInPlace = true;
        Bonus bonusToTake = null;
        for (Bonus visibleBonus : world.getBonuses()) {
            if (visibleBonus.getX() == wayPoint.getX()) {
                bonusToTake = visibleBonus;
                didSeeBonus = true;
                break;
            }
        }

        for (Wizard wizard : world.getWizards()) {
            if (wayPoint.getDistanceTo(wizard) +
                    Constants.VISION_ERROR + game.getBonusRadius() <= wizard.getVisionRange()) {
                if ((didSeeBonus && bonusToTake == null) ||
                        isInitialBonusCheck ||
                        (!didSeeBonus && ((world.getTickIndex() - 5) % game.getBonusAppearanceIntervalTicks() <= 1250))) {
                    isBonusInPlace = false;
                    didSeeBonus = true;
                }
            }
        }

        if (!centralWayPointIsPassed && centralPoint.getDistanceTo(self) <= Constants.WAY_POINT_RADIUS)
            centralWayPointIsPassed = true;

        if (!isBonusInPlace) {
            shouldCheckForBonus = false;
            canCheckBonus = false;
            if (isInitialBonusCheck) isInitialBonusCheck = false;
            shouldReturnFromBonus = true;
            wayPoint = wayPoints.getWayPointBeforeBonus();
        } else if (!centralWayPointIsPassed && centralPoint.getDistanceTo(self) > Constants.WAY_POINT_RADIUS) {
            wayPoint = centralPoint;
        } else if (!didSeeBonus) {
            double delta = game.getBonusRadius() + self.getRadius() + 0.1;
            if (wayPoints.getCurrentLane() == LaneType.TOP)
                wayPoint = new Point2D(wayPoint.getX() - delta, wayPoint.getY() - delta);
            else if (wayPoints.getCurrentLane() == LaneType.BOTTOM)
                wayPoint = new Point2D(wayPoint.getX() + delta, wayPoint.getY() + delta);
            else if (bonusWayPoints.length > 1) {
                if (wayPoint == bonusWayPoints[0])
                    wayPoint = new Point2D(wayPoint.getX() + delta, wayPoint.getY() + delta);
                else wayPoint = new Point2D(wayPoint.getX() - delta, wayPoint.getY() - delta);
            }
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

    private boolean shouldAttackNeutralMinion() {
        int count = 0;
        for (LivingUnit minion : visibleNeutralMinionsByMe) {
            if (collisionHandler.isColliding(minion, self)) count++;
        }
        return count > 1;
    }

    private int getTimeNeededToTakeBonus() {
        int closestIndex = wayPoints.getClosestWayPointIndex();
        Point2D wayPoint;
        Point2D[] bonusWayPoints = wayPoints.getBonusWayPoints();
        double speed = 3.0;
        if (bonusWayPoints.length == 1) wayPoint = bonusWayPoints[0];
        else wayPoint = bonusWayPoints[0].getDistanceTo(self) < bonusWayPoints[1].getDistanceTo(self) ?
                bonusWayPoints[0] : bonusWayPoints[1];
        if (closestIndex <= 10 && closestIndex >= 8) return (int) floor(wayPoint.getDistanceTo(self) / speed);
        else return (int) floor((wayPoints.getCentralPoint().getDistanceTo(self) +
                wayPoints.getCentralPoint().getDistanceTo(wayPoint) - 170.0) / speed);
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
        if ((self.getLife() <= 36 || previousTickLife - self.getLife() >= 23)
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

        if (canCheckBonus) return BraveryLevel.NEED_TO_GRAB_BONUS;
        if (isUnitInCastRange(nearestEnemyUnit)) return BraveryLevel.ENEMY_IN_CAST_RANGE;

        return BraveryLevel.I_AM_SUPERMAN;
    }

    enum BraveryLevel {
        I_AM_SUPERMAN, ENEMY_IN_CAST_RANGE,
        BEWARE_OF_MINIONS_NEAR_BASE, BETTER_TO_GO_BACK, RUN_FOREST_RUN, NEED_TO_GRAB_BONUS
    }
}