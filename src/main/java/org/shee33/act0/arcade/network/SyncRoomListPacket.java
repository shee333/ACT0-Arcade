package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientRoomList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：下发当前可用房间列表与可用竞技场，并标记接收者是否有管理权限（可建房）。
 *
 * <p>{@code open=true} 表示这是玩家主动请求打开浏览界面（如 {@code /arcade browse}），
 * 客户端未开界面时会为其打开；{@code open=false} 为广播/定时刷新，
 * <b>仅在界面已打开时就地刷新</b>，不会弹出界面（避免打扰其他玩家）。
 */
public final class SyncRoomListPacket {

    private static final int MAX_LIST_ENTRIES = 256;

    private final boolean canManage;
    private final boolean open;
    private final List<String> arenaIds;
    private final List<RoomDto> rooms;

    public SyncRoomListPacket(boolean canManage, boolean open, List<String> arenaIds, List<RoomDto> rooms) {
        this.canManage = canManage;
        this.open = open;
        this.arenaIds = arenaIds;
        this.rooms = rooms;
    }

    public static void encode(SyncRoomListPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.canManage);
        buf.writeBoolean(msg.open);
        buf.writeVarInt(msg.arenaIds.size());
        for (String id : msg.arenaIds) {
            buf.writeUtf(id);
        }
        buf.writeVarInt(msg.rooms.size());
        for (RoomDto room : msg.rooms) {
            room.encode(buf);
        }
    }

    public static SyncRoomListPacket decode(FriendlyByteBuf buf) {
        boolean canManage = buf.readBoolean();
        boolean open = buf.readBoolean();
        int an = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        List<String> arenaIds = new ArrayList<>(an);
        for (int i = 0; i < an; i++) {
            arenaIds.add(buf.readUtf());
        }
        int rn = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        List<RoomDto> rooms = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) {
            rooms.add(RoomDto.decode(buf));
        }
        return new SyncRoomListPacket(canManage, open, arenaIds, rooms);
    }

    public static void handle(SyncRoomListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientRoomList.accept(msg.canManage, msg.open, msg.arenaIds, msg.rooms)));
        context.setPacketHandled(true);
    }
}
