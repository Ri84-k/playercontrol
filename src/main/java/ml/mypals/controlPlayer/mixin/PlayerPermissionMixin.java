package ml.mypals.controlPlayer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ml.mypals.controlPlayer.PlayerControl;
import ml.mypals.controlPlayer.core.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(MinecraftServer.class)
public abstract class PlayerPermissionMixin {
    @Shadow public abstract PlayerList getPlayerList();
    @Shadow public abstract GameRules getGameRules();

    @WrapMethod(method = "getProfilePermissions")
    private PermissionSet playercontrol$permissions(NameAndId profile, Operation<PermissionSet> original) {
        PermissionSet own = original.call(profile);
        // Swapped users normally keep permission levels attached to their real UUID.
        if (!this.getGameRules().get(ModGameRules.PERMISSION_SWAP)) return own;
        UUID other = PlayerControl.getSwapManager().getSwapPartner(profile.id()).orElse(null);
        if (other == null) return own;
        ServerPlayer partner = this.getPlayerList().getPlayer(other);
        return partner == null ? own : original.call(partner.nameAndId());
    }
}
