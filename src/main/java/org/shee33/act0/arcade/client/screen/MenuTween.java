package org.shee33.act0.arcade.client.screen;

/**
 * 补间引擎 —— 规格文档 §1.5 指定的五条缓动公式 + 脉冲函数 + 补间状态机，
 * 供「创建房间」界面（{@link CreateRoomScreen} / {@link CreateRoomAnimator}）使用。
 *
 * <p>与姊妹仓库 <b>ACT0-Battlefield</b> 的 {@code Tween} 同款 API 形态，但时间源切到
 * {@link System#currentTimeMillis()}：避免 {@code Util.getMillis()} 对 Minecraft classpath
 * 的依赖，让该引擎保持纯 Java 可单测（{@code MenuTweenTest} 直接跑 JUnit，不进 MC 环境）。
 * 时间基线与 {@link FlatTheme#fadeIn(long)} 一致。
 *
 * <p>Java 没有协程/Promise，所以不做 JS 那种 {@code tw()} 可 await 时间轴——
 * 调用方持有 {@link Anim} 实例，每帧调用 {@link Anim#rawT} / {@link Anim#easedT} 自行采样
 * 当前进度再绘制（参照第 6 节源码的 {@code requestAnimationFrame(s)} 循环）。
 */
final class MenuTween {

    private MenuTween() {
    }

    /** 真实时间源：{@link System#currentTimeMillis()}（与 {@code FlatTheme.fadeIn} 一致）。 */
    static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 规格文档 §1.5 的五条缓动公式，原样 port（JS 的 {@code Math.pow} 直接对应到 Java）。
     *
     * <p>{@code apply(t)} 会在内部把 {@code t} 钳制到 [0,1]——调用方传任何单调递增的进度值都安全，
     * 但建议调用方自管边界（{@link Anim#rawT} 已经钳过），本类只做兜底。
     */
    enum Ease {
        /** 线性（仅用于原样比例 / 不需要缓动的进度推进）。 */
        LINEAR,
        /** 入场默认：快出慢停（标题金线 350ms outExpo 等）。 */
        OUT_EXPO,
        /** 弹入：有过冲（按压回弹 220ms outBack）。 */
        OUT_BACK,
        /** 柔和减速（指示器滑动、数值滚轮、级联淡入等通用曲线）。 */
        OUT_CUBIC,
        /** 退场默认：慢起快走。 */
        IN_CUBIC;

        float apply(float tRaw) {
            float t = Math.max(0f, Math.min(1f, tRaw));
            return switch (this) {
                case LINEAR -> t;
                case OUT_EXPO -> t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
                case OUT_BACK -> {
                    float c = 1.70158f;
                    float d = c + 1.0f;
                    float x = t - 1.0f;
                    yield 1.0f + d * x * x * x + c * x * x;
                }
                case OUT_CUBIC -> 1.0f - (float) Math.pow(1.0f - t, 3.0);
                case IN_CUBIC -> t * t * t;
            };
        }
    }

    /**
     * 选中指示器在滑动途中叠加的纵向拉伸（规格文档 §4.1）：
     * {@code 1 + 0.25 * sin(v * π)}——起点 1，中途峰值 1.25，终点回到 1。
     *
     * <p>{@code v} 应取补间进度（{@link Anim#rawT(now)} 或 {@link Anim#easedT}），
     * 此函数本身不读时间戳，便于在 {@code render()} 里直接做缩放叠加。
     */
    static float pulse(float v) {
        return 1.0f + 0.25f * (float) Math.sin(v * Math.PI);
    }

    /**
     * 轻量"补间状态对象"：构造时给定 {@code duration}/{@code delay}，调用
     * {@link #start(long)} 标记起点，之后每帧用 {@link #rawT} / {@link #easedT} /
     * {@link #isDone} 轮询采样。
     *
     * <p>进度计算：
     * <pre>
     *   elapsed = now - startAt - delay
     *   rawT    = elapsed &lt;= 0 ? 0 : clamp(elapsed / duration, 0, 1)
     * </pre>
     * 调用方不必关心寿命/重置——{@link #start(long)} 可被反复调用以重启。
     */
    static final class Anim {
        private final long durationMs;
        private final long delayMs;
        private long startAtMs = -1L;

        Anim(long durationMs, long delayMs) {
            this.durationMs = Math.max(1L, durationMs);
            this.delayMs = Math.max(0L, delayMs);
        }

        /** 标记起点为 {@code nowMs}（毫秒）。可重复调用以重启动画。 */
        void start(long nowMs) {
            this.startAtMs = nowMs;
        }

        /** 是否已调用过 {@link #start(long)}。 */
        boolean isRunning() {
            return startAtMs >= 0L;
        }

        /**
         * 计入延迟后的原始线性进度 [0,1]：
         * <ul>
         *   <li>未启动（{@code startAtMs < 0}）→ 1（与 Battlefield {@code Tween.Anim} 一致，
         *       "默认就绪但没动过"的占位不阻塞调用方采样）</li>
         *   <li>尚在延迟中（{@code elapsed <= 0}）→ 0</li>
         *   <li>超过 duration → 1</li>
         * </ul>
         */
        float rawT(long nowMs) {
            if (startAtMs < 0L) {
                return 1f;
            }
            long elapsed = nowMs - startAtMs - delayMs;
            if (elapsed <= 0L) {
                return 0f;
            }
            return Math.max(0f, Math.min(1f, elapsed / (float) durationMs));
        }

        /** 套用指定 {@link Ease} 后的进度（{@link #rawT} + {@link Ease#apply}）。 */
        float easedT(long nowMs, Ease ease) {
            return ease.apply(rawT(nowMs));
        }

        /** 是否已播完（启动后 {@link #rawT} ≥ 1）。未启动时不算 done。 */
        boolean isDone(long nowMs) {
            return startAtMs >= 0L && rawT(nowMs) >= 1f;
        }
    }
}
