package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * ACT0-Arcade 全局玩法设置，基于存档持久化。
 *
 * <p>包含全体玩家默认最大血量、街机呼吸回血延迟等需要重启后仍生效的设置。
 */
public final class ArcadeGlobalSettings extends SavedData {

    private static final String DATA_NAME = "act0_arcade_global_settings";
    private static final String KEY_HEALTH = "globalHealth";
    private static final String KEY_BREATH_DELAY = "breathHealDelayTicks";

    public static final double DEFAULT_MAX_HEALTH = 20.0;
    public static final int DEFAULT_BREATH_HEAL_DELAY_TICKS = 5 * 20;

    private double globalHealth = DEFAULT_MAX_HEALTH;
    private int breathHealDelayTicks = DEFAULT_BREATH_HEAL_DELAY_TICKS;

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
        return settings;
    }
}
