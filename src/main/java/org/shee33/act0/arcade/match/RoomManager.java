package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.shee33.act0.arcade.mode.MatchOptions;
import org.shee33.act0.arcade.mode.MatchSettings;
import org.shee33.act0.arcade.mode.ScoringMode;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.storage.ArenaRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 房间管理器：持有所有等待中的 {@link ArcadeRoom}，提供创建/加入/离开/开始的完整流程。
 *
 * <p>房间满员立即自动开局，房主或管理员也可在满员前手动开局；开局通过 {@link MatchLauncher}
 * 创建 {@link ArcadeMatch} 后，房间随即被回收。一名玩家同一时刻只能在一个房间。
 * 所有方法在服务器主线程调用，非线程安全。
 */
public final class RoomManager {

    private final Map<String, ArcadeRoom> rooms = new LinkedHashMap<>();
    /** 玩家 → 所在房间 id，保证一名玩家只在一个房间。 */
    private final Map<UUID, String> roomOf = new LinkedHashMap<>();

    private final ArcadeServices services;
    private int counter;

    public RoomManager(ArcadeServices services) {
        this.services = services;
    }

    /** 房间满员开局所需的总人数（沿用匹配队列的人数门槛）。 */
    public static int capacityFor(String mode) {
        return MatchQueue.targetSize(mode);
    }

    public static int minCapacityFor(String mode) {
        return switch (mode) {
            case "duel_1v1" -> 2;
            case "duel_2v2" -> 4;
            case "team_deathmatch", "free_for_all" -> 2;
            default -> -1;
        };
    }

    public static int maxCapacityFor(String mode) {
        return switch (mode) {
            case "duel_1v1" -> 2;
            case "duel_2v2" -> 4;
            case "team_deathmatch", "free_for_all" -> 32;
            default -> -1;
        };
    }

