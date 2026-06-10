package org.shee33.act0.arcade.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientApparel;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.storage.ApparelCatalogIO;
import org.shee33.act0.arcade.storage.ApparelSelectionCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C：同步服饰目录和玩家当前共享服饰选择。 */
public final class SyncApparelPacket {
    private final List<ApparelCatalogIO.ApparelEntryDto> entries;
    private final CompoundTag selectionTag;

    public SyncApparelPacket(List<ApparelCatalogIO.ApparelEntryDto> entries, ApparelSelection selection) {
        this.entries = entries != null ? entries : List.of();
        this.selectionTag = ApparelSelectionCodec.write(selection);
    }

    private SyncApparelPacket(List<ApparelCatalogIO.ApparelEntryDto> entries, CompoundTag selectionTag) {
        this.entries = entries;
        this.selectionTag = selectionTag;
    }

    public static void encode(SyncApparelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (ApparelCatalogIO.ApparelEntryDto e : msg.entries) {
            buf.writeUtf(safe(e.key));
            buf.writeUtf(safe(e.name));
            buf.writeUtf(safe(e.slot));
            buf.writeVarInt(e.price);
            buf.writeBoolean(e.isDefault);
            buf.writeUtf(safe(e.snbt));
        }
        buf.writeNbt(msg.selectionTag);
    }

    public static SyncApparelPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ApparelCatalogIO.ApparelEntryDto> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ApparelCatalogIO.ApparelEntryDto dto = new ApparelCatalogIO.ApparelEntryDto();
            dto.key = buf.readUtf();
            dto.name = buf.readUtf();
            dto.slot = buf.readUtf();
            dto.price = buf.readVarInt();
            dto.isDefault = buf.readBoolean();
            dto.snbt = buf.readUtf();
            list.add(dto);
        }
        return new SyncApparelPacket(list, buf.readNbt());
    }

    public static void handle(SyncApparelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientApparel.accept(msg.entries, ApparelSelectionCodec.read(msg.selectionTag))));
        context.setPacketHandled(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
