package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 像素风主题：集中定义街机界面的配色与程序化绘制组件。
 *
 * <p>首版用纯程序化绘制（{@link GuiGraphics#fill}）呈现像素风面板与双层斜角边框，
 * <b>不依赖任何 PNG 资源</b>，避免贴图缺失导致渲染失败；后续可替换为真正的 nine-patch 贴图。
 */
public final class PixelTheme {

    /** 面板底色（半透明深墨绿）。 */
    public static final int PANEL_BG = 0xF0101511;
    /** 次级表面。 */
    public static final int SURFACE = 0xD81A211B;
    /** 悬停/选中表面。 */
    public static final int SURFACE_HOVER = 0xEE263222;
    /** 外边框（暗）。 */
    public static final int BORDER_DARK = 0xFF080D0A;
    /** 内斜角高光（亮军绿）。 */
    public static final int BEVEL_LIGHT = 0xFF6F8755;
    /** 内斜角阴影。 */
    public static final int BEVEL_SHADOW = 0xFF263024;
    /** 强调色（标题/选中）。 */
    public static final int ACCENT = 0xFFB7D76D;
    /** 队友/蓝队色。 */
    public static final int BLUE = 0xFF55A8FF;
    /** 敌方/红队色。 */
    public static final int RED = 0xFFFF6464;
    /** 成功色。 */
    public static final int SUCCESS = 0xFF79D87A;
    /** 警告色。 */
    public static final int WARNING = 0xFFE4C35A;
    /** 主文本。 */
    public static final int TEXT = 0xFFF0F2DE;
    /** 次文本。 */
    public static final int TEXT_DIM = 0xFF9FA78D;

    private PixelTheme() {
    }

    /**
     * 绘制一个像素风面板：外暗边 + 双层斜角 + 内填充。坐标为左上角与宽高。
     */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        // 阴影
        gg.fill(x + 3, y + 3, x2 + 3, y2 + 3, 0x65000000);
        // 外层暗边框（1px）
        gg.fill(x, y, x2, y2, BORDER_DARK);
        // 主体填充（内缩 1px）
        gg.fill(x + 1, y + 1, x2 - 1, y2 - 1, PANEL_BG);
        // 顶部轻微层次
        gg.fill(x + 2, y + 2, x2 - 2, y + 16, 0x22FFFFFF);
        // 上/左高光斜角（内缩 1px，2px 宽）
        gg.fill(x + 1, y + 1, x2 - 1, y + 2, BEVEL_LIGHT);
        gg.fill(x + 1, y + 1, x + 2, y2 - 1, BEVEL_LIGHT);
        // 下/右阴影斜角
        gg.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, BEVEL_SHADOW);
        gg.fill(x2 - 2, y + 1, x2 - 1, y2 - 1, BEVEL_SHADOW);
    }

    /** 绘制一条槽位行底纹。 */
    public static void row(GuiGraphics gg, int x, int y, int w, int h, boolean highlight) {
        int body = highlight ? (0x66101810 | (ACCENT & 0x00FFFFFF)) : SURFACE;
        gg.fill(x, y, x + w, y + h, body);
        gg.fill(x, y, x + w, y + 1, highlight ? ACCENT : BEVEL_SHADOW);
        gg.fill(x, y + h - 1, x + w, y + h, 0x55000000);
    }

    /** 绘制标题栏。 */
    public static void titleBar(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, 0xAA0A0F0C);
        gg.fill(x, y + h - 2, x + w, y + h, ACCENT);
        gg.fill(x + 2, y + 2, x + w - 2, y + 3, 0x33FFFFFF);
    }

    /** 绘制卡片块。 */
    public static void card(GuiGraphics gg, int x, int y, int w, int h, boolean highlight) {
        int body = highlight ? SURFACE_HOVER : SURFACE;
        gg.fill(x, y, x + w, y + h, 0xAA000000);
        gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        gg.fill(x + 1, y + 1, x + w - 1, y + 2, highlight ? ACCENT : BEVEL_LIGHT);
        gg.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BEVEL_SHADOW);
    }

    /** 绘制胶囊标签。 */
    public static void chip(GuiGraphics gg, int x, int y, int w, int h, int color) {
        gg.fill(x, y, x + w, y + h, 0x77000000);
        gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x22000000 | (color & 0x00FFFFFF));
        gg.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** 绘制简易进度条。 */
    public static void progress(GuiGraphics gg, int x, int y, int w, int h, float pct, int color) {
        gg.fill(x, y, x + w, y + h, 0x77000000);
        int fill = Math.max(0, Math.min(w, Math.round(w * pct)));
        if (fill > 0) {
            gg.fill(x, y, x + fill, y + h, color);
        }
        gg.fill(x, y, x + w, y + 1, 0x33FFFFFF);
    }
}
