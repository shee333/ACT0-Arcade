package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * MenuChrome —— 菜单层（弹层/设置界面）金色强调视觉语言的共享工具类。
 *
 * <p>从 {@link CreateRoomScreen} 中抽取而来：金色强调色板 + 悬停/禁用透明度分层常量 +
 * 由 {@link MenuTween} 驱动的通用绘制原语（缩放包裹、按压回弹、幽灵按钮、灯态控件、
 * 主按钮、扫光、纵向数字滚轮、横向滑动）。与 {@link FlatTheme} 是互补关系而非替代——
 * FlatTheme 服务于 HUD 层的冷灰蓝阵营配色，MenuChrome 服务于菜单层的金色强调语言，
 * 二者会在同一界面内同时使用（例如 FlatTheme.panel 做面板外壳 + MenuChrome.drawPrimaryButton
 * 做 CTA 按钮）。
 *
 * <p>纯工具类，不持有状态，全部 {@code static}，与 {@link FlatTheme} 同款风格：
 * {@code private} 无参构造禁止实例化。
 */
public final class MenuChrome {

    private MenuChrome() {
    }

    // ============================================================
    // 配色
    // ============================================================

    public static final int GOLD = 0xFFFFD76A;
    public static final int GOLD_TEXT = 0xFF14181D;
    public static final int GREEN = 0xFF7EE2A8;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT_BASE = 0xFFE8EDF2;
    /** 锁定态遮罩：{@code #101010} @ ~69% 不透明度，供未解锁装备/皮肤等锁定叠层使用。 */
    public static final int LOCK_OVERLAY = 0xB0101010;

    // ============================================================
    // 幽灵按钮（drawGhostButton）透明度分层
    // ============================================================

    public static final float GHOST_BORDER_ALPHA_HOVER = 0.40f;
    public static final float GHOST_BORDER_ALPHA_IDLE = 0.15f;
    public static final float GHOST_BORDER_ALPHA_DISABLED = 0.08f;
    public static final float GHOST_TEXT_ALPHA_IDLE = 0.70f;
    public static final float GHOST_TEXT_ALPHA_DISABLED = 0.30f;

    // ============================================================
    // 灯态控件（drawLightControl，原 drawArrow）透明度分层
    // ============================================================

    public static final float LIGHT_BG_ALPHA_HOVER = 0.12f;
    public static final float LIGHT_BG_ALPHA_IDLE = 0.05f;
    public static final float LIGHT_BG_ALPHA_DISABLED = 0.03f;
    public static final float LIGHT_TEXT_ALPHA_IDLE = 0.70f;
    public static final float LIGHT_TEXT_ALPHA_DISABLED = 0.25f;

    // ============================================================
    // 主按钮（drawPrimaryButton）透明度分层
    // ============================================================

    public static final float PRIMARY_DISABLED_ALPHA = 0.35f;
    public static final float PRIMARY_HOVER_HIGHLIGHT_ALPHA = 0.50f;
    public static final float PRIMARY_SWEEP_MAX_ALPHA = 0.50f;

    // ============================================================
    // 纯逻辑辅助方法（可单测，与 CreateRoomAnimator 同名方法逻辑完全一致的独立副本——
    // 两处均是与业务无关的一行数学，故意不做代理统一，详见类头注释）
    // ============================================================

    /** 半开矩形命中检测：{@code x <= mx < x+w} 且 {@code y <= my < y+h}。 */
    public static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * 颜色线性插值。<b>刻意保留</b>既有行为：无论输入两色的 alpha 通道为何，
     * 输出的 alpha 通道总是被强制为完全不透明 {@code 0xFF}——这是从
     * {@code CreateRoomScreen} 原样搬迁过来的既有行为，不在此处“修正”。
     */
    public static int lerpColor(int c1, int c2, float t) {
        float tc = clamp01(t);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * tc);
        int g = Math.round(g1 + (g2 - g1) * tc);
        int b = Math.round(b1 + (b2 - b1) * tc);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 把任意浮点钳制到 [0,1]。 */
    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** 在钳制 {@code t}∈[0,1] 的前提下做线性插值 {@code a + (b-a)*t}。 */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /** 循环容器索引推进：{@code (current + dir) mod size}，负值安全。 */
    public static int wrapIndex(int current, int dir, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(current + dir, size);
    }

    /** 滚动方向：目标索引更大 → {@code +1}，更小 → {@code -1}，相同 → {@code 0}。 */
    public static int scrollDir(int from, int to) {
        return Integer.compare(to, from);
    }

    /**
     * 区块收展进度：{@code visible=true} 时随 {@code eased} 从 0 展开到 1，
     * {@code visible=false} 时从 1 收起到 0。
     */
    public static float expandProgress(boolean visible, float eased) {
        float t = clamp01(eased);
        return visible ? t : 1f - t;
    }

