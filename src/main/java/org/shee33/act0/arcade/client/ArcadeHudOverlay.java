package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.client.screen.FlatTheme;

import java.util.List;

/**
 * 客户端对局 HUD：在屏幕右侧自绘侧边栏（FlatTheme 战地 2042 风格）。
 *
 * <p>替代原版计分板侧边栏，<b>不渲染右侧红色分值（序号）</b>。内容来自 {@link ClientSidebar}，
 * 由服务端经 {@code SyncSidebarPacket} 下发。位置与原版侧边栏一致（右边缘、垂直居中）。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeHudOverlay {

    private static final int PAD_X = 8;
    private static final int LINE_H = 12;
    private static final int HEADER_H = 16;

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

        // 主面板：扁平纯色 + 1px 边框 + 标题栏（无阴影/无斜角高光）
        FlatTheme.panel(gg, left, top, panelW, totalH);
        FlatTheme.titleBar(gg, left, top, panelW, HEADER_H);
        gg.fill(left, top, left + 2, top + totalH, FlatTheme.ACCENT);

        // 标题：右对齐文本
        gg.drawString(font, title, right - PAD_X - font.width(title), top + 4, FlatTheme.TEXT_HEADER, false);

        int y = top + HEADER_H + 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int rowColor = (i & 1) == 0 ? FlatTheme.SURFACE : FlatTheme.SURFACE_DISABLED;
            gg.fill(left + 4, y, right - 4, y + LINE_H - 1, rowColor);
            int bulletColor = colorForLine(line);
            gg.fill(left + 6, y + 4, left + 9, y + 7, bulletColor);
            gg.drawString(font, line, right - PAD_X - font.width(line), y + 2, FlatTheme.TEXT, false);
            y += LINE_H;
        }
    }

    private static int colorForLine(String line) {
        if (line == null) {
            return FlatTheme.ACCENT;
        }
        if (line.contains("§c") || line.contains("红")) {
            return FlatTheme.RED;
        }
        if (line.contains("§9") || line.contains("蓝")) {
            return FlatTheme.BLUE;
        }
        if (line.contains("§a") || line.contains("胜") || line.contains("击杀")) {
            return FlatTheme.SUCCESS;
        }
        if (line.contains("§e")) {
            return FlatTheme.WARNING;
        }
        return FlatTheme.ACCENT;
    }
}
