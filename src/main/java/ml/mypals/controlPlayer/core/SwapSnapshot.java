package ml.mypals.controlPlayer.core;

import com.mojang.authlib.GameProfile;
import ml.mypals.controlPlayer.skin.PlayerSkinRefresher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.Set;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SwapSnapshot {

    public final GameProfile gameProfile;
    public final boolean fallFlying;
    public final boolean wonGame;
    public final ServerStatsCounter stats;
    public final HumanoidArm mainArm;
    public final ServerGamePacketListenerImpl connection;
    public final List<UUID> passengers;
    public final UUID vehicle;
    public final RemoteChatSession chatSession;
    public final List<AttributeInstance.Packed> attributes;
    public final Abilities abilities;
    public final ServerLevel level;
    public final double x, y, z;
    public final BlockPos isAsleep;
    public final float yRot, xRot;
    public final List<ItemStack> inventoryItems;
    public final List<ItemStack> enderChest;
    public final int selectedSlot;
    public final float health;
    public final CompoundTag foodData;
    public final int experienceLevel;
    public final float experienceProgress;
    public final int totalExperience;
    public final GameType gameMode;
    public final List<MobEffectInstance> effects;
    public final List<SynchedEntityData.DataValue<?>> entityData;
    public final CompoundTag additionalData;

    private SwapSnapshot(Builder b) {
        this.gameProfile = b.gameProfile;
        this.attributes = b.attributes;
        this.level = b.level;
        this.x = b.x;
        this.y = b.y;
        this.z = b.z;
        this.yRot = b.yRot;
        this.xRot = b.xRot;
        this.inventoryItems = b.inventoryItems;
        this.enderChest = b.enderChest;
        this.selectedSlot = b.selectedSlot;
        this.health = b.health;
        this.foodData = b.foodData;
        this.experienceLevel = b.experienceLevel;
        this.experienceProgress = b.experienceProgress;
        this.totalExperience = b.totalExperience;
        this.gameMode = b.gameMode;
        this.effects = b.effects;
        this.fallFlying = b.fallFlying;
        this.wonGame = b.wonGame;
        this.stats = b.stats;
        this.mainArm = b.mainArm;
        this.connection = b.connection;
        this.chatSession = b.chatSession;
        this.abilities = b.abilities;
        this.isAsleep = b.isAsleep;
        this.vehicle = b.vehicle;
        this.passengers = b.passengers;
        this.entityData = b.entityData;
        this.additionalData = b.additionalData;
    }

    public static SwapSnapshot capture(ServerPlayer player) {
        Builder b = new Builder();

        b.fallFlying = player.isFallFlying();
        b.wonGame = player.wonGame;
        b.stats = player.getStats();
        b.mainArm = player.getMainArm();
        b.connection = player.connection;
        b.chatSession = player.getChatSession();

        Abilities src = player.getAbilities();
        Abilities abCopy = new Abilities();
        abCopy.invulnerable = src.invulnerable;
        abCopy.flying = src.flying;
        abCopy.mayfly = src.mayfly;
        abCopy.instabuild = src.instabuild;
        abCopy.setWalkingSpeed(src.getWalkingSpeed());
        abCopy.setFlyingSpeed(src.getFlyingSpeed());
        b.abilities = abCopy;

        b.attributes = player.getAttributes().pack();

        b.gameProfile = player.getGameProfile();
        b.level = player.level();
        b.x = player.getX();
        b.y = player.getY();
        b.z = player.getZ();
        b.yRot = player.getYRot();
        b.xRot = player.getXRot();

        // Minecraft 26.2 inventory stores worn equipment outside its private main-item list.
        b.inventoryItems = copyInventory(player);
        b.selectedSlot = player.getInventory().getSelectedSlot();
        b.enderChest = new ArrayList<>();
        var ec = player.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) {
            b.enderChest.add(ec.getItem(i).copy());
        }

        b.health = player.getHealth();
        FoodData food = player.getFoodData();
        TagValueOutput foodOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        food.addAdditionalSaveData(foodOutput);
        b.foodData = foodOutput.buildResult();
        b.experienceLevel = player.experienceLevel;
        b.experienceProgress = player.experienceProgress;
        b.totalExperience = player.totalExperience;
        b.gameMode = player.gameMode.getGameModeForPlayer();
        b.effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            b.effects.add(new MobEffectInstance(effect));
        }

        Entity v = player.getVehicle();
        if (v != null) b.vehicle = v.getUUID();
        b.passengers = player.getPassengers().stream().map(Entity::getUUID).toList();
        if(player.isSleeping() && player.getSleepingPos().isPresent())b.isAsleep = player.getSleepingPos().get();

        List<SynchedEntityData.DataValue<?>> rawValues = player.getEntityData().getNonDefaultValues();
        if (rawValues != null) {
            b.entityData = List.copyOf(rawValues);
        } else {
            b.entityData = List.of();
        }

        TagValueOutput additionalOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.addAdditionalSaveData(additionalOutput);
        b.additionalData = additionalOutput.buildResult();

        return new SwapSnapshot(b);
    }

    public void applyTo(ServerPlayer player, GameProfile profileToApply) {
        String stage = "profile refresh";
        try {
        PlayerSkinRefresher.applyProfileAndRefresh(player, profileToApply);
        stage = "inventory restore";
        applyInventory(player);
        stage = "ender chest restore";
        applyEnderChest(player);
        stage = "health, food and experience restore";
        player.setHealth(health);
        FoodData food = player.getFoodData();
        food.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), foodData.copy()));
        player.experienceLevel = experienceLevel;
        player.setExperienceLevels(experienceLevel);
        player.totalExperience = totalExperience;
        stage = "game mode and potion effects restore";
        player.setGameMode(gameMode);
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        stage = "position and attribute restore";
        player.getInventory().setSelectedSlot(selectedSlot);
        player.teleportTo(level, x, y, z, Set.of(), yRot, xRot, true);

        player.getAttributes().apply(attributes);

        if (!entityData.isEmpty()) {
            player.getEntityData().assignValues(entityData);
        }

        stage = "additional player NBT restore";
        player.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), additionalData.copy()));

        stage = "vehicle and sleep restore";
        applyRiding(player);
        if (isAsleep != null) {
            player.startSleepInBed(isAsleep);
        } else {
            player.clearSleepingPos();
            player.stopSleepInBed(true, true);
        }
        this.level.updateSleepingPlayerList();
        player.inventoryMenu.broadcastChanges();

        if (fallFlying) player.startFallFlying(); else player.stopFallFlying();

        player.wonGame = wonGame;

        stage = "statistics restore";
        for (Map.Entry<Stat<?>, Integer> entry : stats.stats.object2IntEntrySet()) {
            player.getStats().setValue(player, entry.getKey(), entry.getValue());
        }
        player.getStats().sendStats(player);
        player.getStats().markAllDirty();

        stage = "player cosmetic and chat session restore";
        player.setMainArm(mainArm);

        if (chatSession != null) player.setChatSession(chatSession);

        stage = "player abilities restore";
        Abilities dst = player.getAbilities();
        dst.invulnerable = abilities.invulnerable;
        dst.flying = abilities.flying;
        dst.mayfly = abilities.mayfly;
        dst.instabuild = abilities.instabuild;
        dst.setWalkingSpeed(abilities.getWalkingSpeed());
        dst.setFlyingSpeed(abilities.getFlyingSpeed());
        player.onUpdateAbilities();
        } catch (RuntimeException exception) {
            ml.mypals.controlPlayer.PlayerControl.LOGGER.error(
                    "Failed restoring player snapshot at stage '{}' for player {}", stage, player.getUUID(), exception);
            throw exception;
        }
    }

    private void applyInventory(ServerPlayer player) {
        for (int slot = 0; slot < Math.min(player.getInventory().getContainerSize(), inventoryItems.size()); slot++) {
            player.getInventory().setItem(slot, inventoryItems.get(slot).copy());
        }
        player.inventoryMenu.broadcastChanges();
    }

    private void applyRiding(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (UUID uuid : passengers) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity != null) {
                entity.startRiding(player);
            }
        }
        if (vehicle != null) {
            Entity entity = serverLevel.getEntity(vehicle);
            if (entity != null) {
                player.startRiding(entity);
            }
        }
    }

    private void applyEnderChest(ServerPlayer player) {
        var ec = player.getEnderChestInventory();
        for (int i = 0; i < Math.min(ec.getContainerSize(), enderChest.size()); i++) {
            ec.setItem(i, enderChest.get(i).copy());
        }
    }

    private static List<ItemStack> copyInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.add(player.getInventory().getItem(slot).copy());
        }
        return snapshot;
    }

    private static class Builder {
        BlockPos isAsleep;
        UUID vehicle;
        List<UUID> passengers;
        GameProfile gameProfile;
        boolean fallFlying;
        boolean wonGame;
        ServerStatsCounter stats;
        List<AttributeInstance.Packed> attributes;
        HumanoidArm mainArm;
        ServerGamePacketListenerImpl connection;
        RemoteChatSession chatSession;
        Abilities abilities;
        ServerLevel level;
        double x, y, z;
        float yRot, xRot;
        List<ItemStack> inventoryItems, enderChest;
        int selectedSlot;
        float health;
        CompoundTag foodData;
        int experienceLevel;
        float experienceProgress;
        int totalExperience;
        GameType gameMode;
        List<MobEffectInstance> effects;
        List<SynchedEntityData.DataValue<?>> entityData;
        CompoundTag additionalData;
    }
}