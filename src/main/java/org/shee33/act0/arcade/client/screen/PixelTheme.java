package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 像素风主题：集中定义街机界面的配色与一个程序化绘制的"九宫格"面板。
 *
 * <p>首版用纯程序化绘制（{@link GuiGraphics#fill}）呈现像素风面板与双层斜角边框，
 * <b>不依赖任何 PNG 资源</b>，避免贴图缺失导致渲染失败；后续可替换为真正的 nine-patch 贴图。
 */
public final class PixelTheme {

    /** 面板底色（半透明深墨绿）。 */
    public static final int PANEL_BG = 0xF00E120C;
    /** 外边框（暗）。 */
    public static final int BORDER_DARK = 0xFF1C241A;
    /** 内斜角高光（亮军绿）。 */
    public static final int BEVEL_LIGHT = 0xFF5A6B46;
    /** 内斜角阴影。 */
    public static final int BEVEL_SHADOW = 0xFF323C28;
    /** 强调色（标题/选中）。 */
    public static final int ACCENT = 0xFF9DBF63;
    /** 主文本。 */
    public static final int TEXT = 0xFFE6E6CF;
    /** 次文本。 */
    public static final int TEXT_DIM = 0xFF8A8F78;

    private PixelTheme() {
    }

    /**
     * 绘制一个像素风面板：外暗边 + 双层斜角 + 内填充。坐标为左上角与宽高。
     */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        // 外层暗边框（1px）
        gg.fill(x, y, x2, y2, BORDER_DARK);
        // 主体填充（内缩 1px）
        gg.fill(x + 1, y + 1, x2 - 1, y2 - 1, PANEL_BG);
        // 上/左高光斜角（内缩 1px，2px 宽）
        gg.fill(x + 1, y + 1, x2 - 1, y + 2, BEVEL_LIGHT);
        gg.fill(x + 1, y + 1, x + 2, y2 - 1, BEVEL_LIGHT);
        // 下/右阴影斜角
        gg.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, BEVEL_SHADOW);
        gg.fill(x2 - 2, y + 1, x2 - 1, y2 - 1, BEVEL_SHADOW);
    }

    /** 绘制一条槽位行底纹。 */
    public static void row(GuiGraphics gg, int x, int y, int w, int h, boolean highlight) {
        gg.fill(x, y, x + w, y + h, highlight ? (0x55000000 | (ACCENT & 0xFFFFFF)) : 0x40101810);
        gg.fill(x, y, x + w, y + 1, BEVEL_SHADOW);
    }
}
