package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S：客户端请求最新房间列表（界面打开时与定时刷新时发送）。服务端回以 {@link SyncRoomListPacket}。
 */
public final class RequestRoomListPacket {

    public RequestRoomListPacket() {
    }

    public static void encode(RequestRoomListPacket msg, FriendlyByteBuf buf) {
        // 无负载
    }

    public static RequestRoomListPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomListPacket();
    }

    public static void handle(RequestRoomListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ArcadeNetwork.sendRoomList(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
