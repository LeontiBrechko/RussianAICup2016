import model.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings({"WeakerAccess", "Convert2streamapi"})
public class CollisionHandler {
    private static final double COLLISION_ERROR = 7.0;
    private static final double PI_BY_2 = PI / 2.0;

    private Wizard self;

    public CollisionHandler(Wizard self) {
        this.self = self;
    }

    public void setSelf(Wizard self) {
        this.self = self;
    }

//    public void handlePossibleCollisions(ArrayDeque<LivingUnit> visibleUnitsByMe, Move move) {
//        double speedVectorNorm = hypot(move.getStrafeSpeed(), move.getSpeed());
//        double finalX = self.getX() + cos(self.getAngle()) * speedVectorNorm;
//        double finalY = self.getY() + sin(self.getAngle()) * speedVectorNorm;
//        double finalVectorAngle = self.getAngleTo(finalX, finalY);
//        double unitAngle;
//        double unitVectorAngle;
//
//        // TODO: shift final vector beginning for a self radius distance
//        ArrayList<CollisionUnit> possibleCollisionUnits = new ArrayList<>();
//        for (LivingUnit unit : visibleUnitsByMe) {
//            unitAngle = self.getAngleTo(unit);
//            if (finalVectorAngle * unitAngle < 0) {
//                unitVectorAngle = 2 * PI - abs(finalVectorAngle) - abs(unitAngle);
//                if (unitAngle >= 0) unitVectorAngle = -unitVectorAngle;
//            } else unitVectorAngle = unitAngle - finalVectorAngle;
//            if (abs(unitVectorAngle) <= PI_BY_2) {
//                possibleCollisionUnits.add(new CollisionUnit(unit, self.getDistanceTo(unit), unitVectorAngle));
//            }
//        }
//
//        Collections.sort(possibleCollisionUnits);
//
//    }

    public void handleSingleCollision(ArrayDeque<LivingUnit> visibleUnitsByMe, Move move) {
        double minDistance = Double.MAX_VALUE;
        LivingUnit collision = null;
        double currentDistance;

        for (LivingUnit unit : visibleUnitsByMe) {
            if (isColliding(unit, move)) {
                currentDistance = self.getDistanceTo(unit);
                if (currentDistance < minDistance) {
                    minDistance = currentDistance;
                    collision = unit;
                }
            }
        }

        if (collision != null) {
            double speedVectorNorm = hypot(move.getStrafeSpeed(), move.getSpeed());
            double speedVectorAngle = asin(abs(move.getStrafeSpeed() / speedVectorNorm));
            if (move.getStrafeSpeed() < 0) speedVectorAngle = PI - speedVectorAngle;
            if (move.getSpeed() < 0) speedVectorAngle = -speedVectorAngle;

            double speedVectorAngleToUnit;
            double selfAngleToUnit = self.getAngleTo(collision);
            if (selfAngleToUnit * speedVectorAngle < 0) {
                speedVectorAngleToUnit = 2 * PI - abs(speedVectorAngle) - abs(selfAngleToUnit);
                if (selfAngleToUnit >= 0) speedVectorAngleToUnit = -speedVectorAngleToUnit;
            } else speedVectorAngleToUnit = selfAngleToUnit - speedVectorAngle;

            boolean flag = speedVectorAngleToUnit >= 0;
            double unitAngleToSpeedVector = PI_BY_2 - abs(speedVectorAngleToUnit);
            double finalVectorAngle = (PI - unitAngleToSpeedVector) / 2.0;

            double a = self.getRadius();
            double b = collision.getRadius();
            double d = (a * sin(unitAngleToSpeedVector)) / sin(finalVectorAngle);
            double c = (b * d) / a;

            double finalStrafeSpeed = sin(unitAngleToSpeedVector) * (c + d);
            // TODO: calculate speed properly
            double finalSpeed = min(cos(unitAngleToSpeedVector) * (c + d), finalStrafeSpeed);
            if (flag) finalStrafeSpeed = -finalStrafeSpeed;
            move.setSpeed(0.0);
            move.setStrafeSpeed(finalStrafeSpeed);
        }
    }

    public boolean isColliding(LivingUnit unit, Move move) {
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
        return unit != null && self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() + COLLISION_ERROR;
    }

    private static class CollisionUnit implements Comparable<CollisionUnit> {
        private LivingUnit unit;
        private double distance;
        private double angle;

        public CollisionUnit(LivingUnit unit, double distance, double angle) {
            this.unit = unit;
            this.distance = distance;
            this.angle = angle;
        }

        @Override
        public int compareTo(@NotNull CollisionUnit o) {
            int res = Double.compare(this.distance, o.distance);
            if (res == 0) res = Double.compare(abs(this.angle), abs(o.angle));
            return res;
        }
    }
}
