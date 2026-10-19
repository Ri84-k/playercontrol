package ml.mypals.controlPlayer.core;

import com.mojang.authlib.GameProfile;
import ml.mypals.controlPlayer.PlayerControl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class PlayerSwapManager {

    private final Map<UUID, UUID> activeSwaps = new HashMap<>();
    private final Map<UUID, SwapSnapshot> originalStates = new HashMap<>();

    public SwapResult swap(ServerPlayer playerA, ServerPlayer playerB) {
        UUID uidA = playerA.getUUID();
        UUID uidB = playerB.getUUID();

        if (uidA.equals(uidB)) {
            return SwapResult.fail(Component.translatable("controlplayer.error.self_swap"));
        }
        if (activeSwaps.containsKey(uidA)) {
            return SwapResult.fail(Component.translatable("controlplayer.error.already_swapped", playerA.getName()));
        }
        if (activeSwaps.containsKey(uidB)) {
            return SwapResult.fail(Component.translatable("controlplayer.error.already_swapped", playerB.getName()));
        }

        SwapSnapshot snapA = SwapSnapshot.capture(playerA);
        SwapSnapshot snapB = SwapSnapshot.capture(playerB);

        activeSwaps.put(uidA, uidB);
        activeSwaps.put(uidB, uidA);
        originalStates.put(uidA, snapA);
        originalStates.put(uidB, snapB);

        try {
            snapB.applyTo(playerA, snapB.gameProfile);
            snapA.applyTo(playerB, snapA.gameProfile);

            playerA.level().getServer().getPlayerList().sendPlayerPermissionLevel(playerA);
            playerB.level().getServer().getPlayerList().sendPlayerPermissionLevel(playerB);
            return SwapResult.success(Component.empty());
        } catch (RuntimeException failure) {
            PlayerControl.LOGGER.error("Player swap failed for {} and {}; attempting rollback", uidA, uidB, failure);
            boolean recoveredA = restoreAfterFailure(playerA, snapA);
            boolean recoveredB = restoreAfterFailure(playerB, snapB);
            if (recoveredA && recoveredB) {
                clearSwap(uidA, uidB);
            } else {
                PlayerControl.LOGGER.error("Incomplete swap rollback! Pair {} / {} retained for recovery", uidA, uidB);
            }
            return SwapResult.fail(Component.translatable("controlplayer.error.swap_failed"));
        }
    }

    public SwapResult release(ServerPlayer initiator, MinecraftServer server) {
        UUID uidA = initiator.getUUID();
        if (!activeSwaps.containsKey(uidA)) {
            return SwapResult.fail(Component.translatable("controlplayer.error.not_swapped"));
        }

        UUID uidB = activeSwaps.get(uidA);
        ServerPlayer playerB = server.getPlayerList().getPlayer(uidB);

        SwapSnapshot origA = originalStates.get(uidA);
        SwapSnapshot origB = originalStates.get(uidB);

        SwapSnapshot currentA = SwapSnapshot.capture(initiator);
        SwapSnapshot currentB = (playerB != null) ? SwapSnapshot.capture(playerB) : null;

        if (origA == null) {
            return SwapResult.fail(Component.translatable("controlplayer.error.original_missing"));
        }
        try {
            if (currentB != null) {
                currentB.applyTo(initiator, origA.gameProfile);
            } else {
                origA.applyTo(initiator, origA.gameProfile);
            }
            if (playerB != null) {
                GameProfile profileForB = (origB != null) ? origB.gameProfile : playerB.getGameProfile();
                currentA.applyTo(playerB, profileForB);
            }

            initiator.level().getServer().getPlayerList().sendPlayerPermissionLevel(initiator);
            if (playerB != null) playerB.level().getServer().getPlayerList().sendPlayerPermissionLevel(playerB);
            clearSwap(uidA, uidB);
            return SwapResult.success(Component.empty());
        } catch (RuntimeException failure) {
            PlayerControl.LOGGER.error("Player release failed for {} and {}; attempting rollback", uidA, uidB, failure);
            restoreAfterFailure(initiator, currentA);
            if (playerB != null && currentB != null) restoreAfterFailure(playerB, currentB);
            return SwapResult.fail(Component.translatable("controlplayer.error.release_failed"));
        }
    }

    public SwapResult forceRelease(UUID targetUid, MinecraftServer server) {
        if (!activeSwaps.containsKey(targetUid)) {
            return SwapResult.fail(Component.translatable("controlplayer.error.not_swapped"));
        }

        ServerPlayer playerA = server.getPlayerList().getPlayer(targetUid);
        if (playerA != null) {
            return release(playerA, server);
        }

        UUID uidB = activeSwaps.get(targetUid);
        SwapSnapshot origB = originalStates.get(uidB);

        ServerPlayer playerB = server.getPlayerList().getPlayer(uidB);
        try {
            if (playerB != null && origB != null) {
                origB.applyTo(playerB, origB.gameProfile);
                playerB.sendSystemMessage(Component.translatable("controlplayer.partner_offline_restored"));
                playerB.level().getServer().getPlayerList().sendPlayerPermissionLevel(playerB);
            }
            clearSwap(targetUid, uidB);
            return SwapResult.success(Component.empty());
        } catch (RuntimeException failure) {
            PlayerControl.LOGGER.error("Forced player release failed for {}", targetUid, failure);
            return SwapResult.fail(Component.translatable("controlplayer.error.release_failed"));
        }
    }

    private void clearSwap(UUID first, UUID second) {
        activeSwaps.remove(first);
        activeSwaps.remove(second);
        originalStates.remove(first);
        originalStates.remove(second);
    }

    private static boolean restoreAfterFailure(ServerPlayer player, SwapSnapshot snapshot) {
        try {
            snapshot.applyTo(player, snapshot.gameProfile);
            return true;
        } catch (RuntimeException failure) {
            PlayerControl.LOGGER.error("Failed restoring {} during swap rollback", player.getUUID(), failure);
            return false;
        }
    }

    public boolean isSwapped(UUID uid) {
        return activeSwaps.containsKey(uid);
    }

    public Optional<UUID> getSwapPartner(UUID uid) {
        return Optional.ofNullable(activeSwaps.get(uid));
    }

    public List<Map.Entry<UUID, UUID>> getAllSwapPairs() {
        Set<UUID> seen = new HashSet<>();
        List<Map.Entry<UUID, UUID>> pairs = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : activeSwaps.entrySet()) {
            if (seen.add(entry.getKey()) && seen.add(entry.getValue())) {
                pairs.add(entry);
            }
        }
        return pairs;
    }

    public record SwapResult(boolean success, Component message) {
        public static SwapResult fail(Component message) { return new SwapResult(false, message); }
        public static SwapResult success(Component message) { return new SwapResult(true, Component.empty()); }
    }
}