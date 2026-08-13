package org.shee33.act0.arcade.client;

import org.shee33.act0.arcade.arena.HotZoneArea;

/**
 * 客户端热区缓存：由 {@code HotZoneAreaPacket} 驱动写入，供 {@link ClientHotZoneOverlay} 的世界
 * 空间线框渲染直接读取。本类只负责"接收并持有最新一份数据"，不做任何渲染、不自己计时。
 *
 * <p>{@link #next()} 只在服务端处于迁移预告窗口内才非空，用于提前把即将启用的热区画出来。
 *
 * <p>{@link HotZoneArea} 是不可变纯数据，因此这里用 {@code volatile} 引用而不是一堆 {@code volatile
 * double} 字段：渲染线程读到的永远是一份内部自洽的边界，不会出现"minX 已更新、maxX 还是旧值"的
 * 撕裂——旧写法按字段逐个赋值时是有这个窗口的。
 */
public final class ClientHotZoneState {

    private static volatile HotZoneArea active;
    private static volatile HotZoneArea next;

    private ClientHotZoneState() {
    }

    public static void set(HotZoneArea active, HotZoneArea next) {
        ClientHotZoneState.active = active;
        ClientHotZoneState.next = next;
    }

    public static void clear() {
        active = null;
        next = null;
    }

    /** 当前生效的热区；{@code null} 表示当前不在热区模式对局中。 */
    public static HotZoneArea active() {
        return active;
    }

    /** 即将启用的热区；仅在迁移预告窗口内非空。 */
    public static HotZoneArea next() {
        return next;
    }

    public static boolean present() {
        return active != null;
    }
}
