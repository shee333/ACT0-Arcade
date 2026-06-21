package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;

import java.util.function.Supplier;

/** C→S：跳狙飞人蓄力跳按键状态。 */
public final class JumpChargePacket {
    private final boolean charging;

    public JumpChargePacket(boolean charging) {
        this.charging = charging;
    }

    public static void encode(JumpChargePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.charging);
    }

    public static JumpChargePacket decode(FriendlyByteBuf buf) {
        return new JumpChargePacket(buf.readBoolean());
    }

    public static void handle(JumpChargePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                Act0Arcade.services().matches().setJumpCharging(sender.getUUID(), msg.charging);
            }
        });
        context.setPacketHandled(true);
    }
}
