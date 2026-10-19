package ml.mypals.controlPlayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import ml.mypals.controlPlayer.PlayerControl;
import ml.mypals.controlPlayer.net.FakePlayerMenuPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PlayerControlClient implements ClientModInitializer {
    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(PlayerControl.MOD_ID, "controls"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fackplayercontrol.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                category
        ));

        ClientPlayNetworking.registerGlobalReceiver(FakePlayerMenuPackets.ListResponse.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft minecraft = context.client();
                if (minecraft.player == null) return;
                if (minecraft.gui.screen() instanceof FakePlayerScreen screen) {
                    screen.setPlayers(payload);
                } else if (minecraft.gui.screen() == null) {
                    minecraft.gui.setScreen(new FakePlayerScreen(payload));
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (!ClientPlayNetworking.canSend(FakePlayerMenuPackets.Request.TYPE)) {
                    client.player.sendSystemMessage(Component.translatable("controlplayer.menu.unavailable"));
                    continue;
                }
                client.gui.setScreen(new FakePlayerScreen());
            }
        });
    }
}
