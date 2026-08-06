package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.arena.HotZoneArea;
import org.shee33.act0.arcade.client.ClientHotZoneState;

import java.util.function.Supplier;

/**
 * S→C：把服务端确定的热区矩形边界下发给客户端，写入 {@link ClientHotZoneState} 供后续的
 * 边界可视化任务读取。在管理员用热区框选道具确定区域时、以及热区模式对局开始/中途加入时下发。
 */
public final class HotZoneAreaPacket {

    private final String dimension;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    public HotZoneAreaPacket(HotZoneArea area) {
        this(area.dimension(), area.minX(), area.maxX(), area.minY(), area.maxY(), area.minZ(), area.maxZ());
    }

    public HotZoneAreaPacket(String dimension, double minX, double maxX, double minY, double maxY,
                             double minZ, double maxZ) {
        this.dimension = dimension;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public static void encode(HotZoneAreaPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dimension);
        buf.writeDouble(msg.minX);
        buf.writeDouble(msg.maxX);
        buf.writeDouble(msg.minY);
        buf.writeDouble(msg.maxY);
        buf.writeDouble(msg.minZ);
        buf.writeDouble(msg.maxZ);
    }

    public static HotZoneAreaPacket decode(FriendlyByteBuf buf) {
        return new HotZoneAreaPacket(buf.readUtf(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(HotZoneAreaPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHotZoneState.set(msg.dimension, msg.minX, msg.maxX,
                        msg.minY, msg.maxY, msg.minZ, msg.maxZ)));
        context.setPacketHandled(true);
    }
}
