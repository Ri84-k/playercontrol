package ml.mypals.controlPlayer.core;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

public class ModGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> SHOW_PERFIX =
            GameRuleRegistry.register("showControllerPerfix", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
    public static final GameRules.Key<GameRules.BooleanValue> PERMISSION_SWAP =
            GameRuleRegistry.register("permissionSwapsToo", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
    public static void init(){}
}

