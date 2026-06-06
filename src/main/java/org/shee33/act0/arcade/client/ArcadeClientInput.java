package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.RequestLoadoutPacket;

/** 客户端输入轮询：按 B 请求服务端打开配装。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeClientInput {

    private ArcadeClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ModList.get().isLoaded("act0_battlefield")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        while (ArcadeKeyMappings.OPEN_LOADOUT.consumeClick()) {
            ArcadeNetwork.CHANNEL.sendToServer(new RequestLoadoutPacket());
        }
    }
}
