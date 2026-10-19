package ml.mypals.controlPlayer.core;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** World-specific PlayerControl settings for Minecraft 26.2. */
public final class ModGameRules {
    private ModGameRules() {}

    public static final GameRule<Boolean> SHOW_PERFIX = GameRuleBuilder
            .forBoolean(false)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(Identifier.fromNamespaceAndPath("fackplayercontrol", "show_controller_prefix"));

    public static final GameRule<Boolean> PERMISSION_SWAP = GameRuleBuilder
            .forBoolean(false)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(Identifier.fromNamespaceAndPath("fackplayercontrol", "permission_swaps_too"));

    public static void init() {}
}
