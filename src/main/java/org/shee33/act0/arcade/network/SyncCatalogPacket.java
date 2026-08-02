package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO.CatalogEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：把服务端当前装备目录（JSON 数据驱动）完整下发给客户端，供配装界面 / 游戏浏览器渲染。
 */
public final class SyncCatalogPacket {

    private static final int MAX_LIST_ENTRIES = 256;

    private final List<CatalogEntryDto> entries;

    public SyncCatalogPacket(List<CatalogEntryDto> entries) {
        this.entries = entries;
    }

    public static void encode(SyncCatalogPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (CatalogEntryDto entry : msg.entries) {
            buf.writeUtf(safe(entry.key));
            buf.writeUtf(safe(entry.name));
            buf.writeUtf(safe(entry.category));
            buf.writeVarInt(entry.price);
            buf.writeVarInt(entry.ammo);
            buf.writeBoolean(entry.isDefault);
            buf.writeUtf(safe(entry.snbt));
            List<String> classes = entry.classes != null ? entry.classes : List.of();
            buf.writeVarInt(classes.size());
            for (String c : classes) {
                buf.writeUtf(safe(c));
            }
        }
    }

    public static SyncCatalogPacket decode(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        List<CatalogEntryDto> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CatalogEntryDto dto = new CatalogEntryDto();
            dto.key = buf.readUtf();
            dto.name = buf.readUtf();
            dto.category = buf.readUtf();
            dto.price = buf.readVarInt();
            dto.ammo = buf.readVarInt();
            dto.isDefault = buf.readBoolean();
            dto.snbt = buf.readUtf();
            int cn = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
            List<String> classes = new ArrayList<>(cn);
            for (int j = 0; j < cn; j++) {
                classes.add(buf.readUtf());
            }
            dto.classes = classes;
            list.add(dto);
        }
        return new SyncCatalogPacket(list);
    }

    public static void handle(SyncCatalogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCatalog.accept(msg.entries)));
        context.setPacketHandled(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
