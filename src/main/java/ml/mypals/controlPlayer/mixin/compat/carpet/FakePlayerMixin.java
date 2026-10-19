package ml.mypals.controlPlayer.mixin.compat.carpet;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import ml.mypals.controlPlayer.PlayerControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerMPFake.class, remap = false)
public abstract class FakePlayerMixin extends ServerPlayer {

    public FakePlayerMixin(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
        super(minecraftServer, serverLevel, gameProfile, clientInformation);
    }

    @Inject(
            method = "kill(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD")
    )  public void kill(CallbackInfo ci) {
        if(PlayerControl.getSwapManager().isSwapped(this.getUUID())){
            PlayerControl.getSwapManager().release(this,this.level().getServer());
        }
    }
}
