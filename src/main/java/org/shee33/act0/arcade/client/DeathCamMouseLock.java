package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;

/** 死亡相机期间锁定客户端视角角度，避免鼠标移动造成镜头抖动。 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DeathCamMouseLock {

    private DeathCamMouseLock() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ClientDeathCam.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ClientDeathCam.lockAnglesIfNeeded(mc.player.getYRot(), mc.player.getXRot());
        if (ClientDeathCam.hasLockedAngles()) {
            mc.player.setYRot(ClientDeathCam.lockedYaw());
            mc.player.setXRot(ClientDeathCam.lockedPitch());
            mc.player.setYHeadRot(ClientDeathCam.lockedYaw());
            mc.player.setYBodyRot(ClientDeathCam.lockedYaw());
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientDeathCam.hasLockedAngles()) {
            return;
        }
        event.setYaw(ClientDeathCam.lockedYaw());
        event.setPitch(ClientDeathCam.lockedPitch());
        event.setRoll(0f);
    }
}
