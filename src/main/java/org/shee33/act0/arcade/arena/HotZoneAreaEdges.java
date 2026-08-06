package org.shee33.act0.arcade.arena;

/**
 * 热区矩形 AABB 的 12 条棱端点坐标计算：MC-free 纯几何工具，从 {@link HotZoneArea} 拆出来，
 * 专供世界渲染层（选区高亮线框）复用——只要区域数据没变就不必每帧重新拼一遍 8 个顶点的组合。
 *
 * <p>返回顺序固定为「4 条底边（落地轮廓，Y=minY）→ 4 条顶边（Y=maxY）→ 4 根立柱（连接上下）」，
 * 与 {@link #FLOOR_EDGE_COUNT} 常量对应，供渲染层区分"落地轮廓"与"次要边界"分别上色
 * （纯色 + 透明度分层，不引入渐变）。每条棱是一个长度为 6 的数组：{@code {x1,y1,z1,x2,y2,z2}}。
 */
public final class HotZoneAreaEdges {

    /** 一个 AABB 有 12 条棱。 */
    public static final int EDGE_COUNT = 12;

    /** 返回数组中前 {@value} 条是"底边"（落地轮廓）。 */
    public static final int FLOOR_EDGE_COUNT = 4;

    private HotZoneAreaEdges() {
    }

    /** 便捷重载：直接从 {@link HotZoneArea} 取六个边界值。 */
    public static double[][] edgesOf(HotZoneArea area) {
        return edgesOf(area.minX(), area.maxX(), area.minY(), area.maxY(), area.minZ(), area.maxZ());
    }

    /**
     * 计算 AABB 的 12 条棱端点坐标。调用方不必保证 {@code min <= max}——{@link HotZoneArea}
     * 自身已经归一化过，这里不重复做防御性排序，直接按传入值组装几何体。
     */
    public static double[][] edgesOf(double minX, double maxX, double minY, double maxY,
                                     double minZ, double maxZ) {
        return new double[][]{
                // 4 条底边（Y = minY，落地轮廓）
                {minX, minY, minZ, maxX, minY, minZ},
                {maxX, minY, minZ, maxX, minY, maxZ},
                {maxX, minY, maxZ, minX, minY, maxZ},
                {minX, minY, maxZ, minX, minY, minZ},
                // 4 条顶边（Y = maxY）
                {minX, maxY, minZ, maxX, maxY, minZ},
                {maxX, maxY, minZ, maxX, maxY, maxZ},
                {maxX, maxY, maxZ, minX, maxY, maxZ},
                {minX, maxY, maxZ, minX, maxY, minZ},
                // 4 根立柱（连接上下四角）
                {minX, minY, minZ, minX, maxY, minZ},
                {maxX, minY, minZ, maxX, maxY, minZ},
                {maxX, minY, maxZ, maxX, maxY, maxZ},
                {minX, minY, maxZ, minX, maxY, maxZ},
        };
    }
}