    /**
     * 列表项悬停态的目标 padding / 文字透明度：{@code entering=true}（鼠标进入）走
     * 6→10px / 0.55→0.9，{@code entering=false}（鼠标离开）走对称反向 10→6px / 0.9→0.55。
     */
    public record HoverState(float padding, float textAlpha) {
    }

    public static HoverState hoverState(boolean entering, float eased) {
        float t = clamp01(eased);
        return entering
                ? new HoverState(lerp(6f, 10f, t), lerp(0.55f, 0.9f, t))
                : new HoverState(lerp(10f, 6f, t), lerp(0.9f, 0.55f, t));
    }

    /**
     * 数值滚轮 / 竞技场滑动的位移量：Y/X 共用同一公式，仅 {@code clipSize} 不同。
     *
     * @param dir      方向：+1（新值自下/自右而来）、-1（反向）
     * @param e        补间进度 [0,1]（缓动已应用）
     * @param clipSize 振幅，应等于裁剪区尺寸，确保 e=1 时旧文案已完全移出裁剪区
     * @param old      {@code true} 计算“旧文案（滑出）”的偏移，{@code false} 计算“新文案（滑入）”的偏移
     */
    public static float rollOffset(int dir, float e, int clipSize, boolean old) {
        float t = clamp01(e);
        return old ? -dir * clipSize * t : dir * clipSize * (1f - t);
    }

    // ============================================================
    // 缩放包裹（drawScaled / drawScaledAxis）
    // ============================================================

    /** 以 {@code (cx,cy)} 为锚点做等比缩放后执行 {@code draw}，缩放完成后自动还原姿态。 */
    public static void drawScaled(GuiGraphics gg, int cx, int cy, float scale, Runnable draw) {
        drawScaledAxis(gg, cx, cy, scale, scale, draw);
    }

    /** 以 {@code (cx,cy)} 为锚点做双轴缩放后执行 {@code draw}；接近 1 时跳过矩阵变换（性能&精度）。 */
    public static void drawScaledAxis(GuiGraphics gg, int cx, int cy, float sx, float sy, Runnable draw) {
        if (Math.abs(sx - 1f) < 0.001f && Math.abs(sy - 1f) < 0.001f) {
            draw.run();
            return;
        }
        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().scale(sx, sy, 1f);
        gg.pose().translate(-cx, -cy, 0);
        draw.run();
        gg.pose().popPose();
    }

    // ============================================================
    // 按压回弹
    // ============================================================

    /**
     * 按压回弹缩放：{@code scale = 0.82 + 0.18 * OUT_BACK(progress)}；从未启动过的 {@link MenuTween.Anim}
     * 恒返回 {@code 1.0}（未按压过，无回弹）。
     */
    public static float pressScale(MenuTween.Anim pressAnim, long now) {
        if (!pressAnim.isRunning()) {
            return 1f;
        }
        return 0.82f + 0.18f * pressAnim.easedT(now, MenuTween.Ease.OUT_BACK);
    }

    // ============================================================
    // 通用绘制原语
    // ============================================================

    /** 幽灵按钮：细边框 + 居中文字，悬停/禁用态走透明度分层，无填充底色。 */
    public static void drawGhostButton(GuiGraphics gg, Font font, int x, int y, int w, int h, String label,
                                        boolean hovered, boolean enabled, float alpha) {
        float borderA = (enabled ? (hovered ? GHOST_BORDER_ALPHA_HOVER : GHOST_BORDER_ALPHA_IDLE)
                : GHOST_BORDER_ALPHA_DISABLED) * alpha;
        int border = withAlpha(WHITE, borderA);
        gg.fill(x, y, x + w, y + 1, border);
        gg.fill(x, y + h - 1, x + w, y + h, border);
        gg.fill(x, y, x + 1, y + h, border);
        gg.fill(x + w - 1, y, x + w, y + h, border);
        int textColor = enabled
                ? (hovered ? withAlpha(WHITE, alpha) : withAlpha(TEXT_BASE, GHOST_TEXT_ALPHA_IDLE * alpha))
                : withAlpha(TEXT_BASE, GHOST_TEXT_ALPHA_DISABLED * alpha);
        gg.drawCenteredString(font, label, x + w / 2, y + 5, textColor);
    }

