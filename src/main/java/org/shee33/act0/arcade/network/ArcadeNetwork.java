package org.shee33.act0.arcade.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.economy.BuyOutcome;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.match.ArcadeRoom;
import org.shee33.act0.arcade.match.RoomManager;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;
import org.shee33.act0.arcade.storage.ArenaRegistry;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO;

import java.util.ArrayList;
import java.util.List;

/**
 * 街机模组网络通道：配装 GUI 开屏（S→C）、配装保存（C→S）、装备目录同步（S→C）、游戏浏览器开屏（S→C）。
 */
public final class ArcadeNetwork {

        private static final String PROTOCOL = "2";

    @SuppressWarnings("removal")
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Act0Arcade.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ArcadeNetwork() {
    }

    /** 在模组构造期调用，注册所有数据包。 */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenLoadoutPacket.class,
                OpenLoadoutPacket::encode, OpenLoadoutPacket::decode, OpenLoadoutPacket::handle);
        CHANNEL.registerMessage(id++, SaveLoadoutPacket.class,
                SaveLoadoutPacket::encode, SaveLoadoutPacket::decode, SaveLoadoutPacket::handle);
        CHANNEL.registerMessage(id++, SyncCatalogPacket.class,
                SyncCatalogPacket::encode, SyncCatalogPacket::decode, SyncCatalogPacket::handle);
        CHANNEL.registerMessage(id++, SyncUnlocksPacket.class,
                SyncUnlocksPacket::encode, SyncUnlocksPacket::decode, SyncUnlocksPacket::handle);
        CHANNEL.registerMessage(id++, BuyResultPacket.class,
                BuyResultPacket::encode, BuyResultPacket::decode, BuyResultPacket::handle);
        CHANNEL.registerMessage(id++, SyncRoomListPacket.class,
                SyncRoomListPacket::encode, SyncRoomListPacket::decode, SyncRoomListPacket::handle);
        CHANNEL.registerMessage(id++, RequestRoomListPacket.class,
                RequestRoomListPacket::encode, RequestRoomListPacket::decode, RequestRoomListPacket::handle);
        CHANNEL.registerMessage(id++, SyncAttachmentCatalogPacket.class,
                SyncAttachmentCatalogPacket::encode, SyncAttachmentCatalogPacket::decode, SyncAttachmentCatalogPacket::handle);
        CHANNEL.registerMessage(id++, RequestModificationPacket.class,
                RequestModificationPacket::encode, RequestModificationPacket::decode, RequestModificationPacket::handle);
        CHANNEL.registerMessage(id++, SyncModificationPacket.class,
                SyncModificationPacket::encode, SyncModificationPacket::decode, SyncModificationPacket::handle);
        CHANNEL.registerMessage(id++, ModifyActionPacket.class,
                ModifyActionPacket::encode, ModifyActionPacket::decode, ModifyActionPacket::handle);
        CHANNEL.registerMessage(id++, BuyAttachmentPacket.class,
                BuyAttachmentPacket::encode, BuyAttachmentPacket::decode, BuyAttachmentPacket::handle);
        CHANNEL.registerMessage(id++, SyncSidebarPacket.class,
                SyncSidebarPacket::encode, SyncSidebarPacket::decode, SyncSidebarPacket::handle);
        CHANNEL.registerMessage(id++, DeathCamPacket.class,
                DeathCamPacket::encode, DeathCamPacket::decode, DeathCamPacket::handle);
        CHANNEL.registerMessage(id++, CloseRoomBrowserPacket.class,
                CloseRoomBrowserPacket::encode, CloseRoomBrowserPacket::decode, CloseRoomBrowserPacket::handle);
    }

    /** 把服务端当前装备目录下发给指定玩家。 */
    public static void syncCatalog(ServerPlayer player, LoadoutRegistry registry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncCatalogPacket(LoadoutCatalogIO.toDtos(registry)));
    }

    /** 向玩家下发死亡相机/滤镜状态。 */
    public static void sendDeathCam(ServerPlayer player, boolean active, String killerName) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DeathCamPacket(active, killerName));
    }

        public static void closeRoomBrowser(ServerPlayer player) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CloseRoomBrowserPacket());
        }

    /** 把服务端当前配件目录下发给指定玩家。 */
    public static void syncAttachmentCatalog(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncAttachmentCatalogPacket(
                        AttachmentCatalogIO.toDtos(Act0Arcade.services().attachments())));
    }

    /** 构建并下发某玩家对某武器的改装上下文。 */
    public static void sendModification(ServerPlayer player, String weaponKey) {
        ModificationDto dto = Act0Arcade.services().gunMod().buildModificationData(player, weaponKey);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncModificationPacket(dto));
    }

    /** 把指定玩家当前的武器解锁集合下发给该玩家客户端。 */
    public static void syncUnlocks(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncUnlocksPacket(ArcadePlayerUnlocks.get(player.getServer()).unlocked(player.getUUID())));
    }

    /** 回传购买结果，供二级武器选择界面内提示。 */
    public static void sendBuyResult(ServerPlayer player, String key, BuyOutcome outcome, double balance) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BuyResultPacket(key, outcome, balance));
    }

    /** 把当前房间列表下发给指定玩家用于刷新（不会弹出界面）。 */
    public static void sendRoomList(ServerPlayer player) {
        sendRoomList(player, false);
    }

    /**
     * 把当前房间列表（含可用竞技场、是否可建房）下发给指定玩家。
     *
     * @param open {@code true} 表示玩家主动请求开屏（客户端未开界面时会打开）；
     *             {@code false} 仅用于刷新（界面已开才更新，不弹窗）。
     */
    public static void sendRoomList(ServerPlayer player, boolean open) {
        RoomManager rooms = Act0Arcade.services().rooms();
        boolean canManage = player.hasPermissions(2);
        List<String> arenaIds = new ArrayList<>(ArenaRegistry.get(player.getServer()).ids());
        List<RoomDto> dtos = new ArrayList<>();
        for (ArcadeRoom room : rooms.all()) {
            dtos.add(new RoomDto(
                    room.roomId(), rooms.modeName(room), room.arenaId(), room.hostName(),
                    room.size(), room.capacity(), rooms.targetText(room),
                    room.contains(player.getUUID()), room.isInProgress()));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncRoomListPacket(canManage, open, arenaIds, dtos));
    }

    /** 把最新房间列表推送给所有在线玩家（创建/加入/离开/开局后调用）。 */
    public static void broadcastRoomList(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRoomList(player);
        }
    }
}

