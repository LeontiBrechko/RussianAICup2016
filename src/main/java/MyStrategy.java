import model.*;

import java.util.*;
import java.util.stream.Collectors;

public final class MyStrategy implements Strategy {
    // Constants
    private static final double WAYPOINT_RADIUS = 100.0D;
    private static final double LOW_HP_FACTOR = 0.25D;
    private static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    private static final UnitType[] ACTION_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD};

    // World
    private final Map<LaneType, Point2D[]> WAYPOINTS_BY_LANE = new EnumMap<>(LaneType.class);
    private LaneType lane;
    private Point2D[] waypoints;
    private Point2D nextWaypoint;
    private Point2D previousWaypoint;

    private Minion[] visibleMinions;
    private Tree[] visibleTrees;
    private Building[] visibleBuildings;
    private Wizard[] visibleWizards;

    // Strategy
    private Wizard self;
    private World world;
    private Game game;
    private Move move;

    private Random random;

    private Map<UnitType, List<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, List<LivingUnit>> visibleFriendUnitsByMe;
    private LivingTarget nearestEnemyTarget;
    private LivingTarget nearestFriendTarget;
    private double retreatDelta;
    private double retreatAngle;
    private boolean isBraveEnough;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
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
            nearestEnemyTarget = new LivingTarget();
            nearestFriendTarget = new LivingTarget();

            double mapSize = game.getMapSize();

            WAYPOINTS_BY_LANE.put(LaneType.MIDDLE, new Point2D[]{
                    new Point2D(100.0D, mapSize - 100.0D),
                    random.nextBoolean()
                            ? new Point2D(600.0D, mapSize - 200.0D)
                            : new Point2D(200.0D, mapSize - 600.0D),
                    new Point2D(800.0D, mapSize - 800.0D),
                    new Point2D(mapSize - 600.0D, 600.0D)
            });

            WAYPOINTS_BY_LANE.put(LaneType.TOP, new Point2D[]{
                    new Point2D(100.0D, mapSize - 100.0D),
                    new Point2D(100.0D, mapSize - 400.0D),
                    new Point2D(200.0D, mapSize - 800.0D),
                    new Point2D(200.0D, mapSize * 0.75D),
                    new Point2D(200.0D, mapSize * 0.5D),
                    new Point2D(200.0D, mapSize * 0.25D),
                    new Point2D(200.0D, 200.0D),
                    new Point2D(mapSize * 0.25D, 200.0D),
                    new Point2D(mapSize * 0.5D, 200.0D),
                    new Point2D(mapSize * 0.75D, 200.0D),
                    new Point2D(mapSize - 200.0D, 200.0D)
            });

            WAYPOINTS_BY_LANE.put(LaneType.BOTTOM, new Point2D[]{
                    new Point2D(100.0D, mapSize - 100.0D),
                    new Point2D(400.0D, mapSize - 100.0D),
                    new Point2D(800.0D, mapSize - 200.0D),
                    new Point2D(mapSize * 0.25D, mapSize - 200.0D),
                    new Point2D(mapSize * 0.5D, mapSize - 200.0D),
                    new Point2D(mapSize * 0.75D, mapSize - 200.0D),
                    new Point2D(mapSize - 200.0D, mapSize - 200.0D),
                    new Point2D(mapSize - 200.0D, mapSize * 0.75D),
                    new Point2D(mapSize - 200.0D, mapSize * 0.5D),
                    new Point2D(mapSize - 200.0D, mapSize * 0.25D),
                    new Point2D(mapSize - 200.0D, 200.0D)
            });

            switch ((int) self.getId()) {
                case 1:
                case 2:
                case 6:
                case 7:
                    lane = LaneType.TOP;
                    break;
                case 3:
                case 8:
                    lane = LaneType.MIDDLE;
                    break;
                case 4:
                case 5:
                case 9:
                case 10:
                    lane = LaneType.BOTTOM;
                    break;
                default:
            }

            waypoints = WAYPOINTS_BY_LANE.get(lane);

            // Наша стратегия исходит из предположения, что заданные нами ключевые точки упорядочены по убыванию
            // дальности до последней ключевой точки. Сейчас проверка этого факта отключена, однако вы можете
            // написать свою проверку, если решите изменить координаты ключевых точек.

            /*Point2D lastWaypoint = waypoints[waypoints.length - 1];

            Preconditions.checkState(ArrayUtils.isSorted(waypoints, (waypointA, waypointB) -> Double.compare(
                    waypointB.getDistanceTo(lastWaypoint), waypointA.getDistanceTo(lastWaypoint)
            )));*/
        }
    }

    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;

        nextWaypoint = getNextWaypoint();
        previousWaypoint = getPreviousWaypoint();

        visibleMinions = world.getMinions();
        visibleTrees = world.getTrees();
        visibleBuildings = world.getBuildings();
        visibleWizards = world.getWizards();

        visibleEnemyUnitsByMe = getLivingUnitsInRange(Faction.RENEGADES, ACTION_UNIT_TYPES,
                self.getVisionRange());
        visibleFriendUnitsByMe = getLivingUnitsInRange(Faction.ACADEMY, ACTION_UNIT_TYPES,
                self.getVisionRange());

        findNextNearestTarget(Faction.RENEGADES, LIVING_UNIT_TYPES, nearestEnemyTarget, Double.MAX_VALUE);
        findNextNearestTarget(Faction.ACADEMY, LIVING_UNIT_TYPES, nearestFriendTarget, Double.MAX_VALUE);

        isBraveEnough = isBraveEnough();
    }

    private void handleSkillLearning() {
    }

    private void handleActionExecution() {
        if (isBraveEnough && self.getRemainingActionCooldownTicks() == 0) {
            if (nearestEnemyTarget.isInStaffSector(self, game)) {
                move.setAction(ActionType.MAGIC_MISSILE);
                move.setCastAngle(nearestEnemyTarget.getAngleTo());
                move.setMinCastDistance(nearestEnemyTarget.getDistanceTo() -
                        nearestEnemyTarget.getLivingUnit().getRadius() +
                        game.getMagicMissileRadius());
            }
        } else {
            move.setAction(ActionType.NONE);
        }
    }

    private void handleMovement() {

        if (isBraveEnough && nearestEnemyTarget.isInCastRange(self)) {
            Point2D previousWayPoint = getPreviousWaypoint();
            retreatDelta = getRetreatDelta(previousWayPoint);
            retreatAngle = self.getAngleTo(previousWayPoint.x, previousWayPoint.y);
        }

        // handle strafe speed
//        move.setStrafeSpeed(random.nextBoolean() ? game.getWizardStrafeSpeed() : -game.getWizardStrafeSpeed());
        if (isBraveEnough && nearestEnemyTarget.isInCastRange(self)) {
            move.setStrafeSpeed(StrictMath.sin(retreatAngle) * retreatDelta);
        }

        // handle turn
        if (nearestEnemyTarget.isInCastRange(self)) {
            if (!isBraveEnough) {
                move.setTurn(self.getAngleTo(previousWaypoint.x, previousWaypoint.y));
            } else {
                move.setTurn(nearestEnemyTarget.getAngleTo());
            }
        } else {
            move.setTurn(self.getAngleTo(nextWaypoint.x, nextWaypoint.y));
        }

        // handle move
        if (nearestEnemyTarget.isInCastRange(self)) {
            if (!isBraveEnough) {
                move.setSpeed(game.getWizardForwardSpeed());
            } else {
                move.setSpeed(StrictMath.cos(retreatAngle) * retreatDelta);
            }
        } else {
            move.setSpeed(game.getWizardForwardSpeed());
        }

    }

    private void handleMasterManagement() {
    }

    /*
      ---------------------------------UTILS PART-----------------------------------------------------------------------
     */
    private Map<UnitType, List<LivingUnit>> getLivingUnitsInRange(Faction faction, UnitType[] unitTypes,
                                                                  double range) {
        Map<UnitType, List<LivingUnit>> unitsByType = new EnumMap<>(UnitType.class);
        for (UnitType unitType : unitTypes) {
            unitsByType.put(unitType,
                    Arrays.stream(getLivingUnitsByType(unitType))
                            .filter((unit) -> unit.getFaction() == faction
                                    && self.getDistanceTo(unit) <= range
                                    && !(unit instanceof Wizard && ((Wizard) unit).isMe()))
                            .collect(Collectors.toList()));
        }
        return unitsByType;
    }

    private LivingUnit[] getLivingUnitsByType(UnitType unitType) {
        LivingUnit[] array;
        switch (unitType) {
            case MINION:
                array = visibleMinions;
                break;
            case TREE:
                array = visibleTrees;
                break;
            case BUILDING:
                array = visibleBuildings;
                break;
            case WIZARD:
                array = visibleWizards;
                break;
            default:
                throw new EnumConstantNotPresentException(UnitType.class, unitType.toString());
        }
        return array;
    }

    private void findNextNearestTarget(Faction faction, UnitType[] unitTypes,
                                       LivingTarget existingTarget, double range) {
        Map<UnitType, List<LivingUnit>> unitsInRange = getLivingUnitsInRange(faction, unitTypes, range);
        List<LivingUnit> livingUnits = new ArrayList<>();
        LivingUnit nearestLivingUnit = null;
        double nearestLivingUnitDistance = Double.MAX_VALUE;
        double nearestLivingUnitAngle = Double.MAX_VALUE;
        double currentLivingUnitDistance;

        for (UnitType unitType : unitTypes) livingUnits.addAll(unitsInRange.get(unitType));
        for (LivingUnit livingUnit : livingUnits) {
            currentLivingUnitDistance = self.getDistanceTo(livingUnit);
            if (currentLivingUnitDistance + 70.0D < nearestLivingUnitDistance) {
                nearestLivingUnit = livingUnit;
                nearestLivingUnitDistance = currentLivingUnitDistance;
            }
        }

        if (nearestLivingUnit != null) nearestLivingUnitAngle = self.getAngleTo(nearestLivingUnit);
        existingTarget.setLivingUnit(nearestLivingUnit);
        existingTarget.setDistanceTo(nearestLivingUnitDistance);
        existingTarget.setAngleTo(nearestLivingUnitAngle);
    }

    private boolean isBraveEnough() {
        boolean isBraveEnough = true;
        int[][] count = new int[][] {
                {visibleEnemyUnitsByMe.get(UnitType.WIZARD).size(), visibleFriendUnitsByMe.get(UnitType.WIZARD).size()},
                {visibleEnemyUnitsByMe.get(UnitType.MINION).size(), visibleFriendUnitsByMe.get(UnitType.MINION).size()}
        };

        if (self.getLife() < self.getMaxLife() * LOW_HP_FACTOR)
            isBraveEnough = false;
        else {
            if (count[1][1] == 0)
                isBraveEnough = false;
        }
        return isBraveEnough;
    }

    private double getRetreatDelta(Point2D pointForRetreat) {
        double cosAlpha = StrictMath.cos(StrictMath.abs(self.getAngleTo(pointForRetreat.x, pointForRetreat.y)));
        double a = nearestEnemyTarget.getDistanceTo();
        double b = self.getCastRange();
        return a * cosAlpha + StrictMath.sqrt(a * a * (cosAlpha * cosAlpha - 1) + b * b);
    }

    private Point2D getNextWaypoint() {
        int lastWaypointIndex = waypoints.length - 1;
        Point2D lastWaypoint = waypoints[lastWaypointIndex];

        for (int waypointIndex = 0; waypointIndex < lastWaypointIndex; ++waypointIndex) {
            Point2D waypoint = waypoints[waypointIndex];

            if (waypoint.getDistanceTo(self) <= WAYPOINT_RADIUS) {
                return waypoints[waypointIndex + 1];
            }

            if (lastWaypoint.getDistanceTo(waypoint) < lastWaypoint.getDistanceTo(self)) {
                return waypoint;
            }
        }

        return lastWaypoint;
    }

    private Point2D getPreviousWaypoint() {
        Point2D firstWaypoint = waypoints[0];

        for (int waypointIndex = waypoints.length - 1; waypointIndex > 0; --waypointIndex) {
            Point2D waypoint = waypoints[waypointIndex];

            if (waypoint.getDistanceTo(self) <= WAYPOINT_RADIUS) {
                return waypoints[waypointIndex - 1];
            }

            if (firstWaypoint.getDistanceTo(waypoint) < firstWaypoint.getDistanceTo(self)) {
                return waypoint;
            }
        }

        return firstWaypoint;
    }

    private static final class Point2D {
        private final double x;
        private final double y;

        private Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getDistanceTo(double x, double y) {
            return StrictMath.hypot(this.x - x, this.y - y);
        }

        public double getDistanceTo(Point2D point) {
            return getDistanceTo(point.x, point.y);
        }

        public double getDistanceTo(Unit unit) {
            return getDistanceTo(unit.getX(), unit.getY());
        }
    }

    private static final class LivingTarget {
        private LivingUnit livingUnit;
        private double distanceTo;
        private double angleTo;

        public LivingTarget() {
            this.distanceTo = Double.MAX_VALUE;
            this.angleTo = Double.MAX_VALUE;
        }

        public LivingTarget(LivingUnit livingUnit, double distanceTo, double angleTo) {
            this();
            this.livingUnit = livingUnit;
            this.distanceTo = distanceTo;
            this.angleTo = angleTo;
        }

        public LivingUnit getLivingUnit() {
            return livingUnit;
        }

        public void setLivingUnit(LivingUnit livingUnit) {
            this.livingUnit = livingUnit;
        }

        public double getDistanceTo() {
            return distanceTo;
        }

        public void setDistanceTo(double distanceTo) {
            this.distanceTo = distanceTo;
        }

        public double getAngleTo() {
            return angleTo;
        }

        public void setAngleTo(double angleTo) {
            this.angleTo = angleTo;
        }

        public boolean isFound() {
            return this.getLivingUnit() != null;
        }

        public boolean isInCastRange(Wizard self) {
            return this.isFound() && this.distanceTo <= self.getCastRange();
        }

        public boolean isInStaffSector(Wizard self, Game game) {
            return this.isInCastRange(self) && StrictMath.abs(this.angleTo) <= game.getStaffSector() / 2.0D;
        }
    }

    private enum UnitType {
        /**
         * Circular units
         */
        BONUS,
        PROJECTILE,

        /**
         * Living units
         */
        WIZARD,
        BUILDING,
        TREE,
        MINION
    }
}