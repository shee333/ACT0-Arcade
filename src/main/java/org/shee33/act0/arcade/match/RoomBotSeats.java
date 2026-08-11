package org.shee33.act0.arcade.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.bot.BotNames;
import org.shee33.act0.arcade.bot.BotSeatPolicy;
import org.shee33.act0.arcade.bot.mc.BotManager;
import org.shee33.act0.arcade.bot.mc.BotPlayer;
import org.shee33.act0.arcade.storage.ArenaRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 房间的 bot 席位操作：在大厅生成/撤走 AI 士兵、切换难度、真人补位时腾位、对局结束后清理。
 *
 * <p>从 {@link RoomManager} 拆出来，是因为后者已承担创建/加入/离开/开局四条主流程，
 * 把 bot 席位再塞进去会让单个类同时负责两种关注点。此处只做"对一个已校验过的房间执行操作"，
 * 房主与状态校验留在 {@link RoomManager} 侧，与它既有的错误文案风格保持一致。
 */
final class RoomBotSeats {

    private RoomBotSeats() {
    }

    /**
     * 在等待中的房间里追加 AI 士兵。
     *
     * <p><b>刻意不开启自主选目标</b>：{@code BotHostility} 对"不在对局中"的判定是混战
     * （除自己以外皆为敌），等待期就开启会让大厅里的 bot 互相开火、连房主一起打。
     * 战斗意图统一由 {@link #enableCombat} 在开局成功后才打开。
     *
     * @return 面向房主的反馈文本
     */
    static String add(MinecraftServer server, ArcadeRoom room, int requested) {
        ArcadeArena arena = ArenaRegistry.get(server).find(room.arenaId()).orElse(null);
        if (arena == null) {
            return "§c竞技场不存在：" + room.arenaId();
        }
        List<String> candidates = BotNames.pick(requested, occupiedNames(server), server.getTickCount());
        int addable = BotSeatPolicy.addableCount(room.size(), room.capacity(), candidates.size());
        if (addable <= 0) {
            return room.isFull() ? "§c房间已满，无法添加 AI 士兵。" : "§cAI 士兵名池已用尽。";
        }
        int added = 0;
        for (int i = 0; i < addable; i++) {
            String name = candidates.get(i);
            SpawnPoint spawn = stagingSpawn(arena, room.botCount());
            if (spawn == null) {
                return "§c竞技场没有可用的出生点。";
            }
            ServerLevel level = TeleportHelper.resolveLevel(server, spawn.dimension());
            if (level == null) {
                return "§c无法解析竞技场维度：" + spawn.dimension();
            }
            BotPlayer bot = BotManager.INSTANCE.spawn(server, level, name,
                    spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            if (bot == null) {
                continue;
            }
            BotManager.INSTANCE.setDifficulty(name, room.botDifficulty());
            room.addBotMember(bot.getUUID());
            added++;
        }
        if (added <= 0) {
            return "§c添加 AI 士兵失败。";
        }
        return "§a已添加 §e" + added + " §a名 AI 士兵 §7(" + room.size() + "/" + room.capacity() + ")";
    }

    /** 从等待中的房间撤走最后加入的 AI 士兵。 */
    static String remove(MinecraftServer server, ArcadeRoom room) {
        if (!BotSeatPolicy.canRemove(room.botCount())) {
            return "§e房间里没有 AI 士兵。";
        }
        UUID victim = pickRemovable(room, null);
        if (victim == null) {
            return "§e房间里没有 AI 士兵。";
        }
        despawn(server, victim);
        room.removeMember(victim);
        return "§a已撤走 1 名 AI 士兵 §7(" + room.size() + "/" + room.capacity() + ")";
    }

    /** 切换本房间难度并立即应用到在场 bot。 */
    static void applyDifficulty(MinecraftServer server, ArcadeRoom room, AimModel.Difficulty difficulty) {
        room.setBotDifficulty(difficulty);
        for (UUID id : room.botIds()) {
            String name = nameOf(server, id);
            if (name != null) {
                BotManager.INSTANCE.setDifficulty(name, difficulty);
            }
        }
    }

    /**
     * 为真人腾出一个席位：踢掉一个 bot（进行中的对局同时将其从对局内摘除）。
     *
     * @param match 进行中的对局；房间尚在等待期时传 {@code null}
     * @return 被踢掉的 bot；无 bot 可踢时返回 {@code null}
     */
    static UUID kickOneForHuman(MinecraftServer server, ArcadeRoom room, ArcadeMatch match) {
        UUID victim = pickRemovable(room, match);
        if (victim == null) {
            return null;
        }
        if (match != null) {
            ServerPlayer bot = server.getPlayerList().getPlayer(victim);
            if (bot != null) {
                match.quitPlayer(bot);
            }
        }
        despawn(server, victim);
        room.removeMember(victim);
        return victim;
    }

    /** 开局成功后开启 bot 的战斗意图，并按房间难度重新校准。 */
    static void enableCombat(MinecraftServer server, Collection<UUID> botIds, AimModel.Difficulty difficulty) {
        for (UUID id : botIds) {
            String name = nameOf(server, id);
            if (name == null) {
                continue;
            }
            BotManager.INSTANCE.setDifficulty(name, difficulty);
            BotManager.INSTANCE.setAutoTarget(name, true);
        }
    }

    /** 批量撤走 bot（房间解散或对局结束）。 */
    static void despawnAll(MinecraftServer server, Collection<UUID> botIds) {
        for (UUID id : botIds) {
            despawn(server, id);
        }
    }

    private static void despawn(MinecraftServer server, UUID id) {
        String name = nameOf(server, id);
        if (name != null) {
            BotManager.INSTANCE.despawn(server, name);
        }
    }

    /**
     * 按 {@link BotSeatPolicy} 选出该被移除的 bot，映射回其 UUID。
     *
     * <p>阵营下标必须逐个向对局查询真实值：房间成员集合的顺序与对局内的分边无对应关系，
     * 用序号推算会让"踢人多那侧"选错边，反而把平衡性推得更歪。
     */
    private static UUID pickRemovable(ArcadeRoom room, ArcadeMatch match) {
        List<UUID> ids = new ArrayList<>(room.botIds());
        if (ids.isEmpty()) {
            return null;
        }
        int[] sideCounts = match == null ? null : sideCountsOf(match, room);
        List<BotSeatPolicy.BotSeat> seats = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            int side = match == null ? BotSeatPolicy.NO_SIDE : match.sideIndexOf(ids.get(i));
            seats.add(new BotSeatPolicy.BotSeat(i, side));
        }
        OptionalInt pick = BotSeatPolicy.pickKickIndex(seats, sideCounts);
        return pick.isPresent() ? ids.get(pick.getAsInt()) : null;
    }

