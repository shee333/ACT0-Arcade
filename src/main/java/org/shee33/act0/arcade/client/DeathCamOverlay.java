package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;

/**
 * 死亡滤镜：玩家阵亡观战等待期间，全屏叠加红色暗角渐变与"阵亡"大字，营造被钉在死亡现场的氛围。
 *
 * <p>由 {@link ClientDeathCam} 状态驱动；相机锁定在死亡点由服务端旁观钉位实现。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DeathCamOverlay {

    private DeathCamOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientDeathCam.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics gg = event.getGuiGraphics();
        int w = gg.guiWidth();
        int h = gg.guiHeight();
        float fade = ClientDeathCam.fadeIn();

        // 全屏暗红压底
        int baseAlpha = (int) (0x55 * fade);
        gg.fill(0, 0, w, h, (baseAlpha << 24) | 0x3A0000);

        // 上下暗角加重，营造电影黑边死亡感
        int barH = (int) (h * 0.18f);
        int barAlpha = (int) (0xCC * fade);
        gg.fillGradient(0, 0, w, barH, (barAlpha << 24), 0x00000000);
        gg.fillGradient(0, h - barH, w, h, 0x00000000, (barAlpha << 24));

        // 中央"阵亡"大字（缩放绘制）
        Font font = mc.font;
        String title = "§c§l阵 亡";
        gg.pose().pushPose();
        gg.pose().translate(w / 2.0, h / 2.0 - 30, 0);
        gg.pose().scale(2.4f, 2.4f, 1.0f);
        int tw = font.width(title);
        gg.drawString(font, title, -tw / 2, -4, 0xFFFFFFFF, true);
        gg.pose().popPose();

        // 凶手提示：镜头已朝向击杀者并对其高亮发光，这里补一行文字说明。
        String killer = ClientDeathCam.killerName();
        if (killer != null && !killer.isBlank()) {
            String line = "§7被 §c" + killer + " §7击杀";
            int lw = font.width(line);
            gg.drawString(font, line, (w - lw) / 2, h / 2 + 4, 0xFFFFFFFF, true);
        }
    }
}
