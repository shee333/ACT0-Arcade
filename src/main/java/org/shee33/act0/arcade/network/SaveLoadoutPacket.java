package org.shee33.act0.arcade.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;
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
    private final boolean activate;
    private final boolean makeDefault;

    public SaveLoadoutPacket(Loadout loadout) {
        this(0, loadout, true, false);
    }

    public SaveLoadoutPacket(int index, Loadout loadout) {
        this(index, loadout, true, false);
    }

    public SaveLoadoutPacket(int index, Loadout loadout, boolean activate, boolean makeDefault) {
        this.index = index;
        this.loadoutTag = LoadoutCodec.write(loadout);
        this.activate = activate;
        this.makeDefault = makeDefault;
    }

    private SaveLoadoutPacket(int index, CompoundTag loadoutTag, boolean activate, boolean makeDefault) {
        this.index = index;
        this.loadoutTag = loadoutTag;
        this.activate = activate;
        this.makeDefault = makeDefault;
    }

    public static void encode(SaveLoadoutPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
        buf.writeNbt(msg.loadoutTag);
        buf.writeBoolean(msg.activate);
        buf.writeBoolean(msg.makeDefault);
    }

    public static SaveLoadoutPacket decode(FriendlyByteBuf buf) {
        return new SaveLoadoutPacket(buf.readVarInt(), buf.readNbt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SaveLoadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!Act0Arcade.services().matches().canChangeLoadout(sender.getUUID())) {
                sender.displayClientMessage(Component.literal("§c战斗中无法修改配装"), true);
                return;
            }
            Loadout loadout = LoadoutCodec.read(msg.loadoutTag);
            ArcadeLoadoutStore.get(sender.server).save(sender.getUUID(), msg.index, loadout, msg.activate, msg.makeDefault);
            sender.displayClientMessage(Component.literal("§a配装已保存"), true);
        });
        context.setPacketHandled(true);
    }
}
