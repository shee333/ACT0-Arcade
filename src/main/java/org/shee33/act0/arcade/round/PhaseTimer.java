package org.shee33.act0.arcade.round;

/**
 * 阶段倒计时器：以游戏刻（tick，20/秒）为单位的简单倒计时，MC-free 可单测。
 *
 * <p>用于"开局倒计时""重新装备保护窗口""结算停留"等定时阶段。每个游戏刻调用一次 {@link #tick()}，
 * 当返回 {@code true} 表示本次 tick 后倒计时归零（到点），上层据此推进 {@link MatchPhase}。
 *
 * <p>非线程安全；由单个游戏循环线程驱动。
 */
public final class PhaseTimer {

    private int remainingTicks;

    /** 创建一个已停止（剩余 0）的计时器。 */
    public PhaseTimer() {
        this.remainingTicks = 0;
    }

    /** 以指定刻数重新开始倒计时。 */
    public void start(int ticks) {
        this.remainingTicks = Math.max(0, ticks);
    }

    /** 以秒为单位重新开始（按 20 tick/秒换算）。 */
    public void startSeconds(double seconds) {
        start((int) Math.round(seconds * 20.0));
    }

    /**
     * 推进一个游戏刻。
     *
     * @return 当且仅当本次 tick 使倒计时从 &gt;0 变为 0 时返回 {@code true}（到点边沿）。
     */
    public boolean tick() {
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        return remainingTicks == 0;
    }

    /** 是否仍在倒计时（剩余 &gt; 0）。 */
    public boolean isRunning() {
        return remainingTicks > 0;
    }

    /** 是否已到点（剩余 == 0）。 */
    public boolean isFinished() {
        return remainingTicks <= 0;
    }

    /** 剩余刻数。 */
    public int remainingTicks() {
        return remainingTicks;
    }

    /** 剩余整秒数（向上取整，用于 UI 显示）。 */
    public int remainingSeconds() {
        return (remainingTicks + 19) / 20;
    }
}
