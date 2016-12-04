import model.*;

import java.util.Optional;
import java.util.PriorityQueue;

import static java.lang.Math.abs;

@SuppressWarnings("WeakerAccess")
public class Utils {
    public static boolean isTowerInMyLane(WayPoints wayPoints, Building tower) {
        if (tower == null || tower.getType() != BuildingType.GUARDIAN_TOWER) return false;
        for (Point2D wayPoint : wayPoints.getCurrentLaneWayPoints()) {
            if (wayPoint.getDistanceTo(tower) <= Constants.WAY_POINT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    public static Optional<LivingUnit> nextBuildingTarget(Building building, World world, Game game) {
        PriorityQueue<LivingUnit> moreThanDamageUnits =
                new PriorityQueue<>(((o1, o2) -> Integer.compare(o1.getLife(), o2.getLife())));
        PriorityQueue<LivingUnit> lessThanDamageUnits =
                new PriorityQueue<>(((o1, o2) -> Integer.compare(o2.getLife(), o1.getLife())));
        for (Minion minion : world.getMinions()) {
            if (minion.getFaction() != building.getFaction() &&
                    building.getDistanceTo(minion) - minion.getRadius() <= game.getGuardianTowerAttackRange()) {
                if (minion.getLife() >= game.getGuardianTowerDamage()) moreThanDamageUnits.offer(minion);
                else lessThanDamageUnits.offer(minion);
            }
        }
        for (Wizard wizard : world.getWizards()) {
            if (wizard.getFaction() != building.getFaction() &&
                    building.getDistanceTo(wizard) - wizard.getRadius() <= game.getGuardianTowerAttackRange()) {
                if (wizard.getLife() >= game.getGuardianTowerDamage()) moreThanDamageUnits.offer(wizard);
                else lessThanDamageUnits.offer(wizard);
            }
        }
        if (moreThanDamageUnits.size() > 0) return Optional.of(moreThanDamageUnits.peek());
        else return Optional.ofNullable(lessThanDamageUnits.peek());
    }

    public static boolean isUnitInVisionRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() <= self.getVisionRange();
    }

    public static boolean isUnitInStaffSector(Wizard self, LivingUnit unit, Game game) {
        return unit != null && abs(self.getAngleTo(unit)) <= game.getStaffSector() / 2.0;
    }

    public static boolean isUnitInCastRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() <= self.getCastRange();
    }

    public static boolean isUnitInStaffRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() <= 70.0;
    }

    public static boolean isUnitInCollisionRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() - self.getRadius()
                <= Constants.COLLISION_BORDER_LENGTH + Constants.STEP_SIZE + 1;
    }
}
