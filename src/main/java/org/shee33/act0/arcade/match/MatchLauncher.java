package org.shee33.act0.arcade.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.mode.MatchOptions;
import org.shee33.act0.arcade.mode.MatchSettings;
import org.shee33.act0.arcade.mode.RandomWeaponMode;
import org.shee33.act0.arcade.mode.ScoringMode;
import org.shee33.act0.arcade.round.RespawnPolicy;
import org.shee33.act0.arcade.round.RoundFormat;
import org.shee33.act0.arcade.storage.ArcadeLoadoutStore;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;
import org.shee33.act0.arcade.storage.ArenaRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 对局启动工厂：把"创建并开始一场 {@link ArcadeMatch}"的完整流程收敛到一处，
 * 供命令（{@code /arcade start}）与匹配队列（{@link MatchQueue}）共用，避免逻辑重复。
 *
 * <p>负责竞技场查找、模式构建、人数与场地校验、配装/等级提供器装配、登记到
 * {@link MatchManager} 并启动。所有方法在服务器主线程调用。
 */
public final class MatchLauncher {

    /** 各模式的默认计分目标。 */
    public static final int DEFAULT_TDM_KILLS = 30;
    public static final int DEFAULT_FFA_KILLS = 20;
    public static final int DEFAULT_JUMP_SNIPER_ROUNDS = 5;
    public static final int DEFAULT_HOT_ZONE_SCORE = 150;

    private MatchLauncher() {
    }

    /** 启动结果：成功时带 matchId 与对局引用，失败时带本地化原因文本。 */
    public static final class Result {
        private final boolean ok;
        private final String message;
        private final ArcadeMatch match;

        private Result(boolean ok, String message, ArcadeMatch match) {
            this.ok = ok;
            this.message = message;
            this.match = match;
        }

        public static Result ok(ArcadeMatch match) {
            return new Result(true, match.matchId(), match);
        }

        public static Result fail(String reason) {
            return new Result(false, reason, null);
        }

        public boolean isOk() {
            return ok;
        }

        /** 成功时为 matchId，失败时为原因。 */
        public String message() {
            return message;
        }

        /** 成功时为对局实例，失败时为 {@code null}。 */
        public ArcadeMatch match() {
            return match;
        }
    }

    /** 把模式 id 构建为 {@link MatchSettings}（全默认选项）。未知模式返回 {@code null}。 */
    public static MatchSettings buildSettings(String mode, int sizingCount) {
        return buildSettings(mode, sizingCount, MatchOptions.defaults());
    }

    /** 把模式 id 构建为 {@link MatchSettings}，可选地覆盖计分目标。 */
    public static MatchSettings buildSettings(String mode, int sizingCount, Integer winTarget) {
        return buildSettings(mode, sizingCount, new MatchOptions(winTarget, 0, false));
    }

