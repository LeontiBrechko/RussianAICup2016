import model.Game;
import model.LivingUnit;
import model.Wizard;

import static java.lang.Math.abs;

@SuppressWarnings("WeakerAccess")
public class Utils {
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
                <= Constants.COLLESION_BORDER_LENGTH + 1.0;
    }
}
