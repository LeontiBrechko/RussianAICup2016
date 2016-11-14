import model.*;

import java.util.*;
import java.util.stream.Collectors;

public final class MyStrategy implements Strategy {
    // Constants
    private static final double WAYPOINT_RADIUS = Math.sqrt(80000.0) - 20.1;
    private static final double LOW_HP_FACTOR = 0.25;
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

            // TODO: cut unnecessary object initializations
            Point2D[] top = new Point2D[20];
            top[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
            top[1] = new Point2D(mapSize * 0.05, mapSize * 0.9 + 20.0);
            for (int i = 2; i <= 8; i++) {
                top[i] = new Point2D(mapSize * 0.05, mapSize * ((10.0 - i) / 10.0));
            }
            top[9] = new Point2D(mapSize * 0.05, mapSize * 0.15);
            top[10] = new Point2D(mapSize * 0.1, mapSize * 0.1);
            top[11] = new Point2D(mapSize * 0.15, mapSize * 0.05);
            for (int i = 12; i <= 18; i++) {
                top[i] = new Point2D(mapSize * ((i - 12.0 + 2.0) / 10.0), mapSize * 0.05);
            }
            top[19] = new Point2D(mapSize * 0.85, mapSize * 0.05);

            Point2D[] middle = new Point2D[17];
            middle[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
            middle[1] = new Point2D(mapSize * 0.05, mapSize * 0.9 + 20.0);
            for (int i = 2; i <= 16; i++) {
                middle[i] = new Point2D(mapSize * (((i - 2.0) / 2.0 + 1.5) / 10.0),
                        mapSize * ((9.5 - i * 0.5) / 10.0));
            }

            Point2D[] bottom = new Point2D[20];
            bottom[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
            bottom[1] = new Point2D(mapSize * 0.1 - 20.0, mapSize * 0.95);
            for (int i = 2; i <= 8; i++) {
                bottom[i] = new Point2D(mapSize * (i / 10.0), mapSize * 0.95);
            }
            bottom[9] = new Point2D(mapSize * 0.85, mapSize * 0.95);
            bottom[10] = new Point2D(mapSize * 0.9, mapSize * 0.9);
            bottom[11] = new Point2D(mapSize * 0.95, mapSize * 0.85);
            for (int i = 12; i <= 18; i++) {
                bottom[i] = new Point2D(mapSize * 0.95, mapSize * ((8.0 - (i - 12.0)) / 10.0));
            }
            bottom[19] = new Point2D(mapSize * 0.95, mapSize * 0.15);

            WAYPOINTS_BY_LANE.put(LaneType.TOP, top);
            WAYPOINTS_BY_LANE.put(LaneType.MIDDLE, middle);
            WAYPOINTS_BY_LANE.put(LaneType.BOTTOM, bottom);

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
        List<LivingUnit> collisions = getCollisionList();
        if (collisions.size() > 0) {
            for (LivingUnit unit : collisions) {
                if (unit.getFaction() == Faction.OTHER || unit.getFaction() == Faction.RENEGADES)
                    move.setAction(ActionType.STAFF);

                double alpha = self.getAngleTo(unit);
                boolean flag = alpha >= 0;
                alpha = StrictMath.PI / 2.0 - StrictMath.abs(alpha);
                double beta = (180.0 - alpha) / 2.0;
                double a = self.getRadius();
                double b = unit.getRadius();
                double d = (a * StrictMath.sin(alpha)) / StrictMath.sin(beta);
                double c = (b * d) / a;
                double x = StrictMath.min(StrictMath.sin(alpha) * (c + d), game.getWizardStrafeSpeed());
                if (flag) x = -x;
                move.setStrafeSpeed(x);
            }
        } else if (nearestEnemyTarget.isInVisionRange(self)) {
            retreatDelta = getRetreatDelta(previousWaypoint);
            retreatAngle = self.getAngleTo(previousWaypoint.x, previousWaypoint.y);

            move.setStrafeSpeed(StrictMath.sin(retreatAngle) * retreatDelta);

            if (!isBraveEnough) {
                move.setTurn(retreatAngle);
            } else {
                move.setTurn(nearestEnemyTarget.getAngleTo());
            }

            if (!isBraveEnough || nearestEnemyTarget.isInCastRange(self)) {
                move.setSpeed(StrictMath.cos(retreatAngle) * retreatDelta);
            } else {
                move.setSpeed(game.getWizardForwardSpeed());
            }
        } else {
            move.setTurn(self.getAngleTo(nextWaypoint.x, nextWaypoint.y));
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
            if (currentLivingUnitDistance + 70.0 < nearestLivingUnitDistance) {
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

        if (nearestEnemyTarget.isFound()) {
            double[] closestDists = new double[3];
            Arrays.fill(closestDists, Double.MAX_VALUE);
            double currentDist;
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.WIZARD)) {
                currentDist = unit.getDistanceTo(nearestEnemyTarget.getLivingUnit());
                if (currentDist < closestDists[0]) {
                    closestDists[0] = currentDist;
                }
            }
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.MINION)) {
                currentDist = unit.getDistanceTo(nearestEnemyTarget.getLivingUnit());
                if (currentDist < closestDists[1]) {
                    closestDists[1] = currentDist;
                }
            }
            for (LivingUnit unit : visibleFriendUnitsByMe.get(UnitType.BUILDING)) {
                currentDist = unit.getDistanceTo(nearestEnemyTarget.getLivingUnit());
                if (currentDist < closestDists[1]) {
                    closestDists[1] = currentDist;
                }
            }
            if (closestDists[0] > nearestEnemyTarget.distanceTo
                    && closestDists[1] > nearestEnemyTarget.distanceTo
                    && closestDists[2] > nearestEnemyTarget.distanceTo) isBraveEnough = false;
        }

        if (self.getLife() < self.getMaxLife() * LOW_HP_FACTOR && previousWaypoint != waypoints[0])
            isBraveEnough = false;
        return isBraveEnough;
    }

