package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientAttachmentCatalog;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO.AttachmentEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：把服务端当前配件目录完整下发给客户端，供改装界面渲染。
 */
public final class SyncAttachmentCatalogPacket {

    private final List<AttachmentEntryDto> entries;

    public SyncAttachmentCatalogPacket(List<AttachmentEntryDto> entries) {
        this.entries = entries;
    }

    public static void encode(SyncAttachmentCatalogPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (AttachmentEntryDto e : msg.entries) {
            buf.writeUtf(safe(e.key));
            buf.writeUtf(safe(e.attachmentId));
            buf.writeUtf(safe(e.name));
            buf.writeUtf(safe(e.slot));
            buf.writeVarInt(e.price);
            buf.writeBoolean(e.isDefault);
            buf.writeUtf(safe(e.snbt));
        }
    }

    public static SyncAttachmentCatalogPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<AttachmentEntryDto> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AttachmentEntryDto dto = new AttachmentEntryDto();
            dto.key = buf.readUtf();
            dto.attachmentId = buf.readUtf();
            dto.name = buf.readUtf();
            dto.slot = buf.readUtf();
            dto.price = buf.readVarInt();
            dto.isDefault = buf.readBoolean();
            dto.snbt = buf.readUtf();
            list.add(dto);
        }
        return new SyncAttachmentCatalogPacket(list);
    }

    public static void handle(SyncAttachmentCatalogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAttachmentCatalog.accept(msg.entries)));
        context.setPacketHandled(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
