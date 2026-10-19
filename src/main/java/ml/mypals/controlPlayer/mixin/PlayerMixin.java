package ml.mypals.controlPlayer.mixin;

import ml.mypals.controlPlayer.PlayerControl;
import ml.mypals.controlPlayer.core.ModGameRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "getName",at = @At("RETURN"), cancellable = true)
    public void getName(CallbackInfoReturnable<Component> cir) {
        String origin = cir.getReturnValue().getString();
        if(((Player)(Object)this) instanceof ServerPlayer serverPlayer){
            MinecraftServer server = serverPlayer.getServer();
            if(serverPlayer.level().getGameRules().get(ModGameRules.SHOW_PERFIX)){
                if(PlayerControl.getSwapManager().isSwapped(this.getUUID())){
                    PlayerControl.getSwapManager().getSwapPartner(this.uuid).ifPresent(p -> {
                        Player partner = server.getPlayerList().getPlayer(p);
                        if(partner != null){
                            cir.setReturnValue(Component.literal(origin + "(" + partner.getGameProfile().name() + ")"));
                        }
                    });
                }
            }
        }
    }
}
