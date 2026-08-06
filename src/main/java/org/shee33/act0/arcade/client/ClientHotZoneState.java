package org.shee33.act0.arcade.client;

/**
 * 客户端热区矩形边界缓存：由 {@code HotZoneAreaPacket} 驱动写入，供后续的热区边界可视化任务
 * 直接读取。本类只负责"接收并持有最新一份边界数据"，不做任何渲染——渲染是另一个独立任务。
 */
public final class ClientHotZoneState {

    private static volatile boolean present = false;
    private static volatile String dimension = "";
    private static volatile double minX;
    private static volatile double maxX;
    private static volatile double minY;
    private static volatile double maxY;
    private static volatile double minZ;
    private static volatile double maxZ;

    private ClientHotZoneState() {
    }

    public static void set(String dimension, double minX, double maxX, double minY, double maxY,
                           double minZ, double maxZ) {
        ClientHotZoneState.dimension = dimension != null ? dimension : "";
        ClientHotZoneState.minX = minX;
        ClientHotZoneState.maxX = maxX;
        ClientHotZoneState.minY = minY;
        ClientHotZoneState.maxY = maxY;
        ClientHotZoneState.minZ = minZ;
        ClientHotZoneState.maxZ = maxZ;
        ClientHotZoneState.present = true;
    }

    public static void clear() {
        present = false;
    }

    public static boolean present() {
        return present;
    }

    public static String dimension() {
        return dimension;
    }

    public static double minX() {
        return minX;
    }

    public static double maxX() {
        return maxX;
    }

    public static double minY() {
        return minY;
    }

    public static double maxY() {
        return maxY;
    }

    public static double minZ() {
        return minZ;
    }

    public static double maxZ() {
        return maxZ;
    }
}
