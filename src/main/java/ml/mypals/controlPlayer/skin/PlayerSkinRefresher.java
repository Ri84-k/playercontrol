package ml.mypals.controlPlayer.skin;

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

    public static void applyProfileAndRefresh(ServerPlayer player, GameProfile newProfile) {
        GameProfile current = player.getGameProfile();
        PropertyMap newMap = new PropertyMap();
        for (var entry : current.getProperties().entries()) {
            if (!entry.getKey().equals(TEXTURES_KEY)) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        for (Property texProp : newProfile.getProperties().get(TEXTURES_KEY)) {
            newMap.put(TEXTURES_KEY, texProp);
        }

        GameProfile applied = new GameProfile(current.getId(), newProfile.getName());
        applied.getProperties().putAll(newMap);
        ((PlayerAccessor) player).setGameProfile(applied);

        PlayerList playerList = Objects.requireNonNull(player.getServer()).getPlayerList();
        playerList.broadcastAll(new ClientboundBundlePacket(List.of(
                new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())),
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(Collections.singleton(player))
        )));

        refreshEntityTracking(player);
        if (!player.isDeadOrDying()) {
            ServerLevel level = player.serverLevel();

            player.connection.send(new ClientboundBundlePacket(List.of(
                    new ClientboundRespawnPacket(
                            player.createCommonSpawnInfo(level),
                            ClientboundRespawnPacket.KEEP_ALL_DATA
                    ),
                    new ClientboundGameEventPacket(
                            ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START,
                            0
                    )
            )));

            // 重新同步位置（Respawn 包会重置客户端的坐标）
            player.connection.teleport(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );

            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            if (player.getVehicle() != null) {
                player.connection.send(new ClientboundSetPassengersPacket(player.getVehicle()));
            }
            if (!player.getPassengers().isEmpty()) {
                player.connection.send(new ClientboundSetPassengersPacket(player));
            }

            player.onUpdateAbilities();
            player.giveExperiencePoints(0); // 刷新经验条
            playerList.sendPlayerPermissionLevel(player);
            playerList.sendLevelInfo(player, level);
            playerList.sendAllPlayerInfo(player);

            // 重发药水效果
            for (var effect : player.getActiveEffects()) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(
                        player.getId(), effect, false
                ));
            }
        }
    }

    private static void refreshEntityTracking(ServerPlayer player) {
        ChunkMap chunkMap = player.serverLevel().getChunkSource().chunkMap;
        TrackedEntityAccessor tracked = (TrackedEntityAccessor) chunkMap.entityMap.get(player.getId());
        if (tracked == null) return;

        Set<ServerPlayerConnection> observers = Set.copyOf(tracked.getSeenBy());
        for (ServerPlayerConnection conn : observers) {
            ServerPlayer observer = conn.getPlayer();
            tracked.invokeRemovePlayer(observer);

            TrackedEntityAccessor observerTracked = (TrackedEntityAccessor)
                    chunkMap.entityMap.get(observer.getId());
            if (observerTracked != null) {
                observerTracked.invokeRemovePlayer(player);
                observerTracked.invokeUpdatePlayer(player);
            }
            tracked.invokeUpdatePlayer(observer);
        }
    }
}
