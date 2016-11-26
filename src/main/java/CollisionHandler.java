import model.*;

import java.util.*;

import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public class CollisionHandler {
    public void handleSingleCollision(ArrayDeque<LivingUnit> visibleUnitsByMe, Wizard self, Move move) {
        double minDistance = Double.MAX_VALUE;
        // TODO: fix collision when self has multiple collisions
        LivingUnit collision = null;
        double currentDistance;
        int count = 0;

        for (LivingUnit unit : visibleUnitsByMe) {
            if (isColliding(unit, self)) {
                count++;
                currentDistance = self.getDistanceTo(unit);
                if (currentDistance < minDistance) {
                    minDistance = currentDistance;
                    collision = unit;
                }
            }
        }

        if (collision != null && count == 1) {
            if (self.getAngleTo(collision) < 0) move.setStrafeSpeed(8.0);
            else move.setStrafeSpeed(-8.0);
            move.setSpeed(0.0);
        }
    }

    public boolean isColliding(LivingUnit unit, Wizard self) {
        return unit != null && self.getDistanceTo(unit) <= self.getRadius() + unit.getRadius() +
                Constants.COLLISION_ERROR;
    }
}
