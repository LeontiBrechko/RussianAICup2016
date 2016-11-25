import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public class Constants {
    public static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    public static final double NEAREST_UNIT_ERROR = 70.0;
    public static final double BRAVERY_ERROR = 10.0;
    public static final double VISION_ERROR = 0.1;
    public static final double WAY_POINT_RADIUS = sqrt(80000.0) + 90.0;
    public static final double WAY_POINT_RANGE = 150.0;
    public static final int RUN_AWAY_COUNTDOWN = 40;
    public static final double BONUS_VISION_ERROR = 100.0;
    public static final int BONUS_AHEAD_TIME = 400;
    public static final double COLLISION_ERROR = 7.6;
}
