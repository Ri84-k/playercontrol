package ml.mypals.controlPlayer;

import ml.mypals.controlPlayer.command.PlayerControlCommand;
import ml.mypals.controlPlayer.core.ModGameRules;
import ml.mypals.controlPlayer.core.PlayerSwapManager;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class PlayerControl implements ModInitializer {
	public static final String MOD_ID = "fackplayercontrol";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final PlayerSwapManager swapManager = new PlayerSwapManager();

	@Override
	public void onInitialize() {
		ModGameRules.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				new PlayerControlCommand(swapManager).register(dispatcher)
		);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				swapManager.release(handler.getPlayer(), server)
		);
		ServerLifecycleEvents.SERVER_STOPPING.register((minecraftServer)->{
			for(Map.Entry<UUID,UUID> swaps : swapManager.getAllSwapPairs()){
				ServerPlayer player = minecraftServer.getPlayerList().getPlayer(swaps.getKey());
				if(player != null) swapManager.release(player, minecraftServer);
			}
		});
	}

	public static PlayerSwapManager getSwapManager() {
		return swapManager;
	}
}