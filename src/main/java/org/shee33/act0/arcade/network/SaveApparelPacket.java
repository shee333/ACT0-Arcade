package org.shee33.act0.arcade.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.storage.ApparelSelectionCodec;
import org.shee33.act0.arcade.storage.ArcadeApparelStore;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;

import java.util.function.Supplier;

/** C→S：保存玩家全配装共享服饰选择。 */
public final class SaveApparelPacket {
    private final CompoundTag selectionTag;

    public SaveApparelPacket(ApparelSelection selection) {
        this.selectionTag = ApparelSelectionCodec.write(selection);
    }

    private SaveApparelPacket(CompoundTag selectionTag) {
        this.selectionTag = selectionTag;
    }

    public static void encode(SaveApparelPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.selectionTag);
    }

    public static SaveApparelPacket decode(FriendlyByteBuf buf) {
        return new SaveApparelPacket(buf.readNbt());
    }

    public static void handle(SaveApparelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ApparelSelection selection = ApparelSelectionCodec.read(msg.selectionTag);
            var unlocked = ArcadePlayerUnlocks.get(sender.server).unlocked(sender.getUUID());
            for (var e : selection.all().entrySet()) {
                ApparelItem item = Act0Arcade.services().apparel().find(e.getValue()).orElse(null);
                if (item == null || item.slot() != e.getKey() || !item.isUnlockedBy(unlocked)) {
                    selection.set(e.getKey(), null);
                }
            }
            ArcadeApparelStore.get(sender.server).saveSelection(sender.getUUID(), selection);
            ArcadeNetwork.syncApparel(sender);
            sender.displayClientMessage(Component.literal("§a服饰已保存"), true);
        });
        context.setPacketHandled(true);
    }
}
