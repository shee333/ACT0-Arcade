package org.shee33.act0.arcade.client;

/**
 * 客户端死亡相机/滤镜状态缓存：由 {@code DeathCamPacket} 驱动，供 {@link DeathCamOverlay} 渲染。
 *
 * <p>{@code active} 为 {@code true} 时表示玩家正处于死亡观战等待，客户端绘制红色死亡滤镜与提示。
 * 实际相机位置由服务端把旁观者钉在死亡点实现（玩家无法自由飞行）。
 */
public final class ClientDeathCam {

    private static volatile boolean active = false;
    private static volatile long activatedAtMs = 0L;
    private static volatile String killerName = "";
    private static volatile boolean angleLocked = false;
    private static volatile float lockedYaw = 0f;
    private static volatile float lockedPitch = 0f;

    private ClientDeathCam() {
    }

    public static void setActive(boolean active, String killerName) {
        if (active && !ClientDeathCam.active) {
            activatedAtMs = System.currentTimeMillis();
        }
        ClientDeathCam.active = active;
        ClientDeathCam.killerName = killerName != null ? killerName : "";
        if (!active) {
            angleLocked = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void lockAnglesIfNeeded(float yaw, float pitch) {
        if (!active || angleLocked) {
            return;
        }
        lockedYaw = yaw;
        lockedPitch = pitch;
        angleLocked = true;
    }

    public static boolean hasLockedAngles() {
        return active && angleLocked;
    }

    public static float lockedYaw() {
        return lockedYaw;
    }

    public static float lockedPitch() {
        return lockedPitch;
    }

    /** 击杀者名（可能为空，表示自杀/环境死亡）。 */
    public static String killerName() {
        return killerName;
    }

    /** 死亡滤镜淡入进度 0~1（前 0.6 秒淡入）。 */
    public static float fadeIn() {
        if (!active) {
            return 0f;
        }
        float t = (System.currentTimeMillis() - activatedAtMs) / 600f;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }
}
