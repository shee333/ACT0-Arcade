package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.storage.ArcadeLoadoutStore;

import java.util.function.Supplier;

/** C→S：选择某套配装用于下次复活，或设为默认配装。 */
public final class SelectLoadoutPacket {

    public enum Action {
        SELECT_NEXT,
        SET_DEFAULT
    }

    private final int index;
    private final Action action;
    private final boolean matchContext;

    public SelectLoadoutPacket(int index, Action action) {
        this(index, action, false);
    }

    public SelectLoadoutPacket(int index, Action action, boolean matchContext) {
        this.index = index;
        this.action = action != null ? action : Action.SELECT_NEXT;
        this.matchContext = matchContext;
    }

    public static void encode(SelectLoadoutPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.matchContext);
    }

    public static SelectLoadoutPacket decode(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        int ordinal = buf.readVarInt();
        Action[] actions = Action.values();
        Action action = (ordinal >= 0 && ordinal < actions.length) ? actions[ordinal] : Action.SELECT_NEXT;
        boolean matchContext = buf.readBoolean();
        return new SelectLoadoutPacket(index, action, matchContext);
    }

    public static void handle(SelectLoadoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!msg.matchContext && !Act0Arcade.services().matches().canChangeLoadout(sender.getUUID())) {
                sender.displayClientMessage(Component.literal("§c战斗中无法更换配装"), true);
                return;
            }
            ArcadeLoadoutStore store = ArcadeLoadoutStore.get(sender.server);
            if (msg.action == Action.SET_DEFAULT) {
                store.setDefault(sender.getUUID(), msg.index,
                        DefaultLoadoutCatalog.defaultLoadout(Act0Arcade.services().registry()));
                sender.displayClientMessage(Component.literal("§a已设为默认配装"), true);
            } else {
                store.selectActive(sender.getUUID(), msg.index,
                        DefaultLoadoutCatalog.defaultLoadout(Act0Arcade.services().registry()));
                if (msg.matchContext || Act0Arcade.services().matches().isInMatch(sender.getUUID())) {
                    sender.displayClientMessage(Component.literal("§a将在下次复活时使用该配装"), true);
                } else {
                    sender.displayClientMessage(Component.literal("§a已选择配装"), true);
                }
            }
            if (!msg.matchContext) {
                ArcadeNetwork.openLoadout(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
