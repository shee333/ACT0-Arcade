package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientUnlocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：把指定玩家当前已购买/解锁的武器 key 集合下发给客户端，供二级武器选择界面渲染解锁状态。
 */
public final class SyncUnlocksPacket {

    private static final int MAX_LIST_ENTRIES = 256;

    private final List<String> keys;

    public SyncUnlocksPacket(Collection<String> keys) {
        this.keys = new ArrayList<>(keys);
    }

    public static void encode(SyncUnlocksPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.keys.size());
        for (String key : msg.keys) {
            buf.writeUtf(key == null ? "" : key);
        }
    }

    public static SyncUnlocksPacket decode(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf());
        }
        return new SyncUnlocksPacket(list);
    }

    public static void handle(SyncUnlocksPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientUnlocks.accept(msg.keys)));
        context.setPacketHandled(true);
    }
}
