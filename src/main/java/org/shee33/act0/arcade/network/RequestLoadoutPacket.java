package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;

import java.util.function.Supplier;

/** C→S：玩家按键请求打开配装界面。 */
public final class RequestLoadoutPacket {

    public RequestLoadoutPacket() {
    }

    public static void encode(RequestLoadoutPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestLoadoutPacket decode(FriendlyByteBuf buf) {
        return new RequestLoadoutPacket();
    }

    public static void handle(RequestLoadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (Act0Arcade.services().matches().isInMatch(sender.getUUID())) {
                ArcadeNetwork.openLoadoutSelector(sender);
            } else {
                ArcadeNetwork.openLoadout(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
