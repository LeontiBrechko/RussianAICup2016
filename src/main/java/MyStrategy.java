import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public final class MyStrategy implements Strategy {
    // Constants
    private static final double WAY_POINT_RADIUS = sqrt(80000.0);
    private static final double WAY_POINT_RANGE = 190.0;
    private static final double LOW_HP_FACTOR = 0.25;
    private static final double PI_BY_2 = PI / 2.0;
    private static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    private static final double VISION_ERROR = 0.1;
    private static final double NEAREST_UNIT_ERROR = 70.0;
    private static final double BRAVERY_ERROR = 100.0;
    private static final double COLLISION_ERROR = 4.0;

    // World
    private Map<LaneType, Point2D[]> wayPointsByLane;
    private Point2D[] wayPoints;
    private int nextWayPointIndex;
    private int previousWayPointIndex;
    private Point2D nextWayPoint;
    private Point2D previousWayPoint;

    private int previousTickIndex;

    // Strategy
    private Wizard self;
    private World world;
    private Game game;
    private Move move;

    private Random random;

    private Map<UnitType, ArrayDeque<LivingUnit>> visibleEnemyUnitsByMe;
    private Map<UnitType, ArrayDeque<LivingUnit>> visibleFriendUnitsByMe;
    private ArrayDeque<LivingUnit> visibleTreesByMe;
    private ArrayDeque<LivingUnit> visibleNeutralMinionsByMe;

    private LivingTarget nearestEnemyTarget;
    private LivingTarget nearestFriendTarget;
    private boolean isBraveEnough;

    private ArrayDeque<LivingUnit> collisions;
    boolean shouldDetermineLane;

    @Override
    public void move(Wizard self, World world, Game game, Move move) {
        self.getStatuses();
        initializeStrategy(self, game);
        if (world.getTickIndex() < 120) return;
        initializeTick(self, world, game, move);
        if (shouldDetermineLane) return;
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
            nextWayPointIndex = -1;
            previousWayPointIndex = -1;
            shouldDetermineLane = true;

            defineWayPoints(self.getFaction(), game);
        }
    }

    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;

        if (world.getTickIndex() - previousTickIndex > 1) {
            shouldDetermineLane = true;
            wayPoints = null;
        }
        previousTickIndex = world.getTickIndex();

        if (shouldDetermineLane) {
            shouldDetermineLane = false;
            LaneType lane = determineLane();
            wayPoints = wayPointsByLane.get(lane);
        }

        if (wayPoints != null && !shouldDetermineLane) {
            findNextWayPoint();
            findPreviousWayPoint();

            lookAround();

            collisions = getCollisionList();
            isBraveEnough = isBraveEnough();
        }
    }

    private void handleSkillLearning() {
    }

    private void handleActionExecution() {
        if (isBraveEnough && nearestEnemyTarget.isInStaffSector(self, game)) {
            move.setAction(ActionType.MAGIC_MISSILE);
            move.setCastAngle(nearestEnemyTarget.getAngleTo());
            move.setMinCastDistance(nearestEnemyTarget.getDistanceTo() -
                    nearestEnemyTarget.getLivingUnit().getRadius() +
                    game.getMagicMissileRadius());
        }
    }

    private void handleMovement() {
        if (collisions.size() == 1) {
            handleSingleCollision();
        } else if (collisions.size() > 1) {
            double retreatDelta;
            double retreatAngle;
            if (self.getAngleTo(previousWayPoint.x, previousWayPoint.y) <
                    self.getAngleTo(nextWayPoint.x, nextWayPoint.y)) {
                retreatDelta = getRetreatDelta(previousWayPoint);
                retreatAngle = self.getAngleTo(previousWayPoint.x, previousWayPoint.y);
            } else {
                retreatDelta = getRetreatDelta(nextWayPoint);
                retreatAngle = self.getAngleTo(nextWayPoint.x, nextWayPoint.y);
            }

            if (nearestEnemyTarget.isInVisionRange(self)) {
                if (!(nearestEnemyTarget.getLivingUnit() instanceof Tree)) {
                    move.setStrafeSpeed(sin(retreatAngle) * retreatDelta);
                    move.setSpeed(cos(retreatAngle) * retreatDelta);
                }
                move.setTurn(nearestEnemyTarget.getAngleTo());
            } else {
                move.setTurn(retreatAngle);
                move.setSpeed(cos(retreatAngle) * retreatDelta);
                move.setStrafeSpeed(sin(retreatAngle) * retreatDelta);
            }
        } else if (nearestEnemyTarget.isInVisionRange(self)) {
            double retreatDelta = getRetreatDelta(previousWayPoint);
            double retreatAngle = self.getAngleTo(previousWayPoint.x, previousWayPoint.y);

            move.setStrafeSpeed(sin(retreatAngle) * retreatDelta);

            if (!isBraveEnough) {
                move.setTurn(retreatAngle);
            } else {
                move.setTurn(nearestEnemyTarget.getAngleTo());
            }

            if (!isBraveEnough || nearestEnemyTarget.isInCastRange(self)) {
                move.setSpeed(cos(retreatAngle) * retreatDelta);
            } else {
                move.setSpeed(game.getWizardForwardSpeed());
            }
        } else {
            move.setTurn(self.getAngleTo(nextWayPoint.x, nextWayPoint.y));
            move.setSpeed(game.getWizardForwardSpeed());
        }
    }

    private void handleMasterManagement() {
    }

    /*
      ---------------------------------UTILS PART-----------------------------------------------------------------------
     */
    private LaneType determineLane() {
        LaneType lane;

        boolean[] isCounted = new boolean[5];

        int topCount = 0;
        int middleCount = 0;
        int bottomCount = 0;

        if (world.getTickIndex() <= 200) {
            double topAngle, middleAngle, bottomAngle, minAngle;
            Point2D top = wayPointsByLane.get(LaneType.TOP)[5];
            Point2D middle = wayPointsByLane.get(LaneType.MIDDLE)[5];
            Point2D bottom = wayPointsByLane.get(LaneType.BOTTOM)[5];

            for (Wizard wizard : world.getWizards()) {
                if (wizard.isMe() || wizard.getFaction() != Faction.ACADEMY) continue;
                topAngle = abs(wizard.getAngleTo(top.x, top.y));
                middleAngle = abs(wizard.getAngleTo(middle.x, middle.y));
                bottomAngle = abs(wizard.getAngleTo(bottom.x, bottom.y));
                minAngle = min(topAngle, min(middleAngle, bottomAngle));
                if (minAngle == topAngle) topCount++;
                else if (minAngle == middleAngle) middleCount++;
                else bottomCount++;
            }
        } else {
            Point2D[] top = wayPointsByLane.get(LaneType.TOP);
            Point2D[] middle = wayPointsByLane.get(LaneType.MIDDLE);
            Point2D[] bottom = wayPointsByLane.get(LaneType.BOTTOM);
            // TODO: check results of this method
            topCount = countWizards(top, isCounted);
            middleCount = countWizards(middle, isCounted);
            bottomCount = countWizards(bottom, isCounted);
        }

        // TODO: check properly when don't have enough wizards
//        if (topCount + middleCount + bottomCount < 4) {
//            switch ((int) self.getId()) {
//                case 1:
//                case 2:
//                case 6:
//                case 7:
//                    lane = LaneType.TOP;
//                    break;
//                case 3:
//                case 8:
//                    lane = LaneType.MIDDLE;
//                    break;
//                default:
//                    lane = LaneType.BOTTOM;
//                    break;
//            }
//        } else {
        int min = min(topCount, min(middleCount, bottomCount));
        if (min == topCount) lane = LaneType.TOP;
        else if (min == middleCount) lane = LaneType.MIDDLE;
        else lane = LaneType.BOTTOM;
//        }
        return lane;
    }

    private int countWizards(Point2D[] wayPoints, boolean[] isCounted) {
        int count = 0;
        for (Point2D point : wayPoints) {
            for (Wizard wizard : world.getWizards()) {
                if (wizard.getFaction() != Faction.ACADEMY || wizard.isMe()
                        || isCounted[(int) wizard.getId() - 1]) continue;
                if (point.getDistanceTo(wizard) <= WAY_POINT_RADIUS + 100.0) {
                    count++;
                    isCounted[(int) wizard.getId() - 1] = true;
                }
            }
        }
        return count;
    }

    private void defineWayPoints(Faction faction, Game game) {
        double mapSize = game.getMapSize();
        Point2D[] top, middle, bottom;

        // TOP
        top = new Point2D[23];
        top[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
        top[1] = new Point2D(mapSize * 0.05, mapSize * 0.9 + 20.0);
        top[2] = new Point2D(mapSize * 0.05, mapSize * 0.85);
        for (int i = 3, j = 0; i <= 9; i++, j++) {
            top[i] = new Point2D(mapSize * 0.05, mapSize * ((8.0 - j * 1.0) / 10.0));
        }
        top[10] = new Point2D(mapSize * 0.05, mapSize * 0.15);
        top[11] = new Point2D(mapSize * 0.1, mapSize * 0.1);
        top[12] = new Point2D(mapSize * 0.15, mapSize * 0.05);
        for (int i = 13, j = 0; i <= 19; i++, j++) {
            top[i] = new Point2D(mapSize * ((2.0 + j) / 10.0), mapSize * 0.05);
        }
        top[20] = new Point2D(mapSize * 0.85, mapSize * 0.05);
        top[21] = new Point2D(mapSize * 0.9 + 20.0, mapSize * 0.05);
        top[22] = new Point2D(mapSize * 0.95, mapSize * 0.05);

        // MIDDLE
        middle = new Point2D[19];
        middle[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
        middle[1] = new Point2D(mapSize * 0.05, mapSize * 0.9 + 20.0);
        for (int i = 2; i <= 16; i++) {
            middle[i] = new Point2D(mapSize * (((i - 2.0) / 2.0 + 1.5) / 10.0),
                    mapSize * ((9.5 - i * 0.5) / 10.0));
        }
        middle[17] = new Point2D(mapSize * 0.95, mapSize * 0.1 - 20.0);
        middle[18] = new Point2D(mapSize * 0.95, mapSize * 0.95);

        // BOTTOM
        bottom = new Point2D[23];
        bottom[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
        bottom[1] = new Point2D(mapSize * 0.1 - 20.0, mapSize * 0.95);
        bottom[2] = new Point2D(mapSize * 0.15, mapSize * 0.95);
        for (int i = 3, j = 0; i <= 9; i++, j++) {
            bottom[i] = new Point2D(mapSize * ((j + 2.0) / 10.0), mapSize * 0.95);
        }
        bottom[10] = new Point2D(mapSize * 0.85, mapSize * 0.95);
        bottom[11] = new Point2D(mapSize * 0.9, mapSize * 0.9);
        bottom[12] = new Point2D(mapSize * 0.95, mapSize * 0.85);
        for (int i = 13, j = 0; i <= 19; i++, j++) {
            bottom[i] = new Point2D(mapSize * 0.95, mapSize * ((8.0 - j) / 10.0));
        }
        bottom[20] = new Point2D(mapSize * 0.95, mapSize * 0.15);
        bottom[21] = new Point2D(mapSize * 0.95, mapSize * 0.1 - 20.0);
        bottom[22] = new Point2D(mapSize * 0.95, mapSize * 0.95);

        if (faction == Faction.RENEGADES) {
            List<Point2D> list = Arrays.asList(top);
            Collections.reverse(list);
            top = (Point2D[]) list.toArray();
            list = Arrays.asList(middle);
            Collections.reverse(list);
            middle = (Point2D[]) list.toArray();
            list = Arrays.asList(bottom);
            Collections.reverse(list);
            bottom = (Point2D[]) list.toArray();
        }

        wayPointsByLane = new EnumMap<>(LaneType.class);
        wayPointsByLane.put(LaneType.TOP, top);
        wayPointsByLane.put(LaneType.MIDDLE, middle);
        wayPointsByLane.put(LaneType.BOTTOM, bottom);
    }

    private void lookAround() {
        double currentDistance;

        double nearestEnemyDistance = Double.MAX_VALUE;
        LivingUnit nearestEnemy = null;
        double nearestFriendDistance = Double.MAX_VALUE;
        LivingUnit nearestFriend = null;

        visibleEnemyUnitsByMe = new EnumMap<>(UnitType.class);
        visibleFriendUnitsByMe = new EnumMap<>(UnitType.class);
        visibleTreesByMe = new ArrayDeque<>();
        visibleNeutralMinionsByMe = new ArrayDeque<>();

        for (UnitType unitType : LIVING_UNIT_TYPES) {
            visibleEnemyUnitsByMe.put(unitType, new ArrayDeque<>());
            visibleFriendUnitsByMe.put(unitType, new ArrayDeque<>());
        }

        for (Wizard wizard : world.getWizards()) {
            currentDistance = self.getDistanceTo(wizard);
            if (wizard.isMe() || !isUnitInSelfVisionRange(wizard)) continue;
            if (wizard.getFaction() == Faction.RENEGADES) {
                visibleEnemyUnitsByMe.get(UnitType.WIZARD).add(wizard);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                    nearestEnemyDistance = currentDistance;
                    nearestEnemy = wizard;
                }
            } else {
                visibleFriendUnitsByMe.get(UnitType.WIZARD).add(wizard);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestFriendDistance) {
                    nearestFriendDistance = currentDistance;
                    nearestFriend = wizard;
                }
            }
        }
        for (Minion minion : world.getMinions()) {
            currentDistance = self.getDistanceTo(minion);
            if (!isUnitInSelfVisionRange(minion)) continue;
            if (minion.getFaction() == Faction.RENEGADES) {
                visibleEnemyUnitsByMe.get(UnitType.MINION).add(minion);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                    nearestEnemyDistance = currentDistance;
                    nearestEnemy = minion;
                }
            } else if (minion.getFaction() == Faction.ACADEMY) {
                visibleFriendUnitsByMe.get(UnitType.MINION).add(minion);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestFriendDistance) {
                    nearestFriendDistance = currentDistance;
                    nearestFriend = minion;
                }
            } else visibleNeutralMinionsByMe.add(minion);
        }
        for (Building building : world.getBuildings()) {
            currentDistance = self.getDistanceTo(building);
            if (!isUnitInSelfVisionRange(building)) continue;
            if (building.getFaction() == Faction.RENEGADES) {
                visibleEnemyUnitsByMe.get(UnitType.BUILDING).add(building);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                    nearestEnemyDistance = currentDistance;
                    nearestEnemy = building;
                }
            } else {
                visibleFriendUnitsByMe.get(UnitType.BUILDING).add(building);
                if (currentDistance + NEAREST_UNIT_ERROR < nearestFriendDistance) {
                    nearestFriendDistance = currentDistance;
                    nearestFriend = building;
                }
            }
        }
        for (Tree tree : world.getTrees()) {
            if (isUnitInSelfVisionRange(tree)) {
                visibleTreesByMe.add(tree);
                currentDistance = self.getDistanceTo(tree);
                if (isColliding(tree) &&
                        currentDistance + NEAREST_UNIT_ERROR < nearestEnemyDistance) {
                    nearestEnemyDistance = currentDistance;
                    nearestEnemy = tree;
                }
            }
        }

        if (!(nearestEnemyTarget.isInVisionRange(self)
                && isColliding(nearestEnemyTarget.getLivingUnit()))) {
            if (nearestEnemy != null) nearestEnemyTarget.setAngleTo(self.getAngleTo(nearestEnemy));
            else nearestFriendTarget.setAngleTo(Double.MAX_VALUE);
            nearestEnemyTarget.setLivingUnit(nearestEnemy);
            nearestEnemyTarget.setDistanceTo(nearestEnemyDistance);
        }

        if (!nearestFriendTarget.isInVisionRange(self)) {
            if (nearestFriend != null) nearestFriendTarget.setAngleTo(self.getAngleTo(nearestFriend));
            else nearestFriendTarget.setAngleTo(Double.MAX_VALUE);
            nearestFriendTarget.setLivingUnit(nearestFriend);
            nearestFriendTarget.setDistanceTo(nearestFriendDistance);
        }
    }

    private boolean isUnitInSelfVisionRange(Unit unit) {
        return self.getDistanceTo(unit) <= self.getVisionRange() + VISION_ERROR;
    }

    private boolean isBraveEnough() {
        boolean isBraveEnough = true;

        if (collisions == null || collisions.size() <= 1) {
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
                    if (currentDist < closestDists[2]) {
                        closestDists[2] = currentDist;
                    }
                }
                if (closestDists[0] > nearestEnemyTarget.distanceTo + BRAVERY_ERROR
                        && closestDists[1] > nearestEnemyTarget.distanceTo + BRAVERY_ERROR
                        && closestDists[2] > nearestEnemyTarget.distanceTo + BRAVERY_ERROR) isBraveEnough = false;
            }
            // TODO: think about this check
