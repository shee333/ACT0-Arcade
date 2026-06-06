package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientDeathCam;

import java.util.function.Supplier;

/**
 * S→C：死亡相机/滤镜状态。
 *
 * <p>{@code active=true} 时客户端进入"锁定死亡视角"：渲染红色死亡滤镜 + 大字提示，相机被服务端钉在
 * 死亡点（玩家无法自由飞行观战）。{@code active=false} 时清除滤镜，恢复正常。
 */
public final class DeathCamPacket {

    private final boolean active;
    private final String killerName;

    public DeathCamPacket(boolean active, String killerName) {
        this.active = active;
        this.killerName = killerName != null ? killerName : "";
    }

    public static void encode(DeathCamPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeUtf(msg.killerName);
    }

    public static DeathCamPacket decode(FriendlyByteBuf buf) {
        return new DeathCamPacket(buf.readBoolean(), buf.readUtf());
    }

    public static void handle(DeathCamPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDeathCam.setActive(msg.active, msg.killerName)));
        context.setPacketHandled(true);
    }
}
