package org.shee33.act0.arcade.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.economy.BuyOutcome;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.match.ArcadeMatch;
import org.shee33.act0.arcade.match.ArcadeRoom;
import org.shee33.act0.arcade.match.RoomManager;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO;
import org.shee33.act0.arcade.storage.ApparelCatalogIO;
import org.shee33.act0.arcade.storage.ArcadeApparelStore;
import org.shee33.act0.arcade.storage.ArcadeLoadoutStore;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;
import org.shee33.act0.arcade.storage.ArenaRegistry;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 街机模组网络通道：配装 GUI 开屏（S→C）、配装保存（C→S）、装备目录同步（S→C）、游戏浏览器开屏（S→C）。
 */
public final class ArcadeNetwork {

        private static final String PROTOCOL = "7";

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
        CHANNEL.registerMessage(id++, RequestLoadoutPacket.class,
                RequestLoadoutPacket::encode, RequestLoadoutPacket::decode, RequestLoadoutPacket::handle);
        CHANNEL.registerMessage(id++, SelectLoadoutPacket.class,
                SelectLoadoutPacket::encode, SelectLoadoutPacket::decode, SelectLoadoutPacket::handle);
        CHANNEL.registerMessage(id++, SyncFireLockPacket.class,
                SyncFireLockPacket::encode, SyncFireLockPacket::decode, SyncFireLockPacket::handle);
        CHANNEL.registerMessage(id++, SyncApparelPacket.class,
                SyncApparelPacket::encode, SyncApparelPacket::decode, SyncApparelPacket::handle);
        CHANNEL.registerMessage(id++, SaveApparelPacket.class,
                SaveApparelPacket::encode, SaveApparelPacket::decode, SaveApparelPacket::handle);
    }

    /** 把服务端当前装备目录下发给指定玩家。 */
    public static void syncCatalog(ServerPlayer player, LoadoutRegistry registry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncCatalogPacket(LoadoutCatalogIO.toDtos(registry)));
    }

        /** 把服饰目录和玩家当前服饰选择下发给指定玩家。 */
        public static void syncApparel(ServerPlayer player) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new SyncApparelPacket(ApparelCatalogIO.toDtos(Act0Arcade.services().apparel()),
                                                ArcadeApparelStore.get(player.server).getOrCreate(player.getUUID())));
        }

    /** 向玩家下发死亡相机/滤镜状态。 */
    public static void sendDeathCam(ServerPlayer player, boolean active, String killerName) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DeathCamPacket(active, killerName));
    }

        public static void sendFireLock(ServerPlayer player, boolean locked) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncFireLockPacket(locked));
        }

        public static void openLoadout(ServerPlayer player) {
                LoadoutSet set = ArcadeLoadoutStore.get(player.server).getOrCreateSet(player.getUUID(),
                                DefaultLoadoutCatalog.defaultLoadout(Act0Arcade.services().registry()));
                syncUnlocks(player);
                syncApparel(player);
                syncAttachmentCatalog(player);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenLoadoutPacket(set));
        }

        public static void openLoadoutSelector(ServerPlayer player) {
                LoadoutSet set = ArcadeLoadoutStore.get(player.server).getOrCreateSet(player.getUUID(),
                                DefaultLoadoutCatalog.defaultLoadout(Act0Arcade.services().registry()));
                syncUnlocks(player);
                syncApparel(player);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenLoadoutPacket(set, true));
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
        boolean canManage = true;
                List<String> arenaIds = new ArrayList<>();
                for (String arenaId : ArenaRegistry.get(player.getServer()).ids()) {
                        if (!rooms.isArenaReserved(arenaId)) {
                                arenaIds.add(arenaId);
                        }
                }
        List<RoomDto> dtos = new ArrayList<>();
                Set<String> representedMatches = new HashSet<>();
        for (ArcadeRoom room : rooms.all()) {
                        ArcadeMatch match = room.matchId() != null ? Act0Arcade.services().matches().get(room.matchId()) : null;
                        if (match != null) {
                                representedMatches.add(match.matchId());
                        }
            dtos.add(new RoomDto(
                    room.roomId(), rooms.modeName(room), room.arenaId(), room.hostName(),
                    room.size(), room.capacity(), rooms.targetText(room),
                                        room.contains(player.getUUID()), room.isInProgress(),
                                        match != null ? match.participantNames() : participantNames(player.getServer(), room.members()),
                                        match != null ? match.elapsedSeconds() : 0));
                }
                for (ArcadeMatch match : Act0Arcade.services().matches().all()) {
                        if (representedMatches.contains(match.matchId())) {
                                continue;
                        }
                        dtos.add(new RoomDto(
                                        match.matchId(), match.displayName(), match.arenaId(), "-",
                                        match.occupancy(), match.capacity(), match.targetText(),
                                        match.contains(player.getUUID()), true,
                                        match.participantNames(), match.elapsedSeconds()));
        }
                appendBattlefieldRows(player, dtos);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncRoomListPacket(canManage, open, arenaIds, dtos));
    }

        @SuppressWarnings("unchecked")
        private static void appendBattlefieldRows(ServerPlayer player, List<RoomDto> dtos) {
                try {
                        Class<?> mod = Class.forName("org.shee33.act0.battlefield.Act0Battlefield");
                        Object manager = mod.getMethod("manager").invoke(null);
                        Object rows = manager.getClass().getMethod("browserRows", ServerPlayer.class).invoke(manager, player);
                        if (!(rows instanceof List<?> list)) {
                                return;
                        }
                        for (Object rowObj : list) {
                                if (!(rowObj instanceof String[] row) || row.length < 11) {
                                        continue;
                                }
                                dtos.add(new RoomDto(row[0], row[1], row[2], row[3],
                                                parseInt(row[4]), parseInt(row[5]), row[6], Boolean.parseBoolean(row[7]),
                                                Boolean.parseBoolean(row[8]), row[9], parseInt(row[10])));
                        }
                } catch (ReflectiveOperationException ignored) {
                }
        }

        private static int parseInt(String raw) {
                try {
                        return Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                        return 0;
                }
        }

        private static String participantNames(net.minecraft.server.MinecraftServer server, Set<UUID> ids) {
                List<String> names = new ArrayList<>();
                for (UUID id : ids) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        names.add(p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8));
                }
                return String.join(", ", names);
        }

    /** 把最新房间列表推送给所有在线玩家（创建/加入/离开/开局后调用）。 */
    public static void broadcastRoomList(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRoomList(player);
        }
    }
}

