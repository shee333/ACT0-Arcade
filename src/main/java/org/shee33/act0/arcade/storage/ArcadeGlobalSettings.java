package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.saveddata.SavedData;
import org.shee33.act0.arcade.arena.HotZoneRotation;

/**
 * ACT0-Arcade 全局玩法设置，基于存档持久化。
 *
 * <p>包含全体玩家默认最大血量、街机呼吸回血延迟等需要重启后仍生效的设置。
 */
public final class ArcadeGlobalSettings extends SavedData {

    private static final String DATA_NAME = "act0_arcade_global_settings";
    private static final String KEY_HEALTH = "globalHealth";
    private static final String KEY_BREATH_DELAY = "breathHealDelayTicks";
    private static final String KEY_HOT_ZONE_DURATION = "hotZoneDurationTicks";

    public static final double DEFAULT_MAX_HEALTH = 20.0;
    public static final int DEFAULT_BREATH_HEAL_DELAY_TICKS = 5 * 20;
    public static final int DEFAULT_HOT_ZONE_DURATION_TICKS = 25 * 20;

    private double globalHealth = DEFAULT_MAX_HEALTH;
    private int breathHealDelayTicks = DEFAULT_BREATH_HEAL_DELAY_TICKS;
    private int hotZoneDurationTicks = DEFAULT_HOT_ZONE_DURATION_TICKS;

    public ArcadeGlobalSettings() {
    }

    public static ArcadeGlobalSettings get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadeGlobalSettings::load, ArcadeGlobalSettings::new, DATA_NAME);
    }

    public double maxHealth() {
        return globalHealth;
    }

    public void setMaxHealth(double health) {
        globalHealth = clampHealth(health);
        setDirty();
    }

    public int breathHealDelayTicks() {
        return Math.max(0, breathHealDelayTicks);
    }

    public void setBreathHealDelaySeconds(double seconds) {
        breathHealDelayTicks = Math.max(0, (int) Math.round(seconds * 20.0));
        setDirty();
    }

    /** 热区模式下单个热区的存在时长（tick），轮换到下一个热区的间隔。 */
    public int hotZoneDurationTicks() {
        return HotZoneRotation.clampDuration(hotZoneDurationTicks);
    }

    public void setHotZoneDurationSeconds(int seconds) {
        hotZoneDurationTicks = HotZoneRotation.clampDuration(seconds * 20);
        setDirty();
    }

    public void applyHealth(ServerPlayer player) {
        double max = maxHealth();
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
        }
        player.setHealth((float) max);
    }

    /** 立即把当前全局血量应用给所有在线玩家。 */
    public int applyHealthToAll(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyHealth(player);
            count++;
        }
        return count;
    }

    private static double clampHealth(double health) {
        return Math.max(1.0, Math.min(200.0, health));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putDouble(KEY_HEALTH, globalHealth);
        tag.putInt(KEY_BREATH_DELAY, breathHealDelayTicks);
        tag.putInt(KEY_HOT_ZONE_DURATION, hotZoneDurationTicks);
        return tag;
    }

    public static ArcadeGlobalSettings load(CompoundTag tag) {
        ArcadeGlobalSettings settings = new ArcadeGlobalSettings();
        if (tag.contains(KEY_HEALTH)) {
            settings.globalHealth = clampHealth(tag.getDouble(KEY_HEALTH));
        }
        if (tag.contains(KEY_BREATH_DELAY)) {
            settings.breathHealDelayTicks = Math.max(0, tag.getInt(KEY_BREATH_DELAY));
        }
        if (tag.contains(KEY_HOT_ZONE_DURATION)) {
            settings.hotZoneDurationTicks = HotZoneRotation.clampDuration(tag.getInt(KEY_HOT_ZONE_DURATION));
        }
        return settings;
    }
}
