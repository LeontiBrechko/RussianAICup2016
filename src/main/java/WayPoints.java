import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public class WayPoints {
    private Map<LaneType, Point2D[]> pointsByLane;
    private LaneType currentLane;
    private Point2D[] currentLaneWayPoints;
    private Point2D[] bonusWayPoints;

    private int nextWayPointIndex;
    private int previousWayPointIndex;
    private int closestWayPointIndex;
    private Point2D nextWayPoint;
    private Point2D previousWayPoint;
    private Point2D closestWayPoint;
    private Point2D wayPointBeforeBonus;

    public WayPoints(Wizard self, Game game) {
        resetCurrentLane();
        defineLanesWayPoints(self.getFaction(), game);
    }

    public Map<LaneType, Point2D[]> getPointsByLane() {
        return pointsByLane;
    }

    public LaneType getCurrentLane() {
        return currentLane;
    }

    public Point2D[] getCurrentLaneWayPoints() {
        return currentLaneWayPoints;
    }

    public Point2D[] getBonusWayPoints() {
        return bonusWayPoints;
    }

    public int getNextWayPointIndex() {
        return nextWayPointIndex;
    }

    public int getPreviousWayPointIndex() {
        return previousWayPointIndex;
    }

    public int getClosestWayPointIndex() {
        return closestWayPointIndex;
    }

    public Point2D getNextWayPoint() {
        return nextWayPoint;
    }

    public Point2D getPreviousWayPoint() {
        return previousWayPoint;
    }

    public Point2D getClosestWayPoint() {
        return closestWayPoint;
    }

    public Point2D getWayPointBeforeBonus() {
        return wayPointBeforeBonus;
    }

    public void setWayPointBeforeBonus(Point2D wayPointBeforeBonus) {
        this.wayPointBeforeBonus = wayPointBeforeBonus;
    }

    // PUBLIC METHODS

    public void determineWayToGo(Wizard self, World world, Game game, Faction friendFaction) {
        currentLane = determineLane(self, world, friendFaction);
        currentLaneWayPoints = pointsByLane.get(currentLane);
        bonusWayPoints = getBonusWayPoints(game);
    }

    public void findTickWayPoints(Wizard self, Random random) {
        findClosestWayPoint(self, random);
        findNextWayPoint(self, random);
        findPreviousWayPoint(self, random);
    }

    public boolean isLaneDetermined() {
        return currentLane != null;
    }

    public Point2D getCentralPoint() {
        Point2D centralPoint;
        switch (currentLane) {
            case TOP:
            case BOTTOM:
                centralPoint = currentLaneWayPoints[11];
                break;
            case MIDDLE:
                centralPoint = currentLaneWayPoints[9];
                break;
            default:
                throw new EnumConstantNotPresentException(LaneType.class, "No such lane type");
        }
        return centralPoint;
    }

    public int getNearestEnemyWayPointIndex(LivingUnit enemy) {
        double currentDistance;
        int closestWayPointIndex = -1;
        double closestDistance = Float.MAX_VALUE;

        for (int i = currentLaneWayPoints.length - 1; i >= 0; i--) {
            currentDistance = currentLaneWayPoints[i].getDistanceTo(enemy);
            if (currentDistance < closestDistance) {
                closestDistance = currentDistance;
                closestWayPointIndex = i;
            }
        }

        return closestWayPointIndex;
    }

    // PRIVATE METHODS
    private void resetCurrentLane() {
        currentLane = null;
        currentLaneWayPoints = null;
        bonusWayPoints = null;

        nextWayPointIndex = -1;
        previousWayPointIndex = -1;
        closestWayPointIndex = -1;
        nextWayPoint = null;
        previousWayPoint = null;
        closestWayPoint = null;
    }

    private void defineLanesWayPoints(Faction faction, Game game) {
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
        for (int i = 13, j = 0; i <= 17; i++, j++) {
            top[i] = new Point2D(mapSize * ((2.0 + j) / 10.0), mapSize * 0.05);
        }
        top[18] = new Point2D(mapSize * 0.75, mapSize * 0.075);
        top[19] = new Point2D(mapSize * 0.8, mapSize * 0.075);
        top[20] = new Point2D(mapSize * 0.85, mapSize * 0.075);
        top[21] = new Point2D(mapSize * 0.9 + 20.0, mapSize * 0.075);
        top[22] = new Point2D(mapSize * 0.95, mapSize * 0.075);

        // MIDDLE
        middle = new Point2D[19];
        middle[0] = new Point2D(mapSize * 0.05, mapSize * 0.95);
        middle[1] = new Point2D(mapSize * 0.05, mapSize * 0.9 + 20.0);
        for (int i = 2; i <= 13; i++) {
            middle[i] = new Point2D(mapSize * (((i - 2.0) / 2.0 + 1.5) / 10.0),
                    mapSize * ((9.5 - i * 0.5) / 10.0));
        }
        middle[14] = new Point2D(mapSize * 0.77, mapSize * 0.28);
        middle[15] = new Point2D(mapSize * 0.82, mapSize * 0.23);
        middle[16] = new Point2D(mapSize * 0.87, mapSize * 0.18);
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
        for (int i = 13, j = 0; i <= 17; i++, j++) {
            bottom[i] = new Point2D(mapSize * 0.95, mapSize * ((8.0 - j) / 10.0));
        }
        bottom[18] = new Point2D(mapSize * 0.975, mapSize * 0.25);
        bottom[19] = new Point2D(mapSize * 0.975, mapSize * 0.2);
        bottom[20] = new Point2D(mapSize * 0.975, mapSize * 0.15);
        bottom[21] = new Point2D(mapSize * 0.975, mapSize * 0.1 - 20.0);
        bottom[22] = new Point2D(mapSize * 0.975, mapSize * 0.95);

        pointsByLane = new EnumMap<>(LaneType.class);
        pointsByLane.put(LaneType.TOP, top);
        pointsByLane.put(LaneType.MIDDLE, middle);
        pointsByLane.put(LaneType.BOTTOM, bottom);
    }

    private LaneType determineLane(Wizard self, World world, Faction friendFaction) {
        LaneType lane = null;
        Message[] messages = self.getMessages();
        if (!self.isMaster() && messages != null) {
            for (int i = messages.length - 1; i >= 0; i--) {
                if (messages[i] != null && messages[i].getLane() != null) {
                    lane = messages[i].getLane();
                    break;
                }
            }
        }

        if (lane != null) return lane;

        HashSet<Long> ids = new HashSet<>();
        int topCount = 0;
        int middleCount = 0;
        int bottomCount = 0;

        if (world.getTickIndex() < Constants.START_GAME_HOLD) {
            double topAngle, middleAngle, bottomAngle, minAngle;
            Point2D top = pointsByLane.get(LaneType.TOP)[5];
            Point2D middle = pointsByLane.get(LaneType.MIDDLE)[5];
            Point2D bottom = pointsByLane.get(LaneType.BOTTOM)[5];

            for (Wizard wizard : world.getWizards()) {
                if (wizard.isMe() || wizard.getFaction() != friendFaction) continue;
                topAngle = abs(wizard.getAngleTo(top.getX(), top.getY()));
                middleAngle = abs(wizard.getAngleTo(middle.getX(), middle.getY()));
                bottomAngle = abs(wizard.getAngleTo(bottom.getX(), bottom.getY()));
                minAngle = min(topAngle, min(middleAngle, bottomAngle));
                if (minAngle == topAngle) topCount++;
                else if (minAngle == middleAngle) middleCount++;
                else bottomCount++;
            }
        } else {
            Point2D[] top = pointsByLane.get(LaneType.TOP);
            Point2D[] middle = pointsByLane.get(LaneType.MIDDLE);
            Point2D[] bottom = pointsByLane.get(LaneType.BOTTOM);
            topCount = countWizards(top, ids, world, friendFaction);
            middleCount = countWizards(middle, ids, world, friendFaction);
            bottomCount = countWizards(bottom, ids, world, friendFaction);
        }

        // TODO: check properly when don't have enough wizards
        int min = min(topCount, min(middleCount, bottomCount));
        if (min == middleCount) lane = LaneType.MIDDLE;
        else if (min == topCount) lane = LaneType.TOP;
        else lane = LaneType.BOTTOM;
        return lane;
    }

    private int countWizards(Point2D[] wayPoints, HashSet<Long> isCounted, World world, Faction friendFaction) {
        int count = 0;
        for (Point2D point : wayPoints) {
            for (Wizard wizard : world.getWizards()) {
                if (wizard.getFaction() != friendFaction || wizard.isMe()
                        || isCounted.contains(wizard.getId())) continue;
                if (point.getDistanceTo(wizard) <= Constants.WAY_POINT_RADIUS + 50.0) {
                    count++;
                    isCounted.add(wizard.getId());
                }
            }
        }
        return count;
    }

    private Point2D[] getBonusWayPoints(Game game) {
        double mapSize = game.getMapSize();
        Point2D[] bonusWayPoints;
        switch (currentLane) {
            case TOP:
                bonusWayPoints = new Point2D[]{new Point2D(mapSize * 0.3, mapSize * 0.3)};
                break;
            case MIDDLE:
                bonusWayPoints = new Point2D[]{
                        new Point2D(mapSize * 0.3, mapSize * 0.3),
                        new Point2D(mapSize * 0.7, mapSize * 0.7)};
                break;
            default:
                bonusWayPoints = new Point2D[]{new Point2D(mapSize * 0.7, mapSize * 0.7)};
                break;
        }
        return bonusWayPoints;
    }

    private void findNextWayPoint(Wizard self, Random random) {
        double currentDist;
        double x, y;

        for (int i = currentLaneWayPoints.length - 2; i >= 0; i--) {
            currentDist = currentLaneWayPoints[i].getDistanceTo(self);
            if (currentDist <= Constants.WAY_POINT_RADIUS) {
                if (i + 1 != nextWayPointIndex) {
                    if (i + 1 < currentLaneWayPoints.length - 5) {
                        x = currentLaneWayPoints[i + 1].getX() +
                                random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                        y = currentLaneWayPoints[i + 1].getY() +
                                random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                        x = min(x, 3964.0);
                        x = max(x, 36.0);
                        y = min(y, 3964.0);
                        y = max(y, 36.0);
                        nextWayPoint = new Point2D(x, y);
                        nextWayPointIndex = i + 1;
                    } else {
                        nextWayPoint = currentLaneWayPoints[i + 1];
                        nextWayPointIndex = i + 1;
                    }
                }
                return;
            }
        }

        if (closestWayPointIndex != nextWayPointIndex) {
            nextWayPoint = closestWayPoint;
            nextWayPointIndex = closestWayPointIndex;
        }
    }

    private void findPreviousWayPoint(Wizard self, Random random) {
        double currentDist;
        double x, y;

        for (int i = 1; i < currentLaneWayPoints.length; i++) {
            currentDist = currentLaneWayPoints[i].getDistanceTo(self);
            if (currentDist <= Constants.WAY_POINT_RADIUS) {
                if (i - 1 != previousWayPointIndex) {
                    if (i - 1 < currentLaneWayPoints.length - 5) {
                        x = currentLaneWayPoints[i - 1].getX()
                                + random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                        y = currentLaneWayPoints[i - 1].getY()
                                + random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                        x = min(x, 3964.0);
                        x = max(x, 36.0);
                        y = min(y, 3964.0);
                        y = max(y, 36.0);
                        previousWayPoint = new Point2D(x, y);
                        previousWayPointIndex = i - 1;
                    } else {
                        previousWayPoint = currentLaneWayPoints[i - 1];
                        previousWayPointIndex = i - 1;
                    }
                }
                return;
            }
        }

        if (closestWayPointIndex != previousWayPointIndex) {
            previousWayPoint = closestWayPoint;
            previousWayPointIndex = closestWayPointIndex;
        }
    }

    private void findClosestWayPoint(Wizard self, Random random) {
        Point2D closest = currentLaneWayPoints[0];
        int closestIndex = 0;
        double closestDistance = closest.getDistanceTo(self);
        double currentDistance;

        for (int i = 1; i < currentLaneWayPoints.length; i++) {
            currentDistance = currentLaneWayPoints[i].getDistanceTo(self);
            if (currentDistance < closestDistance) {
                closestDistance = currentDistance;
                closestIndex = i;
                closest = currentLaneWayPoints[i];
            }
        }

        if (closestIndex != closestWayPointIndex) {
            if (closestIndex < currentLaneWayPoints.length - 5) {
                double x = closest.getX()
                        + random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                double y = closest.getY()
                        + random.nextDouble() * 2 * Constants.WAY_POINT_RANGE - Constants.WAY_POINT_RANGE;
                x = min(x, 3964.0);
                x = max(x, 36.0);
                y = min(y, 3964.0);
                y = max(y, 36.0);
                closestWayPoint = new Point2D(x, y);
                closestWayPointIndex = closestIndex;
            } else {
                closestWayPoint = closest;
                closestWayPointIndex = closestIndex;
            }
        }
    }
}
