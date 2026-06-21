package org.shee33.act0.arcade.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.JumpChargePacket;
import org.shee33.act0.arcade.network.RequestLoadoutPacket;

/** 客户端输入轮询：按 B 请求服务端打开配装。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeClientInput {
    private static final int CHARGE_JUMP_LONG_PRESS_TICKS = 8;
    private static boolean lastChargeJumpDown;
    private static boolean jumpKeyHeld;
    private static int jumpKeyHoldTicks;

    private ArcadeClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            handleJumpChargeInput();
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ModList.get().isLoaded("act0_battlefield") && isBattlefieldActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            resetJumpChargeInput(false);
            return;
        }
        while (ArcadeKeyMappings.OPEN_LOADOUT.consumeClick()) {
            ArcadeNetwork.CHANNEL.sendToServer(new RequestLoadoutPacket());
        }
    }

    private static void handleJumpChargeInput() {
        if (ModList.get().isLoaded("act0_battlefield") && isBattlefieldActive()) {
            resetJumpChargeInput(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !isJumpSniperActive()) {
            resetJumpChargeInput(true);
            return;
        }
        boolean down = isPhysicalJumpDown(mc);
        if (down) {
            suppressVanillaJump(mc);
            if (!jumpKeyHeld) {
                jumpKeyHeld = true;
                jumpKeyHoldTicks = 0;
                sendChargeJump(true, false);
            }
            jumpKeyHoldTicks++;
        } else if (jumpKeyHeld) {
            boolean normalJump = jumpKeyHoldTicks < CHARGE_JUMP_LONG_PRESS_TICKS;
            jumpKeyHeld = false;
            jumpKeyHoldTicks = 0;
            sendChargeJump(false, normalJump);
        } else {
            sendChargeJump(false, false);
        }
    }

    private static boolean isJumpSniperActive() {
        return ClientSidebar.isShown() && ClientSidebar.title().contains("跳狙飞人");
    }

    private static boolean isPhysicalJumpDown(Minecraft mc) {
        KeyMapping jump = mc.options.keyJump;
        InputConstants.Key key = jump.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return jump.isDown();
    }

    private static void suppressVanillaJump(Minecraft mc) {
        mc.options.keyJump.setDown(false);
    }

    private static void resetJumpChargeInput(boolean notifyServer) {
        jumpKeyHeld = false;
        jumpKeyHoldTicks = 0;
        if (notifyServer) {
            sendChargeJump(false, false);
        } else {
            lastChargeJumpDown = false;
        }
    }

    private static void sendChargeJump(boolean down) {
        sendChargeJump(down, false);
    }

    private static void sendChargeJump(boolean down, boolean normalJump) {
        if (down == lastChargeJumpDown) {
            if (normalJump) {
                ArcadeNetwork.CHANNEL.sendToServer(new JumpChargePacket(false, true));
            }
            return;
        }
        lastChargeJumpDown = down;
        ArcadeNetwork.CHANNEL.sendToServer(new JumpChargePacket(down, normalJump));
    }

    private static boolean isBattlefieldActive() {
        try {
            Class<?> hud = Class.forName("org.shee33.act0.battlefield.client.ClientBattleHud");
            if (Boolean.TRUE.equals(hud.getMethod("isShown").invoke(null))) {
                return true;
            }
            Class<?> deploy = Class.forName("org.shee33.act0.battlefield.client.ClientDeployStatus");
            Object status = deploy.getMethod("status").invoke(null);
            if (status != null && Boolean.TRUE.equals(status.getClass().getMethod("active").invoke(status))) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }
}
