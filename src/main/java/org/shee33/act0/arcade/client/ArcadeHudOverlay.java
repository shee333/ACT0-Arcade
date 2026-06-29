package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.client.screen.PixelTheme;

import java.util.List;

/**
 * 客户端对局 HUD：在屏幕右侧自绘侧边栏。
 *
 * <p>替代原版计分板侧边栏，<b>不渲染右侧红色分值（序号）</b>。内容来自 {@link ClientSidebar}，
 * 由服务端经 {@code SyncSidebarPacket} 下发。位置与原版侧边栏一致（右边缘、垂直居中）。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeHudOverlay {

    private static final int PAD_X = 8;
    private static final int LINE_H = 12;
    private static final int HEADER_H = 17;

    private ArcadeHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientSidebar.isShown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        List<String> lines = ClientSidebar.lines();
        String title = ClientSidebar.title();
        if (lines.isEmpty() && title.isEmpty()) {
            return;
        }

        GuiGraphics gg = event.getGuiGraphics();
        Font font = mc.font;

        int contentW = font.width(title);
        for (String line : lines) {
            contentW = Math.max(contentW, font.width(line));
        }
        int panelW = Math.max(118, contentW + PAD_X * 2 + 10);
        int screenW = gg.guiWidth();
        int screenH = gg.guiHeight();

        int totalH = HEADER_H + lines.size() * LINE_H + 6;
        int top = screenH / 2 - totalH / 2;
        int right = screenW - 6;
        int left = right - panelW;

        // 主面板：阴影 + 边框 + 标题栏
        gg.fill(left + 3, top + 3, right + 3, top + totalH + 3, 0x66000000);
        gg.fill(left, top, right, top + totalH, 0xE40B100D);
        gg.fill(left, top, left + 3, top + totalH, PixelTheme.ACCENT);
        gg.fill(left + 1, top + 1, right - 1, top + 2, 0x44FFFFFF);
        PixelTheme.titleBar(gg, left, top, panelW, HEADER_H);

        // 标题：左侧短线 + 右对齐文本
        gg.fill(left + 8, top + 6, left + 18, top + 8, PixelTheme.ACCENT);
        gg.drawString(font, title, right - PAD_X - font.width(title), top + 5, PixelTheme.TEXT, false);

        int y = top + HEADER_H + 3;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int rowColor = (i & 1) == 0 ? 0x381A211B : 0x221A211B;
            gg.fill(left + 6, y, right - 5, y + LINE_H - 1, rowColor);
            int bulletColor = colorForLine(line);
            gg.fill(left + 9, y + 4, left + 12, y + 7, bulletColor);
            gg.drawString(font, line, right - PAD_X - font.width(line), y + 2, PixelTheme.TEXT, false);
            y += LINE_H;
        }
    }

    private static int colorForLine(String line) {
        if (line == null) {
            return PixelTheme.ACCENT;
        }
        if (line.contains("§c") || line.contains("红")) {
            return PixelTheme.RED;
        }
        if (line.contains("§9") || line.contains("蓝")) {
            return PixelTheme.BLUE;
        }
        if (line.contains("§a") || line.contains("胜") || line.contains("击杀")) {
            return PixelTheme.SUCCESS;
        }
        if (line.contains("§e")) {
            return PixelTheme.WARNING;
        }
        return PixelTheme.ACCENT;
    }
}
