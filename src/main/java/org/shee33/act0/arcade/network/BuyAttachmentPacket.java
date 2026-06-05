package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.economy.ArcadeEconomy;
import org.shee33.act0.arcade.economy.BuyOutcome;
import org.shee33.act0.arcade.economy.EconomyManager;
import org.shee33.act0.arcade.loadout.ArcadeAttachment;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * C→S：玩家购买解锁某配件。与武器购买共用经济 / 解锁机制。
 * 处理成功后下发解锁集合、购买结果，并刷新该武器的改装上下文。
 */
public final class BuyAttachmentPacket {

    private final String weaponKey;
    private final String attachmentKey;

    public BuyAttachmentPacket(String weaponKey, String attachmentKey) {
        this.weaponKey = weaponKey;
        this.attachmentKey = attachmentKey;
    }

    public static void encode(BuyAttachmentPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(safe(msg.weaponKey));
        buf.writeUtf(safe(msg.attachmentKey));
    }

    public static BuyAttachmentPacket decode(FriendlyByteBuf buf) {
        return new BuyAttachmentPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(BuyAttachmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || msg.attachmentKey == null || msg.attachmentKey.isEmpty()) {
                return;
            }
            buy(player, msg.attachmentKey);
            if (msg.weaponKey != null && !msg.weaponKey.isEmpty()) {
                ArcadeNetwork.sendModification(player, msg.weaponKey);
            }
        });
        context.setPacketHandled(true);
    }

    private static void buy(ServerPlayer player, String key) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID uuid = player.getUUID();
        ArcadeEconomy economy = EconomyManager.economy(server);
        Optional<ArcadeAttachment> found = Act0Arcade.services().attachments().find(key);
        if (found.isEmpty()) {
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.ERROR, economy.balance(uuid));
            return;
        }
        ArcadeAttachment att = found.get();
        ArcadePlayerUnlocks unlocks = ArcadePlayerUnlocks.get(server);
        if (att.isDefault() || unlocks.isUnlocked(uuid, key)) {
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.ALREADY_OWNED, economy.balance(uuid));
            return;
        }
        int price = att.price();
        if (!economy.isAvailable()) {
            player.sendSystemMessage(Component.literal("§c当前无法购买。"));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.UNAVAILABLE, 0);
            return;
        }
        if (!economy.has(uuid, price) || !economy.withdraw(uuid, price)) {
            player.sendSystemMessage(Component.literal("§c余额不足，需要 §e" + price));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.INSUFFICIENT, economy.balance(uuid));
            return;
        }
        unlocks.unlock(uuid, key);
        ArcadeNetwork.syncUnlocks(player);
        ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.SUCCESS, economy.balance(uuid));
        player.sendSystemMessage(Component.literal("§a已解锁配件 §f" + att.displayName()));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
