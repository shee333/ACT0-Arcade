package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S：客户端请求某武器的改装上下文（打开改装界面时发送）。服务端回以 {@link SyncModificationPacket}。
 */
public final class RequestModificationPacket {

    private final String weaponKey;

    public RequestModificationPacket(String weaponKey) {
        this.weaponKey = weaponKey;
    }

    public static void encode(RequestModificationPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.weaponKey == null ? "" : msg.weaponKey);
    }

    public static RequestModificationPacket decode(FriendlyByteBuf buf) {
        return new RequestModificationPacket(buf.readUtf());
    }

    public static void handle(RequestModificationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null && msg.weaponKey != null && !msg.weaponKey.isEmpty()) {
                ArcadeNetwork.sendModification(sender, msg.weaponKey);
            }
        });
        context.setPacketHandled(true);
    }
}
