package org.shee33.act0.arcade.arena;

import java.util.Objects;

/**
 * 出生/复活坐标点：维度 + 坐标 + 朝向。MC-free 纯数据，便于配置序列化与单测。
 *
 * <p>维度以资源路径字符串表示（如 {@code "minecraft:overworld"}），由 MC 层解析为实际 {@code ServerLevel}。
 */
public final class SpawnPoint {

    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public SpawnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public SpawnPoint(String dimension, double x, double y, double z) {
        this(dimension, x, y, z, 0f, 0f);
    }

    public String dimension() {
        return dimension;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    public String toString() {
        return "SpawnPoint{" + dimension + " " + x + "," + y + "," + z + " yaw=" + yaw + "}";
    }
}
