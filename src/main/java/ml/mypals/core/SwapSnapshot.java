package ml.mypals.core;

import com.mojang.authlib.GameProfile;
import ml.mypals.skin.PlayerSkinRefresher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.syncher.EntityDataSerializers;
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
    public final boolean noCulling;
    public final List<UUID> passengers;
    public final UUID vehicle;
    public final RemoteChatSession chatSession;
    public final ListTag attributes;
    public final Abilities abilities;
    public final ServerLevel level;
    public final double x, y, z;
    public final BlockPos isAsleep;
    public final float yRot, xRot;
    public final List<ItemStack> mainInventory;
    public final List<ItemStack> armorInventory;
    public final List<ItemStack> offhandInventory;
    public final List<ItemStack> enderChest;
    public final int selectedSlot;
    public final float health;
    public final int foodLevel;
    public final float saturationLevel;
    public final float exhaustionLevel;
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
        this.mainInventory = b.mainInventory;
        this.armorInventory = b.armorInventory;
        this.offhandInventory = b.offhandInventory;
        this.enderChest = b.enderChest;
        this.selectedSlot = b.selectedSlot;
        this.health = b.health;
        this.foodLevel = b.foodLevel;
        this.saturationLevel = b.saturationLevel;
        this.exhaustionLevel = b.exhaustionLevel;
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
        this.noCulling = b.noCulling;
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
        b.noCulling = player.noCulling;
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

        b.attributes = player.getAttributes().save();

        b.gameProfile = player.getGameProfile();
        b.level = player.serverLevel();
        b.x = player.getX();
        b.y = player.getY();
        b.z = player.getZ();
        b.yRot = player.getYRot();
        b.xRot = player.getXRot();

        b.mainInventory = copyItemsFrom(player.getInventory().items);
        b.armorInventory = copyItemsFrom(player.getInventory().armor);
        b.offhandInventory = copyItemsFrom(player.getInventory().offhand);
        b.selectedSlot = player.getInventory().selected;
        b.enderChest = new ArrayList<>();
        var ec = player.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) {
            b.enderChest.add(ec.getItem(i).copy());
        }

        b.health = player.getHealth();
        FoodData food = player.getFoodData();
        b.foodLevel = food.getFoodLevel();
        b.saturationLevel = food.getSaturationLevel();
        b.exhaustionLevel = food.getExhaustionLevel();
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
        if(player.isSleeping())b.isAsleep = player.getOnPos();

        List<SynchedEntityData.DataValue<?>> rawValues = player.getEntityData().getNonDefaultValues();
        if (rawValues != null) {
            b.entityData = rawValues.stream()
                    .filter(v2 -> v2.serializer() != EntityDataSerializers.OPTIONAL_UUID)
                    .toList();
        } else {
            b.entityData = List.of();
        }

        CompoundTag additionalTag = new CompoundTag();
        player.addAdditionalSaveData(additionalTag);
        b.additionalData = additionalTag;

        return new SwapSnapshot(b);
    }

    public void applyTo(ServerPlayer player, GameProfile profileToApply) {
        PlayerSkinRefresher.applyProfileAndRefresh(player, profileToApply);
        applyInventory(player);
        applyEnderChest(player);
        player.setHealth(health);
        FoodData food = player.getFoodData();
        food.setFoodLevel(foodLevel);
        food.setSaturation(saturationLevel);
        food.setExhaustion(exhaustionLevel);
        player.experienceLevel = experienceLevel;
        player.setExperienceLevels(experienceLevel);
        player.totalExperience = totalExperience;
        player.setGameMode(gameMode);
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.getInventory().selected = selectedSlot;
        player.teleportTo(level, x, y, z, yRot, xRot);

        player.getAttributes().load(attributes);

        if (!entityData.isEmpty()) {
            player.getEntityData().assignValues(entityData);
        }

        player.readAdditionalSaveData(additionalData.copy());

        applyRiding(player);
        if (isAsleep != null) {
            player.setSleepingPos(isAsleep);
            player.startSleepInBed(isAsleep);
        } else {
            player.clearSleepingPos();
            player.stopSleepInBed(true, true);
        }
        this.level.updateSleepingPlayerList();
        player.inventoryMenu.broadcastChanges();

        if (fallFlying) player.startFallFlying(); else player.stopFallFlying();

        player.wonGame = wonGame;

        for (Map.Entry<Stat<?>, Integer> entry : stats.stats.object2IntEntrySet()) {
            player.getStats().setValue(player, entry.getKey(), entry.getValue());
        }
        player.getStats().sendStats(player);
        player.getStats().markAllDirty();

        player.setMainArm(mainArm);
        player.noCulling = noCulling;

        if (chatSession != null) player.setChatSession(chatSession);

        Abilities dst = player.getAbilities();
        dst.invulnerable = abilities.invulnerable;
        dst.flying = abilities.flying;
        dst.mayfly = abilities.mayfly;
        dst.instabuild = abilities.instabuild;
        dst.setWalkingSpeed(abilities.getWalkingSpeed());
        dst.setFlyingSpeed(abilities.getFlyingSpeed());
        player.onUpdateAbilities();
    }

    private void applyInventory(ServerPlayer player) {
        copyItemsInto(player.getInventory().items, mainInventory);
        copyItemsInto(player.getInventory().armor, armorInventory);
        copyItemsInto(player.getInventory().offhand, offhandInventory);
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

    private static List<ItemStack> copyItemsFrom(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static void copyItemsInto(List<ItemStack> target, List<ItemStack> source) {
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < source.size() ? source.get(i).copy() : ItemStack.EMPTY);
        }
    }

    private static class Builder {
        BlockPos isAsleep;
        UUID vehicle;
        List<UUID> passengers;
        GameProfile gameProfile;
        boolean fallFlying;
        boolean wonGame;
        ServerStatsCounter stats;
        ListTag attributes;
        HumanoidArm mainArm;
        ServerGamePacketListenerImpl connection;
        boolean noCulling;
        RemoteChatSession chatSession;
        Abilities abilities;
        ServerLevel level;
        double x, y, z;
        float yRot, xRot;
        List<ItemStack> mainInventory, armorInventory, offhandInventory, enderChest;
        int selectedSlot;
        float health, saturationLevel, exhaustionLevel;
        int foodLevel;
        int experienceLevel;
        float experienceProgress;
        int totalExperience;
        GameType gameMode;
        List<MobEffectInstance> effects;
        List<SynchedEntityData.DataValue<?>> entityData;
        CompoundTag additionalData;
    }
}