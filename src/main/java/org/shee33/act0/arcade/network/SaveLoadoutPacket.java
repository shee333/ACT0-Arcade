package org.shee33.act0.arcade.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.storage.ArcadeLoadoutStore;
import org.shee33.act0.arcade.storage.LoadoutCodec;

import java.util.function.Supplier;

/**
 * C→S：客户端提交编辑后的配装，服务端校验并持久化。
 */
public final class SaveLoadoutPacket {

    private final int index;
    private final CompoundTag loadoutTag;

    public SaveLoadoutPacket(Loadout loadout) {
        this(0, loadout);
    }

    public SaveLoadoutPacket(int index, Loadout loadout) {
        this.index = index;
        this.loadoutTag = LoadoutCodec.write(loadout);
    }

    private SaveLoadoutPacket(int index, CompoundTag loadoutTag) {
        this.index = index;
        this.loadoutTag = loadoutTag;
    }

    public static void encode(SaveLoadoutPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
        buf.writeNbt(msg.loadoutTag);
    }

    public static SaveLoadoutPacket decode(FriendlyByteBuf buf) {
        return new SaveLoadoutPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(SaveLoadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            Loadout loadout = LoadoutCodec.read(msg.loadoutTag);
            ArcadeLoadoutStore.get(sender.server).save(sender.getUUID(), msg.index, loadout);
        });
        context.setPacketHandled(true);
    }
}
