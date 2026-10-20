package ml.mypals.controlPlayer.core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Only an integrated singleplayer world's actual owner gets menu access
 * without cheats. LAN guests and dedicated-server visitors do not.
 */
public final class PlayerControlAccess {
    private PlayerControlAccess() {}

    public static boolean isSingleplayerOwner(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null
                && server.isSingleplayer()
                && server.getSingleplayerProfile() != null
                // The UUID of the real player remains stable during a PlayerControl swap,
                // even while the displayed GameProfile name is temporarily exchanged.
                && server.getSingleplayerProfile().id().equals(player.getUUID());
    }

    public static boolean canManage(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || isSingleplayerOwner(player);
    }

    public static boolean canManage(CommandSourceStack source) {
        if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        return player != null && isSingleplayerOwner(player);
    }
}
