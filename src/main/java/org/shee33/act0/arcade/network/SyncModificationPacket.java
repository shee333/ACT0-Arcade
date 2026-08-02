package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientModification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：下发某玩家对某武器的改装上下文（兼容/已安装），供改装界面刷新。
 */
public final class SyncModificationPacket {

    private static final int MAX_LIST_ENTRIES = 256;

    private final ModificationDto dto;

    public SyncModificationPacket(ModificationDto dto) {
        this.dto = dto;
    }

    public static void encode(SyncModificationPacket msg, FriendlyByteBuf buf) {
        ModificationDto dto = msg.dto;
        buf.writeUtf(safe(dto.weaponKey));
        List<ModificationDto.SlotDto> slots = dto.slots != null ? dto.slots : List.of();
        buf.writeVarInt(slots.size());
        for (ModificationDto.SlotDto slot : slots) {
            buf.writeUtf(safe(slot.slot));
            buf.writeUtf(safe(slot.installedKey));
            List<String> compat = slot.compatibleKeys != null ? slot.compatibleKeys : List.of();
            buf.writeVarInt(compat.size());
            for (String key : compat) {
                buf.writeUtf(safe(key));
            }
        }
    }

    public static SyncModificationPacket decode(FriendlyByteBuf buf) {
        ModificationDto dto = new ModificationDto(buf.readUtf());
        int n = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        for (int i = 0; i < n; i++) {
            ModificationDto.SlotDto slot = new ModificationDto.SlotDto(buf.readUtf());
            slot.installedKey = buf.readUtf();
            int cn = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
            List<String> compat = new ArrayList<>(cn);
            for (int j = 0; j < cn; j++) {
                compat.add(buf.readUtf());
            }
            slot.compatibleKeys = compat;
            dto.slots.add(slot);
        }
        return new SyncModificationPacket(dto);
    }

    public static void handle(SyncModificationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientModification.accept(msg.dto)));
        context.setPacketHandled(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
