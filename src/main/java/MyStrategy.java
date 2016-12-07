import model.*;

import java.util.*;

import static java.lang.Math.*;

@SuppressWarnings("WeakerAccess")
public final class MyStrategy implements Strategy {
    private Random random;
    private PFAgent pfAgent;
    private Wizard self;
    private World world;
    private Game game;
    private Move move;
    // WORLD
    private WayPoints wayPoints;
    private int previousTickIndex;
    private boolean shouldCheckForBonus;
    private boolean canCheckBonus;
    private boolean didSeeBonus;
    private boolean shouldReturnFromBonus;
    private boolean centralWayPointIsPassed;
    // SELF
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleFriendUnitsByMe;
    private ArrayDeque<Tree> visibleTreesByMe;
    private ArrayDeque<Minion> visibleNeutralMinionsByMe;
    private Tree nearestTree;
    private LivingUnit nearestEnemyUnit;
    private LivingUnit targetToAttack;
    private LivingUnit enemyUnitInSafeRange;
    private BraveryLevel braveryLevel;
    private Faction friendFaction;
    private int previousTickLife;
    private int runAwayCountdown;
    private HashSet<SkillType> skills;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
        if (random == null) initializeStrategy(self, game);
        if (self.isMaster()) handleMasterManagement(self, world, game, move);
        if (world.getTickIndex() >= Constants.START_GAME_HOLD) {
            initializeTick(self, world, game, move);
            if (game.isSkillsEnabled()) handleSkillLearning();
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
            wayPoints = new WayPoints(self, game);
            skills = new HashSet<>();
            pfAgent = new PFAgent();
        }
    }

    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;

        if (world.getTickIndex() - previousTickIndex > 1 || !wayPoints.isLaneDetermined()) {
            wayPoints.determineWayToGo(self, world, game, friendFaction);
            wayPoints.findTickWayPoints(self, random);
            shouldCheckForBonus = false;
            canCheckBonus = false;
            didSeeBonus = false;
            shouldReturnFromBonus = false;
            centralWayPointIsPassed = false;
        }

        int bonusInterval = game.getBonusAppearanceIntervalTicks();
        if (!shouldCheckForBonus && world.getTickIndex() <= 17600 &&
                bonusInterval - (world.getTickIndex() % bonusInterval) <= getTimeNeededToTakeBonus() - 70) {
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
        nearestEnemyUnit = getNearestEnemyUnit().orElse(null);
        enemyUnitInSafeRange = getEnemyUnitInSafeRange().orElse(null);
        nearestTree = getNearestTree().orElse(null);
        targetToAttack = getTargetToAttack().orElse(null);
        if (isUnitInSafeRange(nearestEnemyUnit)) {
            enemyUnitInSafeRange = nearestEnemyUnit;
            targetToAttack = nearestEnemyUnit;
        }
        braveryLevel = getBraveryLevel();
    }

    private void handleSkillLearning() {
        skills = new HashSet<>(Arrays.asList(self.getSkills()));
        for (int i = 0; i < Constants.SKILLS_TO_LEARN.length; i++) {
            if (!skills.contains(Constants.SKILLS_TO_LEARN[i])) {
                move.setSkillToLearn(Constants.SKILLS_TO_LEARN[i]);
                break;
            }
        }
    }

    private void handleActionExecution() {
        if (Utils.canUseFireBall(self, game, skills)) {
            LivingUnit potentialTarget = Utils.getFireBallTarget(self, world).orElse(null);
            if (potentialTarget != null) targetToAttack = potentialTarget;
        }
        if (Utils.isUnitInStaffSector(self, targetToAttack, game)) {
            if (Utils.isUnitInStaffRange(self, targetToAttack)) {
                move.setAction(ActionType.STAFF);
                move.setCastAngle(self.getAngleTo(targetToAttack));
            } else if (Utils.isUnitInCastRange(self, targetToAttack)) {
                if (Utils.canUseFireBall(self, game, skills)) {
                    move.setAction(ActionType.FIREBALL);
                    move.setMinCastDistance(self.getDistanceTo(targetToAttack) -
                            targetToAttack.getRadius() + game.getFireballRadius());
                } else {
                    move.setAction(ActionType.MAGIC_MISSILE);
                    move.setMinCastDistance(self.getDistanceTo(targetToAttack) -
                            targetToAttack.getRadius() + game.getMagicMissileRadius());
                }
                move.setCastAngle(self.getAngleTo(targetToAttack));
            }
        }
    }

    private void handleMovement() {
        Point2D wayPointToGo = pfAgent.handleNextTick(self, world, game, getWayPointToGo());

        switch (braveryLevel) {
            case ENEMY_IN_SAFE_RANGE:
                moveAgainstUnitInSafeRange(wayPointToGo);
            default:
                moveTowardsWayPoint(wayPointToGo);
                break;
        }
    }

    private void handleMasterManagement(Wizard self, World world, Game game, Move move) {
        Message[] messages = new Message[4];
        int index;
        for (Wizard wizard : world.getWizards()) {
            if (wizard.isMe() || wizard.getFaction() != self.getFaction()) continue;
            index = (int) wizard.getId() - 1;
            if (index >= self.getId()) index--;

            HashSet<SkillType> skills = new HashSet<>(Arrays.asList(wizard.getSkills()));
            SkillType skillToLearn = Constants.SKILLS_TO_LEARN[0];
            for (int i = 0; i < Constants.SKILLS_TO_LEARN.length; i++) {
                if (!skills.contains(Constants.SKILLS_TO_LEARN[i])) {
                    skillToLearn = Constants.SKILLS_TO_LEARN[i];
                    break;
                }
            }

            switch (index % 5) {
                case 0:
                case 1:
                    messages[index % 5] =
                            new Message(LaneType.TOP, skillToLearn, new byte[0]);
                    break;
                case 2:
                case 3:
                    messages[index % 5] =
                            new Message(LaneType.MIDDLE, skillToLearn, new byte[0]);
                    break;
                default:
                    messages[index % 5] =
                            new Message(LaneType.BOTTOM, skillToLearn, new byte[0]);
                    break;
            }
        }
        move.setMessages(messages);
    }

    /*
      ---------------------------------UTILS PART-----------------------------------------------------------------------
     */

    private void lookAround() {
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
                if (!Utils.isUnitInVisionRange(self, unit)
                        || (unit instanceof Wizard && ((Wizard) unit).isMe())) continue;
                switch (unit.getFaction()) {
                    case RENEGADES:
                        if (friendFaction == Faction.RENEGADES) visibleFriendUnitsByMe.get(unitType).add(unit);
                        else visibleEnemyUnitsByMe.get(unitType).add(unit);
                        break;
                    case ACADEMY:
                        if (friendFaction == Faction.RENEGADES) visibleEnemyUnitsByMe.get(unitType).add(unit);
                        else visibleFriendUnitsByMe.get(unitType).add(unit);
                        break;
                    case NEUTRAL:
                        if (unit instanceof Minion) visibleNeutralMinionsByMe.add((Minion) unit);
                        break;
                    case OTHER:
                        if (unit instanceof Tree) visibleTreesByMe.add((Tree) unit);
                        break;
                }
            }
        }
    }

    private Optional<LivingUnit> getNearestEnemyUnit() {
        double currentDistance;
        double nearestEnemyDistance = Float.MAX_VALUE;
        LivingUnit nearestEnemy = null;

        for (UnitType unitType : Constants.LIVING_UNIT_TYPES) {
            for (LivingUnit unit : visibleEnemyUnitsByMe.get(unitType)) {
                currentDistance = self.getDistanceTo(unit);
                if (currentDistance + Constants.NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                    nearestEnemyDistance = currentDistance;
                    nearestEnemy = unit;
                }
            }
        }

        return Optional.ofNullable(nearestEnemy);
    }

    private Optional<LivingUnit> getEnemyUnitInSafeRange() {
        for (Building building : world.getBuildings())
            if (building.getFaction() != self.getFaction() && isUnitInSafeRange(building)) {
                return Optional.of(building);
            }
        for (Wizard wizard : world.getWizards())
            if (!wizard.isMe() && wizard.getFaction() != self.getFaction()
                    && isUnitInSafeRange(wizard)) return Optional.of(wizard);
        for (Minion minion : world.getMinions())
            if (minion.getFaction() != self.getFaction() && minion.getFaction() != Faction.NEUTRAL &&
                    isUnitInSafeRange(minion)) return Optional.of(minion);

        return Optional.empty();
    }

    private Optional<Tree> getNearestTree() {
        double currentDistance;
        double nearestTreeDistance = Float.MAX_VALUE;
        Tree nearestTree = null;
        boolean isNearestTreeStillVisible = false;

        for (Tree tree : visibleTreesByMe) {
            currentDistance = self.getDistanceTo(tree);
            if (!isNearestTreeStillVisible &&
                    currentDistance + Constants.NEAREST_UNIT_ERROR < nearestTreeDistance) {
                nearestTreeDistance = currentDistance;
                nearestTree = tree;
            }
            if (Utils.isUnitInStaffRange(self, this.nearestTree) &&
                    this.nearestTree.getId() == tree.getId()) isNearestTreeStillVisible = true;
        }

        if (isNearestTreeStillVisible) nearestTree = this.nearestTree;
        return Optional.ofNullable(nearestTree);
    }

    private Optional<LivingUnit> getTargetToAttack() {
        if (Utils.isUnitInCollisionRange(self, nearestTree)) return Optional.of(nearestTree);

        LivingUnit weakestEnemyInCastRange = getWeakestEnemyInCastRange().orElse(null);
        if (weakestEnemyInCastRange != null) return Optional.of(weakestEnemyInCastRange);

        return Optional.ofNullable(getAggressiveNeutralMinion().orElse(null));
    }

    private Optional<LivingUnit> getWeakestEnemyInCastRange() {
        for (LivingUnit building : visibleEnemyUnitsByMe.get(UnitType.BUILDING)) {
            if (Utils.isUnitInCastRange(self, building)) {
                if (((Building) building).getType() == BuildingType.FACTION_BASE ||
                        Utils.isTowerInMyLane(wayPoints, (Building) building)) {
                    return Optional.of(building);
                }
            }
        }

        Wizard weakestEnemyWizardInCastRange = getWeakestEnemyWizardInCastRange().orElse(null);
        if (weakestEnemyWizardInCastRange != null) return Optional.of(weakestEnemyWizardInCastRange);

        Minion weakestEnemyMinionInCastRange =
                getWeakestEnemyMinionInCastRange(MinionType.ORC_WOODCUTTER).orElse(null);
        if (weakestEnemyMinionInCastRange != null) return Optional.of(weakestEnemyMinionInCastRange);
        return Optional.ofNullable(getWeakestEnemyMinionInCastRange(MinionType.FETISH_BLOWDART).orElse(null));
    }

    private Optional<Wizard> getWeakestEnemyWizardInCastRange() {
        int currentLife;
        int weakestEnemyWizardLife = Integer.MAX_VALUE / 2;
        Wizard weakestEnemyWizard = null;

        for (LivingUnit wizard : visibleEnemyUnitsByMe.get(UnitType.WIZARD)) {
            if (Utils.isUnitInCastRange(self, wizard)) {
                currentLife = wizard.getLife();
                if (currentLife < weakestEnemyWizardLife) {
                    weakestEnemyWizardLife = currentLife;
                    weakestEnemyWizard = (Wizard) wizard;
                }
            }
        }

        return Optional.ofNullable(weakestEnemyWizard);
    }

    private Optional<Minion> getWeakestEnemyMinionInCastRange(MinionType minionType) {
        int currentLife;
        int weakestEnemyMinionLife = Integer.MAX_VALUE / 2;
        Minion weakestEnemyMinion = null;

        for (LivingUnit minion : visibleEnemyUnitsByMe.get(UnitType.MINION)) {
            if (((Minion) minion).getType() != minionType) continue;
            if (Utils.isUnitInCastRange(self, minion)) {
                currentLife = minion.getLife();
                if (currentLife < weakestEnemyMinionLife) {
                    weakestEnemyMinionLife = currentLife;
                    weakestEnemyMinion = (Minion) minion;
                }
            }
        }

        return Optional.ofNullable(weakestEnemyMinion);
    }

    private void moveAgainstUnitInSafeRange(Point2D wayPoint) {
        double speedVectorNorm;
        if (Utils.isUnitInVisionRange(self, enemyUnitInSafeRange) &&
                (braveryLevel == BraveryLevel.I_AM_SUPERMAN || braveryLevel == BraveryLevel.ENEMY_IN_SAFE_RANGE)) {
            double cosAlpha = cos(abs(self.getAngleTo(wayPoint.getX(), wayPoint.getY())));
            double a = self.getDistanceTo(enemyUnitInSafeRange);
            double b = getSafeRange(enemyUnitInSafeRange);
            speedVectorNorm = a * cosAlpha + sqrt(a * a * (cosAlpha * cosAlpha - 1) + b * b);
        } else speedVectorNorm = wayPoint.getDistanceTo(self);

        double speedVectorAngle = self.getAngleTo(wayPoint.getX(), wayPoint.getY());

        if (Utils.isUnitInCastRange(self, targetToAttack)) move.setTurn(self.getAngleTo(targetToAttack));
        else move.setTurn(speedVectorAngle);
        move.setSpeed(cos(speedVectorAngle) * speedVectorNorm);
        move.setStrafeSpeed(sin(speedVectorAngle) * speedVectorNorm);
    }

    private void moveTowardsWayPoint(Point2D wayPoint) {
        double distanceToPoint = self.getDistanceTo(wayPoint.getX(), wayPoint.getY());
        double angleToPoint = self.getAngleTo(wayPoint.getX(), wayPoint.getY());
        if (Utils.isUnitInVisionRange(self, targetToAttack))
            move.setTurn(self.getAngleTo(targetToAttack));
        else if (Utils.isUnitInVisionRange(self, enemyUnitInSafeRange))
            move.setTurn(self.getAngleTo(enemyUnitInSafeRange));
        else if (Utils.isUnitInVisionRange(self, nearestEnemyUnit))
            move.setTurn(self.getAngleTo(nearestEnemyUnit));
        else move.setTurn(angleToPoint);
        move.setSpeed(cos(angleToPoint) * distanceToPoint);
        move.setStrafeSpeed(sin(angleToPoint) * distanceToPoint);
    }

    private boolean areBonusesReachable() {
        return ((wayPoints.getCurrentLane() != LaneType.MIDDLE &&
                wayPoints.getNextWayPointIndex() - 1 >= 10 &&
                wayPoints.getNextWayPointIndex() - 1 <= 14)
                || (wayPoints.getCurrentLane() == LaneType.MIDDLE &&
                wayPoints.getNextWayPointIndex() - 1 >= 9 &&
                wayPoints.getNextWayPointIndex() - 1 <= 11));
    }

    private Point2D getWayPointToGo() {
        Point2D pointToGo;
        if (braveryLevel == BraveryLevel.NEED_TO_GRAB_BONUS || shouldReturnFromBonus) pointToGo = getBonusWayPoint();
        else if (braveryLevel != BraveryLevel.I_AM_SUPERMAN)
            pointToGo = getLastSafePoint();
        else if (Utils.isUnitInVisionRange(self, targetToAttack))
            pointToGo = new Point2D(targetToAttack.getX(), targetToAttack.getY());
        else if (Utils.isUnitInVisionRange(self, nearestEnemyUnit))
            pointToGo = new Point2D(nearestEnemyUnit.getX(), nearestEnemyUnit.getY());
        else pointToGo = wayPoints.getNextWayPoint();
        return pointToGo;
    }

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
            if (wayPoint.getDistanceTo(wizard) + game.getBonusRadius() <= wizard.getVisionRange()) {
                if ((didSeeBonus && bonusToTake == null) ||
                        (!didSeeBonus && ((world.getTickIndex() - 5) % game.getBonusAppearanceIntervalTicks() <= 2000))) {
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
            shouldReturnFromBonus = true;
            wayPoint = wayPoints.getWayPointBeforeBonus();
        } else if (!centralWayPointIsPassed && centralPoint.getDistanceTo(self) > Constants.WAY_POINT_RADIUS) {
            wayPoint = centralPoint;
        } else if (!didSeeBonus) {
            double delta = game.getBonusRadius() + self.getRadius();
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
            if (Utils.isUnitInVisionRange(self, nearestEnemyUnit))
                return wayPoints.getPreviousWayPoint();
            else return wayPoints.getClosestWayPoint();
        } else if (braveryLevel == BraveryLevel.BEWARE_OF_MINIONS_NEAR_BASE) {
            Point2D[] currentWayPoints = wayPoints.getCurrentLaneWayPoints();
            return currentWayPoints[currentWayPoints.length - 5];
        } else {
            return wayPoints.getPreviousWayPoint();
        }
    }

    private Optional<Minion> getAggressiveNeutralMinion() {
        for (Minion minion : visibleNeutralMinionsByMe) {
            if (Utils.isUnitInCastRange(self, minion)
                    && minion.getRemainingActionCooldownTicks() != 0) {
                return Optional.of(minion);
            }
        }
        return Optional.empty();
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
                wayPoints.getCentralPoint().getDistanceTo(wayPoint) - 180.0) / speed);
    }

    private boolean isUnitInSafeRange(Unit unit) {
        return unit instanceof LivingUnit &&
                self.getDistanceTo(unit) - self.getRadius() <= getSafeRange((LivingUnit) unit);
    }

    private double getSafeRange(LivingUnit unit) {
        if (unit instanceof Minion) {
            if (((Minion) unit).getType() == MinionType.FETISH_BLOWDART)
                return game.getFetishBlowdartAttackRange() + 1.0;
            else return 69.0;
        } else if (unit instanceof Building) {
            Building building = (Building) unit;
            LivingUnit nextTarget = Utils.nextBuildingTarget(building, world, game).orElse(null);
            if (nextTarget == null || nextTarget.getId() == self.getId()) {
                return building.getAttackRange() *
                        (1.0 - (max(0.0, building.getRemainingActionCooldownTicks() - 10)
                                / building.getCooldownTicks())) + 1.0;
            } else return 69.0;
        } else if (unit instanceof Wizard && !((Wizard) unit).isMe()) {
            Wizard wizard = (Wizard) unit;
            if (wizard.getLevel() >= self.getLevel() || wizard.getLife() >= self.getLife())
                return wizard.getCastRange() + 1.0;
            else return self.getCastRange();
        } else return self.getCastRange();
    }

    private BraveryLevel getBraveryLevel() {
        if ((self.getLife() <= 37 || previousTickLife - self.getLife() >= 23)
                && wayPoints.getPreviousWayPointIndex() > 0) {
            runAwayCountdown = Constants.RUN_AWAY_COUNTDOWN;
            shouldCheckForBonus = false;
            canCheckBonus = false;
            didSeeBonus = false;
            shouldReturnFromBonus = false;
            centralWayPointIsPassed = false;
            return BraveryLevel.RUN_FOREST_RUN;
        } else if (runAwayCountdown > 0 && wayPoints.getPreviousWayPointIndex() > 0) {
            shouldCheckForBonus = false;
            canCheckBonus = false;
            didSeeBonus = false;
            shouldReturnFromBonus = false;
            centralWayPointIsPassed = false;
            return BraveryLevel.RUN_FOREST_RUN;
        }

        if (nearestEnemyUnit != null) {
            int count = 0;
            for (UnitType unitType : Constants.LIVING_UNIT_TYPES) {
                for (LivingUnit unit : visibleEnemyUnitsByMe.get(unitType)) {
                    if (unit.getId() == nearestEnemyUnit.getId()) continue;
                    if (nearestEnemyUnit.getDistanceTo(unit) <= 500.0) count++;
                }
            }
            if (count > 0) {
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
        }

        if (self.getRemainingActionCooldownTicks() >= game.getWizardActionCooldownTicks() * 0.2)
            return BraveryLevel.BETTER_TO_GO_BACK;

        if (canCheckBonus) return BraveryLevel.NEED_TO_GRAB_BONUS;

        if (enemyUnitInSafeRange != null) return BraveryLevel.ENEMY_IN_SAFE_RANGE;

        return BraveryLevel.I_AM_SUPERMAN;
    }

    enum BraveryLevel {
        I_AM_SUPERMAN, ENEMY_IN_SAFE_RANGE,
        BEWARE_OF_MINIONS_NEAR_BASE, BETTER_TO_GO_BACK, RUN_FOREST_RUN, NEED_TO_GRAB_BONUS
    }
}