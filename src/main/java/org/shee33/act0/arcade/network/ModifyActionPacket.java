package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;
import org.shee33.act0.arcade.loadout.mc.GunModService;

import java.util.function.Supplier;

/**
 * C→S：在某武器某槽位安装 / 卸下配件。{@code attachmentKey} 为空串表示卸下该槽位。
 * 服务端处理后回以最新的 {@link SyncModificationPacket}；失败时附带提示。
 */
public final class ModifyActionPacket {

    private final String weaponKey;
    private final String slot;
    private final String attachmentKey;

    public ModifyActionPacket(String weaponKey, String slot, String attachmentKey) {
        this.weaponKey = weaponKey;
        this.slot = slot;
        this.attachmentKey = attachmentKey;
    }

    public static void encode(ModifyActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(safe(msg.weaponKey));
        buf.writeUtf(safe(msg.slot));
        buf.writeUtf(safe(msg.attachmentKey));
    }

    public static ModifyActionPacket decode(FriendlyByteBuf buf) {
        return new ModifyActionPacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ModifyActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || msg.weaponKey == null || msg.weaponKey.isEmpty()) {
                return;
            }
            AttachmentSlotType slot = AttachmentSlotType.byName(msg.slot);
            if (slot == null) {
                return;
            }
            String attachmentKey = (msg.attachmentKey == null || msg.attachmentKey.isEmpty())
                    ? null : msg.attachmentKey;
            GunModService.Result result = Act0Arcade.services().gunMod()
                    .applyModification(sender, msg.weaponKey, slot, attachmentKey);
            if (!result.ok()) {
                sender.sendSystemMessage(Component.literal("§c" + result.message()));
            }
            ArcadeNetwork.sendModification(sender, msg.weaponKey);
        });
        context.setPacketHandled(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
