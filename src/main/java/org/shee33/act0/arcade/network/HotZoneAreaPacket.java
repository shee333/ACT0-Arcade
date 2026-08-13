package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.arena.HotZoneArea;
import org.shee33.act0.arcade.client.ClientHotZoneState;

import java.util.function.Supplier;

/**
 * S→C：把服务端当前生效的热区矩形边界下发给客户端，写入 {@link ClientHotZoneState} 供世界空间
 * 线框渲染读取。在管理员用热区框选道具确定区域时、热区模式对局开始/中途加入时，以及每次热区
 * 轮换与轮换预告时下发。
 *
 * <p>同时携带一个可选的"下一个热区"：只在迁移预告窗口内非空，客户端据此把即将启用的区域也画出来
 * （见 {@code ClientHotZoneOverlay}）。预告结束/完成迁移时服务端会重发一个不带下一区的包，因此
 * 客户端不需要自己给预告计时——服务端是唯一时间源。
 */
public final class HotZoneAreaPacket {

    private final HotZoneArea active;
    private final HotZoneArea next;

    public HotZoneAreaPacket(HotZoneArea active) {
        this(active, null);
    }

    public HotZoneAreaPacket(HotZoneArea active, HotZoneArea next) {
        this.active = active;
        this.next = next;
    }

    public static void encode(HotZoneAreaPacket msg, FriendlyByteBuf buf) {
        writeArea(buf, msg.active);
        buf.writeBoolean(msg.next != null);
        if (msg.next != null) {
            writeArea(buf, msg.next);
        }
    }

    public static HotZoneAreaPacket decode(FriendlyByteBuf buf) {
        HotZoneArea active = readArea(buf);
        HotZoneArea next = buf.readBoolean() ? readArea(buf) : null;
        return new HotZoneAreaPacket(active, next);
    }

    private static void writeArea(FriendlyByteBuf buf, HotZoneArea area) {
        buf.writeUtf(area.dimension());
        buf.writeDouble(area.minX());
        buf.writeDouble(area.maxX());
        buf.writeDouble(area.minY());
        buf.writeDouble(area.maxY());
        buf.writeDouble(area.minZ());
        buf.writeDouble(area.maxZ());
    }

    private static HotZoneArea readArea(FriendlyByteBuf buf) {
        return new HotZoneArea(buf.readUtf(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(HotZoneAreaPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHotZoneState.set(msg.active, msg.next)));
        context.setPacketHandled(true);
    }
}
