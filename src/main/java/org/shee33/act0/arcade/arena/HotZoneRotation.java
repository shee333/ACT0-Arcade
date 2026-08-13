package org.shee33.act0.arcade.arena;

/**
 * 热区轮换状态机：在一张地图预设的多个 {@link HotZoneArea} 之间按固定时长顺序切换，并在每次
 * 切换前留出一段预告窗口供客户端高亮"下一个热区"、服务端播报倒计时。MC-free 纯逻辑，可纯 JUnit
 * 单测，不依赖 {@code MinecraftServer} 的 tick 计数。
 *
 * <p>语义上"同一对局只允许有一个热区存在"——本类只维护一个 {@link #activeIndex()}，计分方只认
 * 这一个区；{@link #nextIndex()} 仅用于预告，不参与计分。轮换顺序是确定性的索引递增（与 CoD
 * Hardpoint 的固定轮换序列同一路子），不做随机：玩家能学会轮换路线本身就是战术深度的一部分，
 * 而随机会让"预告"退化成纯粹的通知。
 *
 * <p>只预设了一个热区时 {@link #rotates()} 为 {@code false}：全程固定、不计时、不预告，行为与
 * 引入轮换之前完全一致——这是老竞技场（存档里只有一个热区）的向后兼容路径。
 */
public final class HotZoneRotation {

    /** 切换前的预告窗口时长（tick）：5 秒。 */
    public static final int WARNING_TICKS = 5 * 20;

    /**
     * 单个热区的最短存在时长（tick）：6 秒。
     *
     * <p>必须严格大于 {@link #WARNING_TICKS}，否则预告窗口会覆盖热区的整个生命周期——玩家会看到
     * 一个"从出生就在倒计时迁移"的热区，倒计时因此失去意义。构造时无条件钳制，命令层的取值范围
     * 只是第一道防线。
     */
    public static final int MIN_DURATION_TICKS = 6 * 20;

    /** 单个热区的最长存在时长（tick）：300 秒。 */
    public static final int MAX_DURATION_TICKS = 300 * 20;

    /** {@link #tick()} 的结果：本 tick 是否有需要对外广播的事件发生。 */
    public enum TickResult {
        /** 无事发生，仅剩余时间递减。 */
        NONE,
        /** 本 tick 刚跨入预告窗口：此刻应把"下一个热区"下发给客户端并开始倒计时播报。 */
        WARNING_STARTED,
        /** 本 tick 完成了热区迁移：{@link #activeIndex()} 已更新，应重发激活区并清除预告。 */
        MIGRATED
    }

    private final int zoneCount;
    private final int durationTicks;
    private int activeIndex;
    private int remainingTicks;

    /**
     * @param zoneCount     该竞技场预设的热区数量；{@code <= 1} 时退化为"固定单区不轮换"
     * @param durationTicks 单个热区的存在时长，钳制到
     *                      {@code [MIN_DURATION_TICKS, MAX_DURATION_TICKS]}
     */
    public HotZoneRotation(int zoneCount, int durationTicks) {
        this.zoneCount = Math.max(0, zoneCount);
        this.durationTicks = clampDuration(durationTicks);
        this.activeIndex = 0;
        this.remainingTicks = this.durationTicks;
    }

    public static int clampDuration(int durationTicks) {
        return Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
    }

    public int zoneCount() {
        return zoneCount;
    }

    public int durationTicks() {
        return durationTicks;
    }

    /** 是否会发生轮换：预设了 2 个及以上热区才轮换。 */
    public boolean rotates() {
        return zoneCount > 1;
    }

    /** 当前唯一计分热区的索引。 */
    public int activeIndex() {
        return activeIndex;
    }

    /** 下一个即将激活的热区索引；不轮换时与 {@link #activeIndex()} 相同。 */
    public int nextIndex() {
        if (!rotates()) {
            return activeIndex;
        }
        return (activeIndex + 1) % zoneCount;
    }

    /** 当前热区的剩余存在时间（tick）。 */
    public int remainingTicks() {
        return remainingTicks;
    }

    /**
     * 剩余存在时间（秒，向上取整）：剩 1 tick 也显示 1 秒，避免倒计时在真正迁移之前就先跳到 0。
     */
    public int remainingSeconds() {
        return (remainingTicks + 19) / 20;
    }

    /** 当前是否处于迁移预告窗口内。 */
    public boolean warning() {
        return rotates() && remainingTicks <= WARNING_TICKS;
    }

    /** Boss 血条进度：剩余时间占单区总时长的比例，随时间流逝从 1 排空到 0。 */
    public float progress() {
        if (durationTicks <= 0) {
            return 0.0f;
        }
        float p = (float) remainingTicks / (float) durationTicks;
        return Math.max(0.0f, Math.min(1.0f, p));
    }

    /**
     * 推进一 tick：递减剩余时间，必要时完成迁移。
     *
     * <p>剩余时间归零的那一 tick 直接完成迁移并重置计时，因此 {@link #remainingTicks()} 对外
     * 永远不会被观察到 {@code 0}——省掉一个"已到期但还没切"的中间态。
     */
    public TickResult tick() {
        if (!rotates()) {
            return TickResult.NONE;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            activeIndex = nextIndex();
            remainingTicks = durationTicks;
            return TickResult.MIGRATED;
        }
        if (remainingTicks == WARNING_TICKS) {
            return TickResult.WARNING_STARTED;
        }
        return TickResult.NONE;
    }
}