    /**
     * 把模式 id 构建为 {@link MatchSettings}，应用完整可配置项（计分目标、限时、随机武器）。
     *
     * @param sizingCount 用于决定弹性模式的参战方数/队伍规模（通常传房间容量）
     * @param options     房间可配置项；{@code null} 视为默认
     * @return 模式设置；未知模式返回 {@code null}
     */
    public static MatchSettings buildSettings(String mode, int sizingCount, MatchOptions options) {
        if (options == null) {
            options = MatchOptions.defaults();
        }
        Integer wt = options.winTarget();
        MatchSettings.Builder b;
        switch (mode) {
            case "duel_1v1" -> b = MatchSettings.builder("duel_1v1", "单挑 1v1")
                    .sideCount(2).teamSize(1)
                    .scoringMode(ScoringMode.ROUND_WIN)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : 3))
                    .respawnPolicy(RespawnPolicy.FIXED_SPAWN);
            case "duel_2v2" -> b = MatchSettings.builder("duel_2v2", "2v2")
                    .sideCount(2).teamSize(2)
                    .scoringMode(ScoringMode.ROUND_WIN)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : 3))
                    .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE);
            case "team_deathmatch" -> b = MatchSettings.builder("team_deathmatch", "团队死斗")
                    .sideCount(2).teamSize(Math.max(1, sizingCount / 2))
                    .scoringMode(ScoringMode.KILL_COUNT)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : DEFAULT_TDM_KILLS))
                    .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE);
                case "hot_zone" -> b = MatchSettings.builder("hot_zone", "热区")
                    .sideCount(2).teamSize(Math.max(1, sizingCount / 2))
                    .scoringMode(ScoringMode.HOT_ZONE)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : DEFAULT_HOT_ZONE_SCORE))
                    .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE);
            case "free_for_all" -> b = MatchSettings.builder("free_for_all", "个人乱斗")
                    .sideCount(Math.max(2, sizingCount)).teamSize(1)
                    .scoringMode(ScoringMode.KILL_COUNT)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : DEFAULT_FFA_KILLS))
                    .respawnPolicy(RespawnPolicy.RANDOM);
            case "jump_sniper" -> b = MatchSettings.builder("jump_sniper", "跳狙飞人")
                    .sideCount(2).teamSize(Math.max(1, sizingCount / 2))
                    .scoringMode(ScoringMode.ROUND_WIN)
                    .roundFormat(RoundFormat.firstTo(wt != null ? Math.max(1, wt) : DEFAULT_JUMP_SNIPER_ROUNDS))
                    .respawnPolicy(RespawnPolicy.FIXED_SPAWN)
                    .randomMode(RandomWeaponMode.SNIPER);
            default -> {
                return null;
            }
        }
        return b.timeLimitSeconds(options.timeLimitSeconds())
            .randomMode("jump_sniper".equals(mode) ? RandomWeaponMode.SNIPER : options.randomMode())
                .build();
    }

    /** 某模式在给定在场人数下所需的总参战人数；未知模式返回 {@code -1}。 */
    public static int requiredPlayers(String mode, int hintCount) {
        MatchSettings settings = buildSettings(mode, Math.max(2, hintCount));
        return settings != null ? settings.totalPlayers() : -1;
    }

    /**
     * 创建并开始一场对局（全默认选项）。
     *
     * @param services 街机服务（注册表/应用器/对局管理器）
     * @param server   服务器实例
     * @param mode     模式 id
     * @param arenaId  竞技场 id
     * @param players  参战玩家
     */
    public static Result start(ArcadeServices services,
                               MinecraftServer server,
                               String mode,
                               String arenaId,
                               List<UUID> players) {
        return start(services, server, mode, arenaId, players, MatchOptions.defaults());
    }

    /** 创建并开始一场对局，仅覆盖计分目标。 */
    public static Result start(ArcadeServices services,
                               MinecraftServer server,
                               String mode,
                               String arenaId,
                               List<UUID> players,
                               Integer winTarget) {
        return start(services, server, mode, arenaId, players, new MatchOptions(winTarget, 0, false));
    }

    /**
     * 创建并开始一场对局，应用完整可配置项。
     *
     * <p>弹性模式（击杀制）允许以 [2, 容量] 区间人数开局并预留空位供中途补人；
     * 回合制决斗模式要求满员。参战方按容量预分配。
     */
    public static Result start(ArcadeServices services,
                               MinecraftServer server,
                               String mode,
                               String arenaId,
                               List<UUID> players,
                               MatchOptions options) {
        int defaultCapacity = Math.max(2, MatchQueue.targetSize(mode));
        return start(services, server, mode, arenaId, players, options, defaultCapacity);
    }

    public static Result start(ArcadeServices services,
                               MinecraftServer server,
                               String mode,
                               String arenaId,
                               List<UUID> players,
                               MatchOptions options,
                               int sizingCount) {
        Optional<ArcadeArena> arenaOpt = ArenaRegistry.get(server).find(arenaId);
        if (arenaOpt.isEmpty()) {
            return Result.fail("竞技场不存在：" + arenaId);
        }
        if (services.matches().isArenaInUse(arenaId)) {
            return Result.fail("竞技场正在对局中：" + arenaId);
        }
        ArcadeArena arena = arenaOpt.get();

        for (UUID id : players) {
            if (services.matches().isInMatch(id)) {
                return Result.fail("玩家 " + nameOf(server, id) + " 已在对局中。");
            }
        }

        int capacity = Math.max(2, sizingCount);
        MatchSettings settings = buildSettings(mode, capacity, options);
        if (settings == null) {
            return Result.fail("未知模式：" + mode);
        }
        int cap = settings.totalPlayers();
        if (settings.flexible()) {
            if (players.size() < 2 || players.size() > cap) {
                return Result.fail("模式 " + settings.displayName() + " 需要 2~"
                        + cap + " 名玩家，当前 " + players.size() + "。");
            }
        } else if (players.size() != cap) {
            return Result.fail("模式 " + settings.displayName() + " 需要 "
                    + cap + " 名玩家，当前 " + players.size() + "。");
        }

        ArcadeArena.Validation validation = arena.validateFor(settings.respawnPolicy(), settings.sideCount());
        if (!validation.isValid()) {
            return Result.fail("竞技场不满足要求：" + validation.reason());
        }
        if (settings.respawnPolicy() == org.shee33.act0.arcade.round.RespawnPolicy.RANDOM
                && arena.randomSpawns().size() < players.size()) {
            return Result.fail("个人乱斗复活点不足：需要至少 " + players.size()
                    + " 个，当前 " + arena.randomSpawns().size() + " 个。");
        }
        if ("hot_zone".equals(mode) && arena.randomSpawns().isEmpty()) {
            return Result.fail("热区模式需要至少 1 个热区点，请使用 /arcade arena addhotzone 录入。");
        }

        String matchId = mode + "-" + UUID.randomUUID().toString().substring(0, 8);
        Function<UUID, Loadout> loadoutProvider = id -> ArcadeLoadoutStore.get(server)
                .getOrCreate(id, DefaultLoadoutCatalog.defaultLoadout(services.registry()));
        Function<UUID, java.util.Set<String>> unlocksProvider = id ->
                ArcadePlayerUnlocks.get(server).unlocked(id);

        ArcadeMatch match = new ArcadeMatch(matchId, settings, arena, server,
                services.applier(), services.registry(), loadoutProvider, unlocksProvider);
        services.matches().register(match, players);
        match.start(players);
        return Result.ok(match);
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8);
    }
}
