package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * FlatTheme：战地 2042 风格的扁平化客户端 UI 主题，取代原墨绿像素风 {@code PixelTheme}。
 *
 * <p>与姊妹仓库 <b>ACT0-Battlefield</b>（{@code BattleResultScreen}、{@code BattlefieldHudOverlay}）
 * 共用同一套色值与动效参数，保证两个模组的军械/配装系统视觉语言一致：
 * <ul>
 *   <li>半透明深色面板背景（{@code #0A0A0A} 系，60-80% 不透明度）；</li>
 *   <li>纯色 + 透明度分层，不使用渐变；</li>
 *   <li>1px 细分割线/边框（{@code #3A3A3A}，30-40% 不透明度）；</li>
 *   <li>阵营色沿用蓝/红但降低饱和度（ALPHA {@code #5787C7} / BRAVO {@code #C75757}）；</li>
 *   <li>危险/警示色用橙（{@code #FF8C00}），不滥用纯红；</li>
 *   <li>尖角/无圆角——{@link GuiGraphics#fill} 本身不支持圆角绘制，因此形状天然保持硬边；</li>
 *   <li>动效以淡入淡出为主（150-300ms，三次方 ease-out），面板出现可配合 2-4px 上移，
 *       禁止弹跳/旋转/缩放弹跳/过度闪烁。</li>
 * </ul>
 *
 * <p>与 {@code PixelTheme} 一样，全部纯代码绘制（{@link GuiGraphics#fill}），<b>不依赖任何 PNG 贴图</b>。
 */
public final class FlatTheme {

    // ============================================================
    // 面板 / 背景 / 分割线
    // ============================================================

    /** 面板底色：{@code #0A0A0A} @ 80% 不透明度（与 ACT0-Battlefield BattleResultScreen 一致）。 */
    public static final int PANEL_BG = 0xCC0A0A0A;
    /** 面板底色（低）：{@code #0A0A0A} @ 60% 不透明度，用于 HUD 等更轻的叠加层。 */
    public static final int PANEL_BG_LOW = 0x990A0A0A;
    /** 全屏模态遮罩（弹层背后暗化整屏，使用与面板同色系而非纯黑）。 */
    public static final int MODAL_OVERLAY = 0xB30A0A0A;

    /** 行/卡片常态表面：中性深灰，低不透明度分层。 */
    public static final int SURFACE = 0x66101010;
    /** 行/卡片悬停或选中表面：冷灰蓝分层，不使用渐变。 */
    public static final int SURFACE_HOVER = 0x8C16232E;
    /** 禁用状态表面。 */
    public static final int SURFACE_DISABLED = 0x400A0A0A;

    /** 通用 1px 边框：{@code #3A3A3A} @ 40% 不透明度（与 ACT0-Battlefield 一致）。 */
    public static final int BORDER = 0x663A3A3A;
    /** 1px 分割线：{@code #3A3A3A} @ 30% 不透明度（与 ACT0-Battlefield 一致）。 */
    public static final int DIVIDER = 0x4D3A3A3A;

    // ============================================================
    // 阵营色 / 语义色
    // ============================================================

    /** ALPHA 阵营蓝（降饱和度）：{@code #5787C7}，与 ACT0-Battlefield 完全一致。 */
    public static final int ALPHA = 0xFF5787C7;
    /** BRAVO 阵营红（降饱和度）：{@code #C75757}，与 ACT0-Battlefield 完全一致。 */
    public static final int BRAVO = 0xFFC75757;
    /** 兼容旧 {@code PixelTheme.BLUE} 语义：蓝队/友方。 */
    public static final int BLUE = ALPHA;
    /** 兼容旧 {@code PixelTheme.RED} 语义：红队/敌方。 */
    public static final int RED = BRAVO;

    /** 通用强调色（选中态边框、标题、焦点指示）：沿用 ALPHA 冷蓝，取代旧的军绿 ACCENT。 */
    public static final int ACCENT = ALPHA;
    /** 成功/已解锁/已拥有：降饱和度绿，克制使用。 */
    public static final int SUCCESS = 0xFF6FBF6F;
    /** 危险/警示/价格高亮：橙色，不使用纯红（与 ACT0-Battlefield TEXT_AWARD 一致）。 */
    public static final int DANGER = 0xFFFF8C00;
    /** 兼容旧 {@code PixelTheme.WARNING} 语义，同 {@link #DANGER}。 */
    public static final int WARNING = DANGER;

    // ============================================================
    // 文本色
    // ============================================================

    /** 主文本：浅灰，避免纯白刺眼。 */
    public static final int TEXT = 0xFFE0E0E0;
    /** 次文本：中性灰。 */
    public static final int TEXT_DIM = 0xFF909090;
    /** 标题/最高优先级文本：纯白，克制使用。 */
    public static final int TEXT_HEADER = 0xFFFFFFFF;

    private FlatTheme() {
    }

    // ============================================================
    // 面板绘制
    // ============================================================

    /** 绘制一个扁平面板：纯色填充 + 1px 细边框，无斜角/无渐变。坐标为左上角与宽高。 */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h) {
        panel(gg, x, y, w, h, 1.0f);
    }

    /** 带透明度的扁平面板绘制，供淡入动效使用。 */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h, float alpha) {
        int x2 = x + w;
        int y2 = y + h;
        gg.fill(x, y, x2, y2, withAlpha(PANEL_BG, alpha));
        int border = withAlpha(BORDER, alpha);
        gg.fill(x, y, x2, y + 1, border);
        gg.fill(x, y2 - 1, x2, y2, border);
        gg.fill(x, y, x + 1, y2, border);
        gg.fill(x2 - 1, y, x2, y2, border);
    }

    /** 绘制标题栏：纯色底 + 底部 2px 强调线，取代旧的双层斜角标题栏。 */
    public static void titleBar(GuiGraphics gg, int x, int y, int w, int h) {
        titleBar(gg, x, y, w, h, ACCENT);
    }

    /** 绘制标题栏，可指定强调色（例如阵营色）。 */
    public static void titleBar(GuiGraphics gg, int x, int y, int w, int h, int accentColor) {
        gg.fill(x, y, x + w, y + h, PANEL_BG);
        gg.fill(x, y + h - 2, x + w, y + h, accentColor);
    }

    /** 绘制一条槽位行底纹：纯色表面 + 顶部 1px 强调/分割线，无斜角高光。 */
    public static void row(GuiGraphics gg, int x, int y, int w, int h, boolean highlight) {
        gg.fill(x, y, x + w, y + h, highlight ? SURFACE_HOVER : SURFACE);
        gg.fill(x, y, x + w, y + 1, highlight ? ACCENT : DIVIDER);
    }

    /** 绘制卡片块：纯色表面 + 1px 描边（选中/悬停时描边变为强调色），无斜角。 */
    public static void card(GuiGraphics gg, int x, int y, int w, int h, boolean highlight) {
        int x2 = x + w;
        int y2 = y + h;
        gg.fill(x, y, x2, y2, highlight ? SURFACE_HOVER : SURFACE);
        int border = highlight ? ACCENT : BORDER;
        gg.fill(x, y, x2, y + 1, border);
        gg.fill(x, y2 - 1, x2, y2, border);
        gg.fill(x, y, x + 1, y2, border);
        gg.fill(x2 - 1, y, x2, y2, border);
    }

    /** 绘制扁平标签/胶囊：低透明度色块 + 顶部 1px 实色强调线，无渐变。 */
    public static void chip(GuiGraphics gg, int x, int y, int w, int h, int color) {
        gg.fill(x, y, x + w, y + h, withAlpha(color, 0.22f));
        gg.fill(x, y, x + w, y + 1, color);
    }

    /** 绘制扁平进度条：深色底 + 纯色填充，末端平直不圆角。 */
    public static void progress(GuiGraphics gg, int x, int y, int w, int h, float pct, int color) {
        gg.fill(x, y, x + w, y + h, PANEL_BG);
        int fill = Math.max(0, Math.min(w, Math.round(w * pct)));
        if (fill > 0) {
            gg.fill(x, y, x + fill, y + h, color);
        }
    }

    /** 绘制一条水平 1px 分割线。 */
    public static void divider(GuiGraphics gg, int x, int y, int w) {
        gg.fill(x, y, x + w, y + 1, DIVIDER);
    }

    // ============================================================
    // 颜色 / 动效工具
    // ============================================================

    /** 将 ARGB 颜色的 alpha 通道按比例缩放（0-1），用于淡入淡出。 */
    public static int withAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 三次方 ease-out 缓动曲线，与 ACT0-Battlefield {@code BattlefieldHudOverlay#easeOut} 一致。 */
    public static float easeOut(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0);
    }

    /** 面板/弹层淡入状态：透明度 + 竖直偏移，供 {@code render()} 逐帧计算。 */
    public record Fade(float alpha, int offsetY) {
    }

    /** 默认 220ms、3px 上移的淡入曲线（面板出现的标准手感）。 */
    public static Fade fadeIn(long openedAtMs) {
        return fadeIn(openedAtMs, 220, 3);
    }

    /** 自定义时长（150-300ms 建议区间）与最大上移像素的淡入曲线。 */
    public static Fade fadeIn(long openedAtMs, int durationMs, int maxOffsetY) {
        long age = System.currentTimeMillis() - openedAtMs;
        float t = Math.max(0.0f, Math.min(1.0f, age / (float) durationMs));
        float eased = easeOut(t);
        int offset = Math.round(maxOffsetY * (1.0f - eased));
        return new Fade(eased, offset);
    }
}
