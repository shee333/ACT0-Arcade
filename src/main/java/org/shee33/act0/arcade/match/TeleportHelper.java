package org.shee33.act0.arcade.match;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.shee33.act0.arcade.arena.SpawnPoint;

/**
 * 传送辅助：把 MC-free 的 {@link SpawnPoint} 解析为真实 {@code ServerLevel} 并传送玩家。
 *
 * <p>是竞技场坐标数据与 Minecraft 世界之间的唯一桥接点，集中处理维度解析与跨维度传送。
 */
public final class TeleportHelper {

    private TeleportHelper() {
    }

    /**
     * 将玩家传送到指定出生点（支持跨维度）。
     *
     * @return 是否成功（维度无法解析时返回 {@code false}）。
     */
    public static boolean teleport(ServerPlayer player, SpawnPoint spawn) {
        if (spawn == null) {
            return false;
        }
        ServerLevel level = resolveLevel(player.server, spawn.dimension());
        if (level == null) {
            return false;
        }
        player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        return true;
    }

    /** 把维度资源路径字符串解析为 {@code ServerLevel}，失败返回 {@code null}。 */
    public static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return server.getLevel(key);
    }
}
