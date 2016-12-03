import model.SkillType;

import static java.lang.StrictMath.sqrt;
import static model.SkillType.*;

@SuppressWarnings("WeakerAccess")
public class Constants {
    public static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    public static final double NEAREST_UNIT_ERROR = 70.0;
    public static final double BRAVERY_ERROR = 35.0;
    public static final double WAY_POINT_RADIUS = sqrt(80000.0) + 90.0;
    public static final double WAY_POINT_RANGE = 150.0;
    public static final int RUN_AWAY_COUNTDOWN = 60;
    public static final double BONUS_VISION_ERROR = 100.0;
    public static final int BONUS_AHEAD_TIME = 380;
    public static final double COLLISION_ERROR = 7.6;
    public static final int START_GAME_HOLD = 120;
    public static final double SAFE_RANGE = 100.0;
    public static final SkillType[] SKILLS_TO_LEARN = new SkillType[]{
            RANGE_BONUS_PASSIVE_1, RANGE_BONUS_AURA_1, RANGE_BONUS_PASSIVE_2,
            RANGE_BONUS_AURA_2, ADVANCED_MAGIC_MISSILE, MAGICAL_DAMAGE_BONUS_PASSIVE_1, MAGICAL_DAMAGE_BONUS_AURA_1,
            MAGICAL_DAMAGE_BONUS_PASSIVE_2, MAGICAL_DAMAGE_BONUS_AURA_2, FROST_BOLT, MOVEMENT_BONUS_FACTOR_PASSIVE_1,
            MOVEMENT_BONUS_FACTOR_AURA_1, MOVEMENT_BONUS_FACTOR_PASSIVE_2, MOVEMENT_BONUS_FACTOR_AURA_2
    };

    // Potential field constants
    public static final int STEP_SIZE = 8;
    public static final int NOT_PASSABLE_POTENTIAL = -5150;
    public static final int COLLISION_RADIUS = 35;
    public static final int COLLESION_BORDER_LENGTH = 3;
    public static final int DEBUG_RANGE = 200;
}
