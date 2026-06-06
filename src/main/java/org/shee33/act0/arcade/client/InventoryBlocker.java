package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;

/** 全局禁用玩家物品栏：sandbox 世界与 OP 管理员除外。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class InventoryBlocker {

    private InventoryBlocker() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            return;
        }
        if (mc.level == null || isSandbox(mc.level.dimension().location())) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isSandbox(ResourceLocation dimension) {
        return "sandbox".equals(dimension.getPath()) || "sandbox".equals(dimension.toString());
    }
}