    /** 各阵营当前总人数（含真人），供踢人策略挑"人最多那侧"。 */
    private static int[] sideCountsOf(ArcadeMatch match, ArcadeRoom room) {
        Map<Integer, Integer> counts = new HashMap<>();
        int max = -1;
        for (UUID id : room.members()) {
            int side = match.sideIndexOf(id);
            if (side < 0) {
                continue;
            }
            counts.merge(side, 1, Integer::sum);
            max = Math.max(max, side);
        }
        if (max < 0) {
            return null;
        }
        int[] result = new int[max + 1];
        counts.forEach((side, count) -> result[side] = count);
        return result;
    }

    /**
     * 竞技场内供等待期站位的出生点：优先阵营点，其次乱斗点，最后返回点。
     *
     * <p>等待期的站位只需合法可站，开局时 {@code MatchLauncher} 会把所有成员重新传送到
     * 模式要求的正式出生点，故此处不必与模式匹配。
     */
    private static SpawnPoint stagingSpawn(ArcadeArena arena, int index) {
        List<SpawnPoint> sides = arena.sideSpawns();
        if (!sides.isEmpty()) {
            return sides.get(Math.floorMod(index, sides.size()));
        }
        List<SpawnPoint> random = arena.randomSpawns();
        if (!random.isEmpty()) {
            return random.get(Math.floorMod(index, random.size()));
        }
        return arena.hasReturnSpawn() ? arena.returnSpawn() : null;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().getName() : null;
    }

    private static Set<String> occupiedNames(MinecraftServer server) {
        Set<String> taken = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            taken.add(player.getGameProfile().getName());
        }
        return taken;
    }
}
