package org.shee33.act0.arcade.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.shee33.act0.arcade.Act0Arcade;

/** 客户端按键：B 打开配装系统；左 Alt 触发跳狙飞人蓄力跳。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcadeKeyMappings {

    public static final KeyMapping OPEN_LOADOUT = new KeyMapping(
            "key.act0_arcade.open_loadout",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.act0_arcade");

    public static final KeyMapping CHARGE_JUMP = new KeyMapping(
            "key.act0_arcade.charge_jump",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.act0_arcade");

    private ArcadeKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_LOADOUT);
        event.register(CHARGE_JUMP);
    }
}