//            if (self.getLife() < self.getMaxLife() * LOW_HP_FACTOR && previousWayPoint != wayPoints[0])
//                isBraveEnough = false;
        }

        return isBraveEnough;
    }

    private double getRetreatDelta(Point2D pointForRetreat) {
        double cosAlpha = cos(abs(self.getAngleTo(pointForRetreat.x, pointForRetreat.y)));
        double a = nearestEnemyTarget.getDistanceTo();
        double b = self.getCastRange();
        return a * cosAlpha + sqrt(a * a * (cosAlpha * cosAlpha - 1) + b * b);
    }

    //=================================Collision handling===============================
    private ArrayDeque<LivingUnit> getCollisionList() {
        ArrayDeque<LivingUnit> collisionQueue = new ArrayDeque<>();
        ArrayDeque<LivingUnit> nextQueue;
        for (UnitType unitType : LIVING_UNIT_TYPES) {
            nextQueue = visibleEnemyUnitsByMe.get(unitType);
            addCollisionUnits(collisionQueue, nextQueue);
            nextQueue = visibleFriendUnitsByMe.get(unitType);
            addCollisionUnits(collisionQueue, nextQueue);
        }
        nextQueue = visibleNeutralMinionsByMe;
        addCollisionUnits(collisionQueue, nextQueue);
        nextQueue = visibleTreesByMe;
        addCollisionUnits(collisionQueue, nextQueue);

        return collisionQueue;
    }

    @SuppressWarnings("Convert2streamapi")
    private void addCollisionUnits(ArrayDeque<LivingUnit> collisionQueue, ArrayDeque<LivingUnit> queueToCheck) {
        for (LivingUnit unit : queueToCheck) {
            if (self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() + COLLISION_ERROR
                    && abs(self.getAngleTo(unit)) <= PI_BY_2 + 0.4)
                collisionQueue.add(unit);
        }
    }

    private void handleSingleCollision() {
        LivingUnit unit = collisions.peek();
        double alpha = self.getAngleTo(unit);
        boolean flag = alpha >= 0;
        alpha = PI_BY_2 - abs(alpha);
        double beta = (180.0 - alpha) / 2.0;
        double a = self.getRadius();
        double b = unit.getRadius();
        double d = (a * sin(alpha)) / sin(beta);
        double c = (b * d) / a;
        double x = min(sin(alpha) * (c + d) + 0.5, game.getWizardStrafeSpeed());
        if (flag) x = -x;
        move.setStrafeSpeed(x);
    }

    private boolean isColliding(LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() + COLLISION_ERROR;
    }

    //=================================WayPoints===============================
    private void findNextWayPoint() {
        Point2D closest = wayPoints[wayPoints.length - 1];
        double closestDist = closest.getDistanceTo(self);
        int closestIndex = wayPoints.length - 1;
        double currentDist;
        double x, y;

        for (int i = wayPoints.length - 2; i >= 0; i--) {
            currentDist = wayPoints[i].getDistanceTo(self);
            if (currentDist <= WAY_POINT_RADIUS) {
                if (i + 1 != nextWayPointIndex) {
                    x = wayPoints[i + 1].x + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
                    y = wayPoints[i + 1].y + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
                    x = min(x, 3964.0);
                    x = max(x, 36.0);
                    y = min(y, 3964.0);
                    y = max(y, 36.0);
                    nextWayPoint = new Point2D(x, y);
                    nextWayPointIndex = i + 1;
                }
                return;
            }
            if (currentDist < closestDist) {
                closestDist = currentDist;
                closest = wayPoints[i];
                closestIndex = i;
            }
        }

        if (closestIndex != nextWayPointIndex) {
            x = closest.x + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
            y = closest.y + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
            nextWayPoint = new Point2D(x, y);
            nextWayPointIndex = closestIndex;
        }
    }

    private void findPreviousWayPoint() {
        Point2D closest = wayPoints[0];
        double closestDist = closest.getDistanceTo(self);
        int closestIndex = 0;
        double currentDist;
        double x, y;

        for (int i = 1; i < wayPoints.length; i++) {
            currentDist = wayPoints[i].getDistanceTo(self);
            if (currentDist <= WAY_POINT_RADIUS) {
                if (i - 1 != previousWayPointIndex) {
                    x = wayPoints[i - 1].x + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
                    y = wayPoints[i - 1].y + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
                    x = min(x, 3964.0);
                    x = max(x, 36.0);
                    y = min(y, 3964.0);
                    y = max(y, 36.0);
                    previousWayPoint = new Point2D(x, y);
                    previousWayPointIndex = i - 1;
                }
                return;
            }
            if (currentDist < closestDist) {
                closestDist = currentDist;
                closest = wayPoints[i];
                closestIndex = i;
            }
        }

        if (closestIndex != previousWayPointIndex) {
            x = closest.x + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
            y = closest.y + random.nextDouble() * 2 * WAY_POINT_RANGE - WAY_POINT_RANGE;
            previousWayPoint = new Point2D(x, y);
            previousWayPointIndex = closestIndex;
        }
    }

    //=================================Classes=================================
    @SuppressWarnings({"WeakerAccess", "unused"})
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
            return hypot(this.x - x, this.y - y);
        }

        public double getDistanceTo(Point2D point) {
            return getDistanceTo(point.x, point.y);
        }

        public double getDistanceTo(Unit unit) {
            return getDistanceTo(unit.getX(), unit.getY());
        }

        @Override
        public boolean equals(Object obj) {
            return !(obj == null || !(obj instanceof Point2D))
                    && this.x == ((Point2D) obj).x && this.y == ((Point2D) obj).y;
        }
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
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
            return this.getLivingUnit() != null && this.getLivingUnit().getLife() > 0;
        }

        public boolean isInCastRange(Wizard self) {
            return this.isFound() && this.distanceTo <= self.getCastRange();
        }

        public boolean isInVisionRange(Wizard self) {
            return this.isFound() && this.distanceTo <= self.getVisionRange();
        }

        public boolean isInStaffSector(Wizard self, Game game) {
            return this.isInCastRange(self) && abs(this.angleTo) <= game.getStaffSector() / 2.0;
        }
    }

    @SuppressWarnings("unused")
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