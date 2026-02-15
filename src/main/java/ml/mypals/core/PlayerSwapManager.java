package ml.mypals.core;

import com.mojang.authlib.GameProfile;
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

        snapB.applyTo(playerA, snapB.gameProfile);
        snapA.applyTo(playerB, snapA.gameProfile);

        return SwapResult.success(Component.empty());
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

        activeSwaps.remove(uidA);
        activeSwaps.remove(uidB);
        originalStates.remove(uidA);
        originalStates.remove(uidB);

        if (origA == null) {
            return SwapResult.fail(Component.translatable("controlplayer.error.original_missing"));
        }
        if (currentB != null) {
            currentB.applyTo(initiator, origA.gameProfile);
        } else {
            origA.applyTo(initiator, origA.gameProfile);
        }

        if (playerB != null) {
            GameProfile profileForB = (origB != null) ? origB.gameProfile : playerB.getGameProfile();
            currentA.applyTo(playerB, profileForB);
        }

        return SwapResult.success(Component.empty());
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

        activeSwaps.remove(targetUid);
        activeSwaps.remove(uidB);
        originalStates.remove(targetUid);
        originalStates.remove(uidB);

        ServerPlayer playerB = server.getPlayerList().getPlayer(uidB);
        if (playerB != null && origB != null) {
            origB.applyTo(playerB, origB.gameProfile);
            playerB.sendSystemMessage(Component.translatable("controlplayer.partner_offline_restored"));
        }
        return SwapResult.success(Component.empty());
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