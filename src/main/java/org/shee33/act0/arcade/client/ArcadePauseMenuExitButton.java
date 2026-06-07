package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;

/** 在对局内 ESC 菜单“回到游戏”上方添加红色“退出对局”按钮。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadePauseMenuExitButton {

    private ArcadePauseMenuExitButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || !ClientSidebar.isShown()) {
            return;
        }
        int x = event.getScreen().width / 2 - 102;
        int y = event.getScreen().height / 4 - 16;
        event.addListener(Button.builder(Component.literal("§c退出对局"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.sendCommand("arcade leave");
                mc.setScreen(null);
            }
        }).bounds(x, y, 204, 20).build());
    }
}
