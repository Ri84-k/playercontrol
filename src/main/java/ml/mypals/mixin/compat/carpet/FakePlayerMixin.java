package ml.mypals.mixin.compat.carpet;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import ml.mypals.PlayerControl;
import ml.mypals.command.PlayerControlCommand;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
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
            method = "kill()V",
            cancellable = true,
            at = @At("HEAD")
    )  public void kill(CallbackInfo ci) {
        if(PlayerControl.getSwapManager().isSwapped(this.getUUID())){
            PlayerControl.getSwapManager().release(this,this.server);
        }
    }
}