    public static int normalizeCapacity(String mode, int requested) {
        int min = minCapacityFor(mode);
        int max = maxCapacityFor(mode);
        if (min <= 0 || max <= 0) {
            return -1;
        }
        int value = Math.max(min, Math.min(max, requested));
        if ("team_deathmatch".equals(mode) && value % 2 != 0) {
            value++;
            if (value > max) {
                value -= 2;
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 房间快照（只读，保持创建顺序）。 */
    public Collection<ArcadeRoom> all() {
        return new ArrayList<>(rooms.values());
    }

    public boolean isInRoom(UUID id) {
        return roomOf.containsKey(id);
    }

    /** 玩家所在房间 id，不在任何房间返回 {@code null}。 */
    public String roomIdOf(UUID id) {
        return roomOf.get(id);
    }

    /** 该房间的计分目标文案，如 "3 胜" / "30 杀"。 */
    public String targetText(ArcadeRoom room) {
        MatchSettings s = MatchLauncher.buildSettings(room.modeId(), room.capacity(), room.winTarget());
        if (s == null) {
            return "-";
        }
        int pts = s.roundFormat().pointsToWin();
        return s.scoringMode() == ScoringMode.KILL_COUNT ? (pts + " 杀") : (pts + " 胜");
    }

    /** 该房间的模式显示名。 */
    public String modeName(ArcadeRoom room) {
        MatchSettings s = MatchLauncher.buildSettings(room.modeId(), room.capacity(), room.winTarget());
        return s != null ? s.displayName() : room.modeId();
    }

    // ---------------- 创建 ----------------

    /**
     * 创建房间（房主自动加入）。仅校验业务规则，权限校验在命令层完成。
     *
     * @param winTarget 自定义计分目标；{@code null} 用模式默认
     * @return 面向发起者的反馈文本
     */
    public String create(MinecraftServer server, ServerPlayer host, String mode, String arenaId,
                         Integer winTarget, int timeLimitSeconds, boolean randomWeapons, Integer requestedCapacity) {
        int capacity = requestedCapacity != null ? normalizeCapacity(mode, requestedCapacity) : capacityFor(mode);
        if (capacity <= 0) {
            return "§c未知模式：" + mode;
        }
        UUID id = host.getUUID();
        if (services.matches().isInMatch(id)) {
            return "§c你已在对局中，无法创建房间。";
        }
        if (roomOf.containsKey(id)) {
            return "§e你已在一个房间中，请先离开。";
        }
        if (ArenaRegistry.get(server).find(arenaId).isEmpty()) {
            return "§c竞技场不存在：" + arenaId;
        }
        Integer target = winTarget != null ? Math.max(1, Math.min(99, winTarget)) : null;
        int timeLimit = Math.max(0, Math.min(3600, timeLimitSeconds));

        String roomId = nextRoomId();
        ArcadeRoom room = new ArcadeRoom(roomId, id, host.getGameProfile().getName(),
                mode, arenaId, target, capacity, timeLimit, randomWeapons);
        rooms.put(roomId, room);
        roomOf.put(id, roomId);
        String extra = (timeLimit > 0 ? " §7限时 §e" + (timeLimit / 60) + "分" + (timeLimit % 60) + "秒" : "")
                + (randomWeapons ? " §7随机武器" : "");
        return "§a已创建房间 §e" + modeName(room) + " §7@ §e" + arenaId
                + " §7(1/" + capacity + ")" + extra;
    }

    public String resize(MinecraftServer server, ServerPlayer player, String roomId, int requestedCapacity, boolean privileged) {
        ArcadeRoom room = roomId != null ? rooms.get(roomId) : rooms.get(roomOf.get(player.getUUID()));
        if (room == null) {
            return "§c房间不存在或已关闭。";
        }
        if (room.state() != ArcadeRoom.State.WAITING) {
            return "§c对局已开始，无法修改人数。";
        }
        if (!player.getUUID().equals(room.hostId()) && !privileged) {
            return "§c只有房主或管理员可以修改人数。";
        }
        int capacity = normalizeCapacity(room.modeId(), requestedCapacity);
        if (capacity <= 0) {
            return "§c未知模式：" + room.modeId();
        }
        if (capacity < room.size()) {
            return "§c人数不能小于当前成员数 " + room.size() + "。";
        }
        if (("duel_1v1".equals(room.modeId()) || "duel_2v2".equals(room.modeId())) && capacity != capacityFor(room.modeId())) {
            return "§c该模式为固定人数。";
        }
        room.setCapacity(capacity);
        notifyRoom(server, room, "§e房间人数已调整为 §f" + room.size() + "/" + room.capacity());
        return "§a已修改房间人数为 §e" + capacity;
    }

    /** 由房间构建完整可配置项。 */
    private static MatchOptions optionsOf(ArcadeRoom room) {
        return new MatchOptions(room.winTarget(), room.timeLimitSeconds(), room.randomWeapons());
    }

    // ---------------- 加入 ----------------

    /** 玩家加入指定房间；满员则立即开局。 */
    public String join(MinecraftServer server, ServerPlayer player, String roomId) {
        UUID id = player.getUUID();
        if (services.matches().isInMatch(id)) {
            return "§c你已在对局中，无法加入房间。";
        }
        ArcadeRoom room = rooms.get(roomId);
        if (room == null) {
            return "§c房间不存在或已关闭。";
        }
        if (room.contains(id)) {
            return "§e你已在该房间中 §7(" + room.size() + "/" + room.capacity() + ")";
        }
        if (room.isFull()) {
            return "§c房间已满。";
        }
        // 进行中的弹性房间：中途补人
        if (room.state() == ArcadeRoom.State.IN_PROGRESS) {
            return joinInProgress(server, player, room);
        }
        if (room.state() != ArcadeRoom.State.WAITING) {
            return "§c该房间已开始。";
        }
        // 若在其它房间，先离开
        String existing = roomOf.get(id);
        if (existing != null && !existing.equals(roomId)) {
            internalLeave(server, id, false);
        }
        room.addMember(id);
        roomOf.put(id, roomId);
        notifyRoom(server, room, "§b" + player.getGameProfile().getName()
                + " §7加入房间 §7(" + room.size() + "/" + room.capacity() + ")");

        if (room.isFull()) {
            return launch(server, room);
        }
        return "§a已加入房间 §e" + modeName(room) + " §7(" + room.size() + "/" + room.capacity() + ")";
    }

    /** 中途加入一场进行中的弹性对局。 */
    private String joinInProgress(MinecraftServer server, ServerPlayer player, ArcadeRoom room) {
        ArcadeMatch match = services.matches().get(room.matchId());
        if (match == null) {
            // 对局已结束但房间尚未回收：直接回收并拒绝
            onMatchEnded(room.matchId());
            return "§c对局已结束。";
        }
        if (!match.acceptsLatecomers()) {
            return "§c该对局暂不可加入（已满或即将结束）。";
        }
        UUID id = player.getUUID();
        String existing = roomOf.get(id);
        if (existing != null && !existing.equals(room.roomId())) {
            internalLeave(server, id, false);
        }
        String fail = match.addLatecomer(player);
        if (fail != null) {
            return fail;
        }
        room.addMember(id);
        roomOf.put(id, room.roomId());
        services.matches().addParticipant(room.matchId(), id);
        ArcadeNetwork.closeRoomBrowser(player);
        return "§a已加入进行中的对局 §e" + modeName(room);
    }

    // ---------------- 离开 ----------------

    public String leave(MinecraftServer server, ServerPlayer player) {
        if (!roomOf.containsKey(player.getUUID())) {
            return "§7你当前不在任何房间。";
        }
        boolean dissolved = internalLeave(server, player.getUUID(), true);
        return dissolved ? "§a已离开房间（房间已解散）。" : "§a已离开房间。";
    }

    /** 登出清理：等待中的房间等价于离开；进行中的房间保留坐位以便重连。 */
    public void onPlayerLogout(MinecraftServer server, UUID id) {
        String key = roomOf.get(id);
        if (key == null) {
            return;
        }
        ArcadeRoom room = rooms.get(key);
        if (room != null && room.state() == ArcadeRoom.State.IN_PROGRESS) {
            return; // 对局进行中：保留坐位，由对局层标记掉线
        }
        internalLeave(server, id, true);
    }

    /**
     * 内部离开逻辑。
     *
     * @param notify 是否向房间其余成员广播
     * @return 是否导致房间解散
     */
    private boolean internalLeave(MinecraftServer server, UUID id, boolean notify) {
        String key = roomOf.remove(id);
        if (key == null) {
            return false;
        }
        ArcadeRoom room = rooms.get(key);
        if (room == null) {
            return false;
        }
        room.removeMember(id);

        // 房主离开或房间清空 → 解散
        if (room.isEmpty() || id.equals(room.hostId())) {
            for (UUID other : room.members()) {
                roomOf.remove(other);
                if (notify) {
                    ServerPlayer p = server.getPlayerList().getPlayer(other);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal("§e房主已离开，房间已解散。"));
                    }
                }
            }
            rooms.remove(key);
            return true;
        }
        if (notify) {
            notifyRoom(server, room, "§7有玩家离开房间 §7(" + room.size() + "/" + room.capacity() + ")");
        }
        return false;
    }

    // ---------------- 开始 ----------------

    /** 房主或管理员手动开局。 */
    public String start(MinecraftServer server, ServerPlayer player, boolean privileged) {
        String key = roomOf.get(player.getUUID());
        if (key == null) {
            return "§c你当前不在任何房间。";
        }
        ArcadeRoom room = rooms.get(key);
        if (room == null) {
            return "§c房间不存在或已关闭。";
        }
        if (!player.getUUID().equals(room.hostId()) && !privileged) {
            return "§c只有房主或管理员可以开始对局。";
        }
        if (room.size() < 2) {
            return "§c人数不足，至少需要 2 人。";
        }
        return launch(server, room);
    }

    // ---------------- 内部开局 ----------------

    private String launch(MinecraftServer server, ArcadeRoom room) {
        room.setState(ArcadeRoom.State.LAUNCHING);
        List<UUID> members = new ArrayList<>(room.members());

        MatchLauncher.Result result = MatchLauncher.start(
                services, server, room.modeId(), room.arenaId(), members, optionsOf(room));
        if (!result.isOk()) {
            room.setState(ArcadeRoom.State.WAITING);
            notifyAll(server, members, "§c开局失败：" + result.message());
            return "§c开局失败：" + result.message();
        }

        ArcadeMatch match = result.match();
        if (match.canGrow()) {
            // 弹性模式：保留房间为进行中，允许中途补人
            room.setMatchId(match.matchId());
            room.setState(ArcadeRoom.State.IN_PROGRESS);
            notifyAll(server, members, "§a对局开始！祝你好运。");
            closeRoomBrowsers(server, members);
            return "§a对局开始 §7(" + result.message() + ")";
        }
        // 固定模式：回收房间
        rooms.remove(room.roomId());
        for (UUID id : members) {
            roomOf.remove(id);
        }
        notifyAll(server, members, "§a对局开始！祝你好运。");
        closeRoomBrowsers(server, members);
        return "§a对局开始 §7(" + result.message() + ")";
    }

    private void closeRoomBrowsers(MinecraftServer server, Collection<UUID> ids) {
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ArcadeNetwork.closeRoomBrowser(p);
            }
        }
    }

    /**
     * 对局结束回调：回收对应的进行中房间，释放其成员占用。
     */
    public void onMatchEnded(String matchId) {
        if (matchId == null) {
            return;
        }
        ArcadeRoom found = null;
        for (ArcadeRoom room : rooms.values()) {
            if (matchId.equals(room.matchId())) {
                found = room;
                break;
            }
        }
        if (found == null) {
            return;
        }
        for (UUID id : found.members()) {
            roomOf.remove(id);
        }
        rooms.remove(found.roomId());
    }

    private void notifyRoom(MinecraftServer server, ArcadeRoom room, String message) {
        notifyAll(server, room.members(), message);
    }

    private void notifyAll(MinecraftServer server, Collection<UUID> ids, String message) {
        Component component = Component.literal(message);
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(component);
            }
        }
    }

    private String nextRoomId() {
        String idStr;
        do {
            idStr = "r" + (++counter);
        } while (rooms.containsKey(idStr));
        return idStr;
    }
}
