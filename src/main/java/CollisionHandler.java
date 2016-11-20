import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public class CollisionHandler {
    private static final double COLLISION_ERROR = 7.6;

    public void handleSingleCollision(ArrayDeque<LivingUnit> visibleUnitsByMe, Wizard self, Move move) {
        double minDistance = Double.MAX_VALUE;
        // TODO: fix collision when self has multiple collisions
        LivingUnit collision = null;
        double currentDistance;

        for (LivingUnit unit : visibleUnitsByMe) {
            if (isColliding(unit, self)) {
                currentDistance = self.getDistanceTo(unit);
                if (currentDistance < minDistance) {
                    minDistance = currentDistance;
                    collision = unit;
                }
            }
        }

        if (collision != null) {
            if (self.getAngleTo(collision) < 0) move.setStrafeSpeed(8.0);
            else move.setStrafeSpeed(-8.0);
            move.setSpeed(0.0);
        }
    }

    public boolean isColliding(LivingUnit unit, Wizard self) {
        return unit != null && self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() + COLLISION_ERROR;
    }
}