    private List<LivingUnit> getCollisionList() {
        List<LivingUnit> collisionList = new ArrayList<>();
        for (UnitType unitType : LIVING_UNIT_TYPES) {
            collisionList.addAll(
                    Arrays.stream(getLivingUnitsByType(unitType))
                            .filter((unit) -> self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() + 2.0
                                    && !(unit instanceof Wizard && ((Wizard) unit).isMe())
                                    && StrictMath.abs(self.getAngleTo(unit)) < StrictMath.PI / 2.0)
                            .collect(Collectors.toList()));
        }
        return collisionList;
    }

    private double getRetreatDelta(Point2D pointForRetreat) {
        double cosAlpha = StrictMath.cos(StrictMath.abs(self.getAngleTo(pointForRetreat.x, pointForRetreat.y)));
        double a = nearestEnemyTarget.getDistanceTo();
        double b = self.getCastRange();
        return a * cosAlpha + StrictMath.sqrt(a * a * (cosAlpha * cosAlpha - 1) + b * b);
    }

    private Point2D getNextWaypoint() {
        Point2D last = waypoints[waypoints.length - 1];

        for (int i = waypoints.length - 2; i >= 0; i--) {
            double temp = waypoints[i].getDistanceTo(self);
            if (waypoints[i].getDistanceTo(self) <= WAYPOINT_RADIUS) {
                return waypoints[i + 1];
            }
        }
        return last;
    }

    private Point2D getPreviousWaypoint() {
        Point2D first = waypoints[0];
        for (int i = 1; i < waypoints.length; i++) {
            if (waypoints[i].getDistanceTo(self) <= WAYPOINT_RADIUS) {
                return waypoints[i - 1];
            }
        }

        return first;
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

        @Override
        public boolean equals(Object obj) {
            return !(obj == null || !(obj instanceof Point2D)) && this.x == ((Point2D) obj).x && this.y == ((Point2D) obj).y;
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

        public boolean isInVisionRange(Wizard self) {
            return this.isFound() && this.distanceTo <= self.getVisionRange();
        }

        public boolean isInStaffSector(Wizard self, Game game) {
            return this.isInCastRange(self) && StrictMath.abs(this.angleTo) <= game.getStaffSector() / 2.0;
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