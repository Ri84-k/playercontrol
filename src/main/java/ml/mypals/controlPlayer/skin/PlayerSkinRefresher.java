package ml.mypals.controlPlayer.skin;

import com.google.common.collect.ImmutableMultimap;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import ml.mypals.controlPlayer.mixin.PlayerAccessor;
import ml.mypals.controlPlayer.mixin.TrackedEntityAccessor;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.server.players.PlayerList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class PlayerSkinRefresher {

    private static final String TEXTURES_KEY = "textures";

    public static void applyProfile(ServerPlayer player, GameProfile newProfile) {
        GameProfile current = player.getGameProfile();
        // Authlib 26.2 exposes PropertyMap as immutable. Build all entries first,
        // then construct the immutable PropertyMap in one step.
        ImmutableMultimap.Builder<String, Property> properties = ImmutableMultimap.builder();
        for (var entry : current.properties().entries()) {
            if (!entry.getKey().equals(TEXTURES_KEY)) {
                properties.put(entry.getKey(), entry.getValue());
            }
        }
        for (Property texProp : newProfile.properties().get(TEXTURES_KEY)) {
            properties.put(TEXTURES_KEY, texProp);
        }

        GameProfile applied = new GameProfile(current.id(), newProfile.name(),
                new PropertyMap(properties.build()));
        ((PlayerAccessor) player).setGameProfile(applied);

        PlayerList playerList = Objects.requireNonNull(player.level().getServer()).getPlayerList();
        playerList.broadcastAll(new ClientboundBundlePacket(List.of(
                new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())),
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(Collections.singleton(player))
        )));

    }

    public static void refreshAfterRestore(ServerPlayer player, boolean changedDimension) {
        PlayerList playerList = Objects.requireNonNull(player.level().getServer()).getPlayerList();
        ServerLevel level = player.level();
        boolean fake = player instanceof EntityPlayerMPFake;
        if (!fake && !player.isDeadOrDying()) {
            // A real cross-dimension teleport already sends a respawn packet.
            // For a same-dimension swap, refresh the local cosmetic profile only
            // after all snapshot state is final, then send its actual position.
            if (!changedDimension && !player.isPassenger() && !player.isVehicle() && !player.isSleeping()) {
                player.connection.send(new ClientboundBundlePacket(List.of(
                        new ClientboundRespawnPacket(
                                player.createCommonSpawnInfo(level),
                                ClientboundRespawnPacket.KEEP_ALL_DATA
                        ),
                        new ClientboundGameEventPacket(
                                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0)
                )));
                player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot());
            }
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            playerList.sendLevelInfo(player, level);
            playerList.sendAllPlayerInfo(player);
            for (var effect : player.getActiveEffects()) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
            }
        }
        // Never send a synthetic respawn or a direct passenger packet to a fake
        // connection. Let normal entity tracking supply passenger relationships.
        player.connection.resetPosition();
        level.getChunkSource().move(player);
        refreshEntityTracking(player);
        playerList.sendPlayerPermissionLevel(player);
    }

    private static void refreshEntityTracking(ServerPlayer player) {
        ChunkMap chunkMap = player.level().getChunkSource().chunkMap;
        TrackedEntityAccessor tracked = (TrackedEntityAccessor) chunkMap.entityMap.get(player.getId());
        if (tracked == null) return;

        Set<ServerPlayerConnection> observers = Set.copyOf(tracked.getSeenBy());
        for (ServerPlayerConnection conn : observers) {
            ServerPlayer observer = conn.getPlayer();
            tracked.invokeRemovePlayer(observer);

            tracked.invokeUpdatePlayer(observer);
        }
    }
}
