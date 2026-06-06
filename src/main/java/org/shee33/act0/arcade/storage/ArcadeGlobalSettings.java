package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACT0-Arcade 全局玩法设置，基于存档持久化。
 *
 * <p>包含玩家默认最大血量、街机呼吸回血延迟等需要重启后仍生效的设置。
 */
public final class ArcadeGlobalSettings extends SavedData {

    private static final String DATA_NAME = "act0_arcade_global_settings";
    private static final String KEY_PLAYER_HEALTH = "playerHealth";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_HEALTH = "health";
    private static final String KEY_BREATH_DELAY = "breathHealDelayTicks";

    public static final double DEFAULT_MAX_HEALTH = 20.0;
    public static final int DEFAULT_BREATH_HEAL_DELAY_TICKS = 5 * 20;

    private final Map<UUID, Double> playerHealth = new HashMap<>();
    private int breathHealDelayTicks = DEFAULT_BREATH_HEAL_DELAY_TICKS;

    public ArcadeGlobalSettings() {
    }

    public static ArcadeGlobalSettings get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadeGlobalSettings::load, ArcadeGlobalSettings::new, DATA_NAME);
    }

    public double maxHealth(UUID id) {
        return playerHealth.getOrDefault(id, DEFAULT_MAX_HEALTH);
    }

    public void setMaxHealth(UUID id, double health) {
        playerHealth.put(id, clampHealth(health));
        setDirty();
    }

    public int breathHealDelayTicks() {
        return Math.max(0, breathHealDelayTicks);
    }

    public void setBreathHealDelaySeconds(double seconds) {
        breathHealDelayTicks = Math.max(0, (int) Math.round(seconds * 20.0));
        setDirty();
    }

    public void applyHealth(ServerPlayer player) {
        double max = maxHealth(player.getUUID());
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
        }
        player.setHealth((float) max);
    }

    private static double clampHealth(double health) {
        return Math.max(1.0, Math.min(200.0, health));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(KEY_BREATH_DELAY, breathHealDelayTicks);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Double> e : playerHealth.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            entry.putDouble(KEY_HEALTH, e.getValue());
            list.add(entry);
        }
        tag.put(KEY_PLAYER_HEALTH, list);
        return tag;
    }

    public static ArcadeGlobalSettings load(CompoundTag tag) {
        ArcadeGlobalSettings settings = new ArcadeGlobalSettings();
        if (tag.contains(KEY_BREATH_DELAY)) {
            settings.breathHealDelayTicks = Math.max(0, tag.getInt(KEY_BREATH_DELAY));
        }
        ListTag list = tag.getList(KEY_PLAYER_HEALTH, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID(KEY_UUID)) {
                settings.playerHealth.put(entry.getUUID(KEY_UUID), clampHealth(entry.getDouble(KEY_HEALTH)));
            }
        }
        return settings;
    }
}
