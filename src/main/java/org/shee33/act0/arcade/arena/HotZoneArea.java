package org.shee33.act0.arcade.arena;

import java.util.Objects;

/**
 * 热区区域：矩形 AABB（XZ 平面矩形 + Y 轴范围），MC-free 纯数据，便于配置序列化与单测。
 *
 * <p>由管理员手持热区框选道具在场景中左键点击角 1、右键点击角 2 后自动生成（见
 * {@link #fromClicks}），归一化为 {@code min <= max} 的矩形——不要求管理员必须先点哪一个角。
 * 一张地图仅允许一个固定热区，不再像旧版"复用竞技场 {@code randomSpawns} 圆形点位 + 索引轮转"
 * 那样存在多个候选点或轮转的概念。
 *
 * <p>维度以资源路径字符串表示（如 {@code "minecraft:overworld"}），与 {@link SpawnPoint} 保持
 * 同样的表示方式，由 MC 层解析为实际 {@code ServerLevel}。
 */
public final class HotZoneArea {

    /**
     * 由两次点击构造时使用的 Y 轴垂直容差（格）。
     *
     * <p>两次点击记录的是"脚下方块"坐标，而判定用的是玩家实体的脚部坐标：实战中还会有跳跃、
     * 蹲下、站在半砖/台阶上等正常起伏。旧版圆形热区用固定的 {@code HOT_ZONE_HEIGHT=4.0} 覆盖
     * "玩家脚部与热区中心点的垂直落差"，这里延续同一量级，在两次点击 Y 坐标的最小/最大值基础上
     * 各向外扩 4 格，覆盖同样的"贴地站立即算在区域内"的场景。
     */
    public static final double VERTICAL_MARGIN = 4.0D;

    private final String dimension;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    /**
     * 直接以边界值构造；出于防御性考虑仍会做一次 min/max 归一化（调用方传入的两组坐标顺序
     * 不敏感），用于从 NBT/网络包解码等"边界值已经算好"的场景。
     */
    public HotZoneArea(String dimension, double minX, double maxX, double minY, double maxY,
                       double minZ, double maxZ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /**
     * 由管理员两次点击的方块坐标构造：XZ 两轴直接 min/max 归一化，Y 轴额外留 {@link #VERTICAL_MARGIN}
     * 垂直容差。角 1/角 2 谁大谁小、谁先点都无所谓。
     */
    public static HotZoneArea fromClicks(String dimension,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2) {
        double loY = Math.min(y1, y2) - VERTICAL_MARGIN;
        double hiY = Math.max(y1, y2) + VERTICAL_MARGIN;
        return new HotZoneArea(dimension, x1, x2, loY, hiY, z1, z2);
    }

    public String dimension() {
        return dimension;
    }

    public double minX() {
        return minX;
    }

    public double maxX() {
        return maxX;
    }

    public double minY() {
        return minY;
    }

    public double maxY() {
        return maxY;
    }

    public double minZ() {
        return minZ;
    }

    public double maxZ() {
        return maxZ;
    }

    /**
     * AABB 包含判定：先比对维度，再逐轴判断（边界取闭区间）。纯函数，供 {@code ArcadeMatch}
     * 直接调用，不必起服务器即可单测。
     */
    public boolean contains(String dimension, double x, double y, double z) {
        if (!this.dimension.equals(dimension)) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    @Override
    public String toString() {
        return "HotZoneArea{" + dimension
                + " x[" + minX + "," + maxX + "]"
                + " y[" + minY + "," + maxY + "]"
                + " z[" + minZ + "," + maxZ + "]}";
    }
}
