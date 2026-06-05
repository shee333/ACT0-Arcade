package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientBuyFeedback;
import org.shee33.act0.arcade.economy.BuyOutcome;

import java.util.function.Supplier;

/**
 * S→C：购买武器的结果回传，供二级武器选择界面在界面内即时提示（成功 / 余额不足 / 不可用…）并刷新余额。
 */
public final class BuyResultPacket {

    private final String key;
    private final BuyOutcome outcome;
    private final double balance;

    public BuyResultPacket(String key, BuyOutcome outcome, double balance) {
        this.key = key;
        this.outcome = outcome;
        this.balance = balance;
    }

    public static void encode(BuyResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key == null ? "" : msg.key);
        buf.writeVarInt(msg.outcome.ordinal());
        buf.writeDouble(msg.balance);
    }

    public static BuyResultPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        BuyOutcome outcome = BuyOutcome.byId(buf.readVarInt());
        double balance = buf.readDouble();
        return new BuyResultPacket(key, outcome, balance);
    }

    public static void handle(BuyResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientBuyFeedback.accept(msg.key, msg.outcome, msg.balance)));
        context.setPacketHandled(true);
    }
}
