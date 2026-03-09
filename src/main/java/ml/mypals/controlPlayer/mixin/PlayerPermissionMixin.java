package ml.mypals.controlPlayer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import ml.mypals.controlPlayer.PlayerControl;
import ml.mypals.controlPlayer.core.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(MinecraftServer.class)
public abstract class PlayerPermissionMixin {

    @Shadow public abstract PlayerList getPlayerList();

    @Shadow public abstract GameRules getGameRules();

    @WrapMethod(method = "getProfilePermissions")
    public int getProfilePermissions(GameProfile gameProfile, Operation<Integer> original) {

        AtomicInteger result = new AtomicInteger();
        result.set(original.call(gameProfile));

        UUID uuid = gameProfile.getId();

        if(!this.getGameRules().getRule(ModGameRules.PERMISSION_SWAP).get()){
            if(PlayerControl.getSwapManager().isSwapped(uuid)){
                PlayerControl.getSwapManager().getSwapPartner(uuid).ifPresent(p -> {
                    ServerPlayer partner = this.getPlayerList().getPlayer(p);
                    if(partner != null){
                        int partnerLevel = original.call(partner.getGameProfile());
                        result.set(partnerLevel);
                    }
                });
            }
        }

        return result.get();
    }
}