    /**
     * 灯态控件（数值调整箭头 / +- 按钮）：低透明度底色块 + 居中字形，内部自带按压缩放包裹
     * （{@code scale} 由调用方通过 {@link #pressScale(MenuTween.Anim, long)} 预先算好传入）。
     */
    public static void drawLightControl(GuiGraphics gg, Font font, int x, int y, int w, int h, String glyph,
                                         boolean hovered, boolean enabled, float scale, float alpha) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        drawScaled(gg, cx, cy, scale, () -> {
            int bg = withAlpha(WHITE, (enabled ? (hovered ? LIGHT_BG_ALPHA_HOVER : LIGHT_BG_ALPHA_IDLE)
                    : LIGHT_BG_ALPHA_DISABLED) * alpha);
            gg.fill(x, y, x + w, y + h, bg);
            int col = enabled
                    ? (hovered ? withAlpha(WHITE, alpha) : withAlpha(TEXT_BASE, LIGHT_TEXT_ALPHA_IDLE * alpha))
                    : withAlpha(TEXT_BASE, LIGHT_TEXT_ALPHA_DISABLED * alpha);
            gg.drawCenteredString(font, glyph, x + w / 2, y + 5, col);
        });
    }

    /**
     * 主按钮（创建/确认类 CTA）纯绘制部分：金→绿颜色过渡 + 悬停高光 + 扫光 + 内置按压缩放包裹。
     * 调用方（{@code CreateRoomScreen}）负责用状态机算出 {@code confirmed}/{@code colorShiftE}/
     * {@code sweepE}/{@code pressScale} 等输入并驱动状态转换（例如启动 {@code createRevert}），
     * 本方法本身不持有、不修改任何状态。
     */
    public static void drawPrimaryButton(GuiGraphics gg, Font font, int x, int y, int w, int h, String label,
                                          boolean hovered, boolean enabled, boolean confirmed,
                                          float colorShiftE, float sweepE, float pressScale) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        drawScaled(gg, cx, cy, pressScale, () -> {
            float colorT = confirmed ? 1f : colorShiftE;
            int bg = enabled ? lerpColor(GOLD, GREEN, colorT) : withAlpha(GOLD, PRIMARY_DISABLED_ALPHA);
            gg.fill(x, y, x + w, y + h, bg);
            if (hovered) {
                gg.fill(x, y, x + w, y + 1, withAlpha(WHITE, PRIMARY_HOVER_HIGHLIGHT_ALPHA));
            }
            gg.drawCenteredString(font, label, x + w / 2, y + 5, GOLD_TEXT);
            drawSweep(gg, x, y, w, h, sweepE, PRIMARY_SWEEP_MAX_ALPHA);
        });
    }

    /** 扫光条：中心位置随 {@code e} 从 -40% 扫到 140%，{@code e<=0 或 e>=1} 时静默不绘制。 */
    public static void drawSweep(GuiGraphics gg, int x, int y, int w, int h, float e, float maxAlpha) {
        if (e <= 0f || e >= 1f) {
            return;
        }
        float centerFrac = -0.4f + 1.8f * e;
        int barCenter = x + Math.round(centerFrac * w);
        int half = Math.max(1, Math.round(w * 0.15f));
        int bx1 = Math.max(x, barCenter - half);
        int bx2 = Math.min(x + w, barCenter + half);
        if (bx2 > bx1) {
            gg.fill(bx1, y, bx2, y + h, withAlpha(WHITE, maxAlpha));
        }
    }

    /**
     * 纵向数字滚轮：旧值滑出 + 新值滑入（{@code dir=1} 新值自下而上）。
     * {@code travelPx} 应等于裁剪区高度，确保 e=1 时旧文案已完全移出裁剪区，不会有一帧跳变。
     */
    public static void drawRollY(GuiGraphics gg, Font font, int cx, int cy, String oldText, String newText, int dir,
                                  MenuTween.Anim rollAnim, long now, int color, int travelPx) {
        float e = rollAnim.easedT(now, MenuTween.Ease.OUT_CUBIC);
        if (e < 1f && oldText != null && !oldText.isEmpty()) {
            int oy = cy + Math.round(rollOffset(dir, e, travelPx, true));
            gg.drawCenteredString(font, oldText, cx, oy - 4, color);
        }
        int ny = cy + Math.round(rollOffset(dir, e, travelPx, false));
        gg.drawCenteredString(font, newText, cx, ny - 4, color);
    }

    /**
     * 横向滑动（竞技场，{@code dir=1} ▶ 新值自右滑入）。{@code travelPx} 应等于裁剪区宽度，
     * 同样确保完全滑出裁剪区。
     */
    public static void drawSlideX(GuiGraphics gg, Font font, int cx, int cy, String oldText, String newText, int dir,
                                   MenuTween.Anim slideAnim, long now, int color, int travelPx) {
        float e = slideAnim.easedT(now, MenuTween.Ease.OUT_CUBIC);
        if (e < 1f && oldText != null && !oldText.isEmpty()) {
            int ox = cx + Math.round(rollOffset(dir, e, travelPx, true));
            gg.drawCenteredString(font, oldText, ox, cy - 4, color);
        }
        int nx = cx + Math.round(rollOffset(dir, e, travelPx, false));
        gg.drawCenteredString(font, newText, nx, cy - 4, color);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private static int withAlpha(int color, float alpha) {
        return FlatTheme.withAlpha(color, alpha);
    }
}
