package org.shee33.act0.arcade.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientLoadoutScreenOpener;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.storage.LoadoutSetCodec;

import java.util.function.Supplier;

/**
 * S→C：请求客户端打开配装编辑界面，携带玩家的多套配装。
 */
public final class OpenLoadoutPacket {

    private final CompoundTag setTag;
    private final boolean quickSelect;

    public OpenLoadoutPacket(Loadout loadout) {
        this(LoadoutSet.single(loadout));
    }

    public OpenLoadoutPacket(LoadoutSet set) {
        this(set, false);
    }

    public OpenLoadoutPacket(LoadoutSet set, boolean quickSelect) {
        this.setTag = LoadoutSetCodec.write(set);
        this.quickSelect = quickSelect;
    }

    private OpenLoadoutPacket(CompoundTag setTag, boolean quickSelect) {
        this.setTag = setTag;
        this.quickSelect = quickSelect;
    }

    public static void encode(OpenLoadoutPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.setTag);
        buf.writeBoolean(msg.quickSelect);
    }

    public static OpenLoadoutPacket decode(FriendlyByteBuf buf) {
        return new OpenLoadoutPacket(buf.readNbt(), buf.readBoolean());
    }

    public static void handle(OpenLoadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> {
                if (msg.quickSelect) {
                    ClientLoadoutScreenOpener.openQuickSelect(LoadoutSetCodec.read(msg.setTag));
                } else {
                    ClientLoadoutScreenOpener.open(LoadoutSetCodec.read(msg.setTag));
                }
            }));
        context.setPacketHandled(true);
    }
}
