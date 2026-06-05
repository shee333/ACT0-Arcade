package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;

import java.util.List;

/**
 * 客户端对局 HUD：在屏幕右侧自绘侧边栏。
 *
 * <p>替代原版计分板侧边栏，<b>不渲染右侧红色分值（序号）</b>。内容来自 {@link ClientSidebar}，
 * 由服务端经 {@code SyncSidebarPacket} 下发。位置与原版侧边栏一致（右边缘、垂直居中）。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeHudOverlay {

    private static final int PAD_X = 4;
    private static final int LINE_H = 9;
    private static final int BG = 0x90000000;
    private static final int TITLE_BG = 0xB0000000;

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
        int panelW = contentW + PAD_X * 2;
        int screenW = gg.guiWidth();
        int screenH = gg.guiHeight();

        int totalH = LINE_H + lines.size() * LINE_H;
        int top = screenH / 2 - totalH / 2;
        int right = screenW - 1;
        int left = right - panelW;

        // 标题栏背景
        gg.fill(left, top, right, top + LINE_H, TITLE_BG);
        // 内容背景
        gg.fill(left, top + LINE_H, right, top + totalH, BG);

        // 标题：右对齐
        gg.drawString(font, title, right - PAD_X - font.width(title), top + 1, 0xFFFFFFFF, false);

        int y = top + LINE_H;
        for (String line : lines) {
            gg.drawString(font, line, right - PAD_X - font.width(line), y + 1, 0xFFFFFFFF, false);
            y += LINE_H;
        }
    }
}
