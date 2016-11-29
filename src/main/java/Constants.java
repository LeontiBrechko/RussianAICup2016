import model.*;

import static model.SkillType.*;
import static java.lang.StrictMath.*;

@SuppressWarnings("WeakerAccess")
public class Constants {
    public static final UnitType[] LIVING_UNIT_TYPES =
            new UnitType[]{UnitType.BUILDING, UnitType.MINION, UnitType.WIZARD, UnitType.TREE};
    public static final double NEAREST_UNIT_ERROR = 70.0;
    public static final double BRAVERY_ERROR = 35.0;
    public static final double VISION_ERROR = 0.1;
    public static final double WAY_POINT_RADIUS = sqrt(80000.0) + 90.0;
    public static final double WAY_POINT_RANGE = 150.0;
    public static final int RUN_AWAY_COUNTDOWN = 60;
    public static final double BONUS_VISION_ERROR = 100.0;
    public static final int BONUS_AHEAD_TIME = 380;
    public static final double COLLISION_ERROR = 7.6;
    public static final int START_GAME_HOLD = 120;
    public static final double SAFE_RANGE = 35.0;
    public static final SkillType[] SKILLS_TO_LEARN = new SkillType[]{
            MAGICAL_DAMAGE_BONUS_PASSIVE_1, MAGICAL_DAMAGE_BONUS_AURA_1, MAGICAL_DAMAGE_BONUS_PASSIVE_2,
            MAGICAL_DAMAGE_BONUS_AURA_2, FROST_BOLT, RANGE_BONUS_PASSIVE_1, RANGE_BONUS_AURA_1, RANGE_BONUS_PASSIVE_2,
            RANGE_BONUS_AURA_2, ADVANCED_MAGIC_MISSILE, MOVEMENT_BONUS_FACTOR_PASSIVE_1, MOVEMENT_BONUS_FACTOR_AURA_1,
            MOVEMENT_BONUS_FACTOR_PASSIVE_2, MOVEMENT_BONUS_FACTOR_AURA_2
    };
}
