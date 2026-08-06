package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientHotZoneState;

import java.util.function.Supplier;

/**
 * S→C：清空客户端缓存的热区矩形边界。对局结束（{@code ArcadeMatch#finish}）或玩家主动退出
 * 对局（{@code ArcadeMatch#quitPlayer}）时下发，避免玩家回到大厅后世界渲染的选区高亮框
 * 残留在原地——{@link HotZoneAreaPacket} 只管"设置"，清空是独立的反向操作，不复用同一个包
 * （不用一个哨兵值表示"清空"，语义更直白）。
 */
public final class ClearHotZonePacket {

    public ClearHotZonePacket() {
    }

    public static void encode(ClearHotZonePacket msg, FriendlyByteBuf buf) {
    }

    public static ClearHotZonePacket decode(FriendlyByteBuf buf) {
        return new ClearHotZonePacket();
    }

    public static void handle(ClearHotZonePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientHotZoneState::clear));
        context.setPacketHandled(true);
    }
}
