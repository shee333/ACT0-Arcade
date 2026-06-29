package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;
import org.shee33.act0.arcade.integration.MatchResultBroadcaster;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.loadout.WeaponCategory;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.mode.MatchSettings;
import org.shee33.act0.arcade.mode.RandomWeaponMode;
import org.shee33.act0.arcade.mode.ScoringMode;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.round.MatchPhase;
import org.shee33.act0.arcade.round.MatchScore;
import org.shee33.act0.arcade.round.PhaseTimer;
import org.shee33.act0.arcade.round.RespawnPolicy;
import org.shee33.act0.arcade.storage.ArcadeApparelStore;
import org.shee33.act0.arcade.storage.ArcadeGlobalSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 通用街机对局运行时（Minecraft 层）：由单一编排逻辑驱动全部四种模式，差异完全来自注入的
 * {@link MatchSettings}。
 *
 * <p>职责：
 * <ul>
 *   <li>分组、传送出生、发放配装（经 {@link LoadoutApplier}，街机规则集无缝禁用兵种道具）。</li>
 *   <li>推进 {@link MatchPhase} 生命周期（倒计时→战斗→结算→重装→下一回合）。</li>
 *   <li>处理击杀/死亡：决斗模式按"团灭判回合"，死斗模式按"击杀计分 + 即时复活"。</li>
 *   <li>判定赛点并收尾。</li>
 * </ul>
 *
 * <p>线程模型：所有方法均在服务器主线程（游戏循环）调用，非线程安全。
 */
public final class ArcadeMatch {

    private final String matchId;
    private final MatchSettings settings;
    private final ArcadeArena arena;
    private final MinecraftServer server;
    private final LoadoutApplier applier;
    private final LoadoutRegistry registry;
    private final Function<UUID, Loadout> loadoutProvider;
    private final Function<UUID, Set<String>> unlocksProvider;

    private final MatchScore score;
    private final PhaseTimer timer = new PhaseTimer();
    private final Random random = new Random();

    /** 头顶 Boss 血条：集中显示对局阶段、比分与倒计时进度。 */
    private final ServerBossEvent bossBar;

    /** 侧边栏计分板：显示各方比分与赛制信息。 */
    private final MatchScoreboard sidebar;

    /** 方索引 → 成员（保持加入顺序）。 */
    private final List<Set<UUID>> sides = new ArrayList<>();
    private final Map<UUID, Integer> sideOf = new HashMap<>();
    /** 当前回合存活者（仅决斗模式用于团灭判定）。 */
    private final Set<UUID> alive = new LinkedHashSet<>();
    /** 死斗模式的待复活计时（玩家 → 剩余刻）。 */
    private final Map<UUID, PhaseTimer> respawnTimers = new HashMap<>();
    /** 已掉线但保留坐位的玩家（重连即可归位）。 */
    private final Set<UUID> disconnected = new LinkedHashSet<>();
    /** 个人乱斗实时计分用：玩家 → 击杀数。 */
    private final Map<UUID, Integer> kills = new HashMap<>();
    /** 死亡进入观察者前记录的原始游戏模式，复活时还原。 */
    private final Map<UUID, GameType> originalGameMode = new HashMap<>();
    /** 进入对局前的游戏模式，对局结束时尽量恢复。 */
    private final Map<UUID, GameType> preMatchGameMode = new HashMap<>();
    /** 复活倒计时上次播报的整秒，用于逐秒 title + 音符盒去重（按玩家）。 */
    private final Map<UUID, Integer> respawnLastSecond = new HashMap<>();
    /** 开局倒计时期间锁定的位置（防乱跑）。 */
    private final Map<UUID, Vec3> countdownLock = new HashMap<>();
    /** 死亡观战期间锁定相机的位置与朝向（钉在死亡点，禁止自由飞）。 */
    private final Map<UUID, DeathView> deathCamView = new HashMap<>();
    /** 死者 → 其死亡相机正盯着的击杀者（用于持续追踪朝向与高亮发光管理）。 */
    private final Map<UUID, UUID> deathCamKiller = new HashMap<>();
    /** 2v2 阵亡后延迟切到队友视角的 tick。 */
    private final Map<UUID, Long> teammateSpectateSwitchTick = new HashMap<>();
    /** 当前正在借用队友相机的死者 → 队友。 */
    private final Map<UUID, UUID> teammateSpectateTarget = new HashMap<>();
    /** 玩家最近一次受伤 tick，用于街机“呼吸回血”。 */
    private final Map<UUID, Long> lastHurtTick = new HashMap<>();
    /** 跳狙飞人蓄力跳：玩家 → 当前蓄力 tick。 */
    private final Map<UUID, Integer> jumpChargeTicks = new HashMap<>();
    /** 跳狙飞人蓄力跳：玩家 → 冷却结束 tick。 */
    private final Map<UUID, Long> jumpChargeCooldownUntil = new HashMap<>();
    /** 跳狙飞人蓄力跳：玩家当前是否按住蓄力键。 */
    private final Set<UUID> jumpChargeInput = new LinkedHashSet<>();
    /** 本对局生成的弹药补给箱实体 UUID，对局结束时清理。 */
    private final Set<UUID> ammoCrates = new LinkedHashSet<>();
    /** 本对局创建的原版计分板队伍：用于隐藏敌方头顶名、保留队友头顶名。 */
    private final List<PlayerTeam> nameTagTeams = new ArrayList<>();

    /** 死亡补给箱标记 NBT 键：拾取后补满虚拟弹药。 */
    public static final String AMMO_CRATE_KEY = "Act0AmmoCrate";
    private static final double AMMO_CRATE_DROP_CHANCE = 0.25D;
    private static final double RESPAWN_CLEAR_RADIUS = 10.0;
    private static final double TEAMMATE_RESPAWN_MIN_RADIUS = 3.0;
    private static final double TEAMMATE_RESPAWN_MAX_RADIUS = 5.0;
    private static final double TEAMMATE_COMBAT_RADIUS = 12.0;
    private static final double TEAMMATE_RESPAWN_ENEMY_CLEAR_RADIUS = 8.0;
    private static final int TEAMMATE_RECENT_COMBAT_TICKS = 5 * 20;
    private static final int JUMP_SNIPER_EFFECT_TICKS = 90;
    private static final int JUMP_CHARGE_MIN_TICKS = 8;
    private static final int JUMP_CHARGE_MAX_TICKS = 35;
    private static final int JUMP_CHARGE_COOLDOWN_TICKS = 12;
    private static final double JUMP_CHARGE_NORMAL_VERTICAL = 0.82D;
    private static final double JUMP_CHARGE_MIN_VERTICAL = 0.72D;
    private static final double JUMP_CHARGE_MAX_VERTICAL = 1.35D;
    private static final double JUMP_CHARGE_MIN_HORIZONTAL = 0.35D;
    private static final double JUMP_CHARGE_MAX_HORIZONTAL = 1.05D;
    private static final int HOT_ZONE_ROTATE_TICKS = 60 * 20;
    private static final double HOT_ZONE_RADIUS = 6.0D;
    private static final double HOT_ZONE_HEIGHT = 4.0D;
        private static final ChatFormatting[] FFA_COLORS = new ChatFormatting[]{
            ChatFormatting.RED,
            ChatFormatting.BLUE,
            ChatFormatting.GREEN,
            ChatFormatting.YELLOW,
            ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD,
            ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.DARK_RED,
            ChatFormatting.DARK_BLUE,
            ChatFormatting.GRAY,
            ChatFormatting.WHITE
        };

    private MatchPhase phase = MatchPhase.WAITING;
    private int winnerSide = -1;
    /** 倒计时/复活阶段的总时长（刻），用于 Boss 血条进度归一化。 */
    private int phaseTotalTicks = 1;
    /** 倒计时阶段上次播报的整秒数，用于逐秒 3-2-1 提示去重。 */
    private int lastCountdownSecond = -1;
    /** 对局限时剩余刻（{@code <0} 表示不限时）。 */
    private int matchClockTicks = -1;
    /** 当前热区索引（复用竞技场 randomSpawns 作为热区点）。 */
    private int hotZoneIndex = -1;
    /** 当前热区剩余 tick。 */
    private int hotZoneTicksRemaining = 0;
    private long startedTick;
    private boolean draw = false;
    private final Map<UUID, ServerBossEvent> personalBossBars = new HashMap<>();

    public ArcadeMatch(String matchId,
                       MatchSettings settings,
                       ArcadeArena arena,
                       MinecraftServer server,
                       LoadoutApplier applier,
                       LoadoutRegistry registry,
                       Function<UUID, Loadout> loadoutProvider,
                       Function<UUID, Set<String>> unlocksProvider) {
        this.matchId = matchId;
        this.settings = settings;
        this.arena = arena;
        this.server = server;
        this.applier = applier;
        this.registry = registry;
        this.loadoutProvider = loadoutProvider;
        this.unlocksProvider = unlocksProvider;
        this.score = new MatchScore(settings.roundFormat());
        for (int i = 0; i < settings.sideCount(); i++) {
            sides.add(new LinkedHashSet<>());
            score.registerSide(sideId(i));
        }
        this.bossBar = new ServerBossEvent(
                Component.literal("§6" + settings.displayName()),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setVisible(false);
        this.sidebar = new MatchScoreboard("act0_" + Integer.toHexString(matchId.hashCode()),
                Component.literal("§e§l" + settings.displayName()));
    }

    // ---- 生命周期 ----

    /**
     * 按方分配玩家并开始对局。
     *
     * <p>弹性模式（击杀制）允许 {@code 2 <= players.size() <= totalPlayers()}，按顺序填入各方并预留空位；
     * 回合制决斗模式要求恰好满员。
     */
    public void start(List<UUID> players) {
        int cap = settings.totalPlayers();
        if (settings.flexible()) {
            if (players.size() < 2 || players.size() > cap) {
                throw new IllegalArgumentException("弹性模式需要 2~" + cap + " 名玩家，收到 " + players.size());
            }
        } else if (players.size() != cap) {
            throw new IllegalArgumentException("需要 " + cap + " 名玩家，收到 " + players.size());
        }
        int idx = 0;
        for (UUID id : players) {
            int side = Math.min(idx / settings.teamSize(), settings.sideCount() - 1);
            sides.get(side).add(id);
            sideOf.put(id, side);
            kills.put(id, 0);
            idx++;
        }
        for (UUID id : sideOf.keySet()) {
            ServerPlayer player = player(id);
            if (player != null) {
                rememberPreMatchMode(player);
                addBossBarPlayer(player);
                sidebar.showTo(player);
            }
        }
        setupNameTagTeams();
        startedTick = server.getTickCount();
        bossBar.setVisible(!usesPersonalBossBars());
        if (settings.timeLimitSeconds() > 0) {
            matchClockTicks = settings.timeLimitSeconds() * 20;
        }
        updateSidebar();
        startRound();
    }

    /** 开始（或重开）一个回合：满血归位、发配装、进入倒计时。 */
    private void startRound() {
        alive.clear();
        respawnTimers.clear();
        respawnLastSecond.clear();
        countdownLock.clear();
        jumpChargeTicks.clear();
        jumpChargeCooldownUntil.clear();
        jumpChargeInput.clear();
        for (UUID id : sideOf.keySet()) {
            if (disconnected.contains(id)) {
                continue; // 掉线者保留坐位但不参与本回合，重连后归位
            }
            alive.add(id);
            ServerPlayer player = player(id);
            if (player == null) {
                continue;
            }
            exitSpectator(player);
            enterAdventureMode(player);
            spawnForRound(player, id, sideOf.get(id));
            equip(player);
            applyGlobalHealth(player);
            applyCountdownLock(player);
        }
        phase = MatchPhase.COUNTDOWN;
        phaseTotalTicks = Math.max(1, settings.countdownSeconds() * 20);
        lastCountdownSecond = -1;
        resetHotZone();
        timer.startSeconds(settings.countdownSeconds());
        sendFireLockToAll(true);
        showTitle("§e准备", "§7" + roundLabel() + " · 倒计时 " + settings.countdownSeconds() + " 秒", 5, 30, 10);
        updateBossBar();
        updateSidebar();
        broadcast("§e对局开始，倒计时 " + settings.countdownSeconds() + " 秒…");
    }

    /** 每服务器刻调用一次，推进定时阶段。 */
    public void tick() {
        switch (phase) {
            case COUNTDOWN -> {
                enforceCountdownLock();
                tickJumpSniperEffects();
                syncTeamHighlights();
                int secs = timer.remainingSeconds();
                if (secs != lastCountdownSecond) {
                    lastCountdownSecond = secs;
                    if (secs > 0 && secs <= 3) {
                        showTitle("§e§l" + secs, "", 0, 14, 6);
                        playToAll(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f + (3 - secs) * 0.25f);
                    }
                }
                if (timer.tick()) {
                    phase = MatchPhase.COMBAT;
                    releaseCountdownLock();
                    sendFireLockToAll(false);
                    showTitle("§a§l战斗开始！", "", 2, 20, 8);
                    playToAll(SoundEvents.PLAYER_LEVELUP, 1.0f);
                    broadcast("§a战斗开始！");
                }
                updateBossBar();
            }
            case COMBAT -> {
                enforceDeathCam();
                tickTeammateSpectate();
                syncTeamHighlights();
                tickJumpSniperEffects();
                tickJumpCharge();
                tickRespawns();
                tickBreathHealing();
                tickHotZone();
                if (phase != MatchPhase.COMBAT) {
                    return;
                }
                if (tickMatchClock()) {
                    return; // 限时到，已收尾
                }
                updateBossBar();
            }
            case ROUND_RESULT -> {
                enforceDeathCam();
                tickTeammateSpectate();
                int secs = timer.remainingSeconds();
                if (secs != lastCountdownSecond) {
                    lastCountdownSecond = secs;
                    if (secs > 0 && secs <= 3) {
                        showTitle("§e§l新回合 " + secs, "", 0, 14, 6);
                        playToAll(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f + (3 - secs) * 0.2f);
                    }
                }
                if (timer.tick()) {
                    startRound();
                }
            }
            case MATCH_RESULT -> {
                if (timer.tick()) {
                    finish();
                }
            }
            default -> {
            }
        }
    }

    private void tickRespawns() {
        if (respawnTimers.isEmpty()) {
            return;
        }
        List<UUID> ready = new ArrayList<>();
        for (Map.Entry<UUID, PhaseTimer> e : respawnTimers.entrySet()) {
            UUID id = e.getKey();
            PhaseTimer t = e.getValue();
            int secs = t.remainingSeconds();
            Integer last = respawnLastSecond.get(id);
            if (last == null || last != secs) {
                respawnLastSecond.put(id, secs);
                if (secs > 0) {
                    showRespawnCountdown(id, secs);
                }
            }
            if (t.tick()) {
                ready.add(id);
            }
        }
        for (UUID id : ready) {
            respawnTimers.remove(id);
            respawnLastSecond.remove(id);
            ServerPlayer player = player(id);
            if (player == null) {
                continue;
            }
            exitSpectator(player);
            enterAdventureMode(player);
            clearJumpCharge(id);
            setupNameTagTeams();
            respawn(player, sideOf.getOrDefault(id, 0));
            equip(player);
            applyGlobalHealth(player);
            showTitleTo(id, "§a§l重生", "", 0, 12, 6);
            playTo(id, SoundEvents.PLAYER_LEVELUP, 1.2f);
        }
    }

    /** 死亡观战期间逐秒倒计时 title + 音符盒音效（向单个玩家）。 */
    private void showRespawnCountdown(UUID id, int secs) {
        showTitleTo(id, "§c§l" + secs, "§7阵亡 · 即将重生", 0, 22, 6);
        playTo(id, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f + (3 - Math.min(3, secs)) * 0.2f);
    }

    /**
     * 推进对局限时（仅战斗阶段倒数）。到时按比分领先者判胜，平分则平局。
     *
     * @return 是否已因到时收尾（收尾后调用方应停止本 tick 后续处理）
     */
    private boolean tickMatchClock() {
        if (matchClockTicks < 0) {
            return false;
        }
        int before = matchClockTicks;
        matchClockTicks--;
        if (matchClockTicks <= 0) {
            matchClockTicks = 0;
            onTimeExpired();
            return true;
        }
        // 每跨过一个整秒刷新侧边栏并在最后 10 秒制造紧迫感
        if (before / 20 != matchClockTicks / 20) {
            updateSidebar();
            int secsLeft = matchClockTicks / 20 + 1;
            if (secsLeft <= 10) {
                String color = secsLeft <= 5 ? "§c§l" : "§e§l";
                showTitle(color + secsLeft, "§7剩余时间", 0, 24, 6);
                playToAll(SoundEvents.NOTE_BLOCK_HAT.value(),
                        secsLeft <= 5 ? 1.6f : 1.0f);
            }
        }
        return false;
    }

    /** 限时到：按当前比分领先者判胜；并列最高则平局。 */
    private void onTimeExpired() {
        broadcast("§e时间到！");
        int leadIndex = -1;
        int leadScore = -1;
        boolean tie = false;
        for (int i = 0; i < settings.sideCount(); i++) {
            int s = score.score(sideId(i));
            if (s > leadScore) {
                leadScore = s;
                leadIndex = i;
                tie = false;
            } else if (s == leadScore) {
                tie = true;
            }
        }
        if (tie || leadIndex < 0) {
            finishDraw();
        } else {
            winMatch(leadIndex);
        }
    }

    private boolean isHotZoneMode() {
        return settings.scoringMode() == ScoringMode.HOT_ZONE;
    }

    private void resetHotZone() {
        if (!isHotZoneMode()) {
            hotZoneIndex = -1;
            hotZoneTicksRemaining = 0;
            return;
        }
        hotZoneIndex = 0;
        hotZoneTicksRemaining = HOT_ZONE_ROTATE_TICKS;
    }

    private void rotateHotZone() {
        List<SpawnPoint> zones = arena.randomSpawns();
        if (zones.isEmpty()) {
            return;
        }
        hotZoneIndex = hotZoneIndex < 0 ? 0 : (hotZoneIndex + 1) % zones.size();
        hotZoneTicksRemaining = HOT_ZONE_ROTATE_TICKS;
        SpawnPoint zone = zones.get(hotZoneIndex);
        broadcast("§6热区转移 §7» §e#" + (hotZoneIndex + 1)
                + " §8(" + Math.round(zone.x()) + ", " + Math.round(zone.y()) + ", " + Math.round(zone.z()) + ")");
        showTitle("§6热区转移", "§7前往新目标区域", 4, 28, 8);
        playToAll(SoundEvents.NOTE_BLOCK_BELL.value(), 1.15f);
        updateSidebar();
    }

    private void tickHotZone() {
        if (!isHotZoneMode() || phase != MatchPhase.COMBAT) {
            return;
        }
        List<SpawnPoint> zones = arena.randomSpawns();
        if (zones.isEmpty()) {
            return;
        }
        if (hotZoneIndex < 0 || hotZoneIndex >= zones.size()) {
            resetHotZone();
        }
        hotZoneTicksRemaining--;
        if (hotZoneTicksRemaining <= 0) {
            rotateHotZone();
            return;
        }
        if (server.getTickCount() % 20L != 0L) {
            return;
        }
        int controllingSide = hotZoneController(zones.get(hotZoneIndex));
        if (controllingSide < 0) {
            updateSidebar();
            return;
        }
        int total = score.addPoint(sideId(controllingSide));
        actionBarSide(controllingSide, "§6热区占领 §7+1 §8(" + total + "/" + score.pointsToWin() + ")");
        updateBossBar();
        updateSidebar();
        if (score.isReached()) {
            winMatch(controllingSide);
        }
    }

    /**
     * @return 唯一控制热区的方；无人或多方争夺返回 -1
     */
    private int hotZoneController(SpawnPoint zone) {
        boolean[] present = new boolean[settings.sideCount()];
        int count = 0;
        for (Map.Entry<UUID, Integer> e : sideOf.entrySet()) {
            ServerPlayer p = player(e.getKey());
            if (p == null || !p.isAlive() || p.isSpectator() || respawnTimers.containsKey(e.getKey())
                    || deathCamView.containsKey(e.getKey())) {
                continue;
            }
            if (!isInsideHotZone(p, zone)) {
                continue;
            }
            int side = e.getValue();
            if (side >= 0 && side < present.length && !present[side]) {
                present[side] = true;
                count++;
            }
        }
        if (count != 1) {
            return -1;
        }
        for (int i = 0; i < present.length; i++) {
            if (present[i]) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInsideHotZone(ServerPlayer player, SpawnPoint zone) {
        ServerLevel zoneLevel = TeleportHelper.resolveLevel(server, zone.dimension());
        if (zoneLevel == null || player.level() != zoneLevel) {
            return false;
        }
        double dx = player.getX() - zone.x();
        double dz = player.getZ() - zone.z();
        double dy = Math.abs(player.getY() - zone.y());
        return dx * dx + dz * dz <= HOT_ZONE_RADIUS * HOT_ZONE_RADIUS && dy <= HOT_ZONE_HEIGHT;
    }

    private void actionBarSide(int side, String message) {
        if (side < 0 || side >= sides.size()) {
            return;
        }
        for (UUID id : sides.get(side)) {
            actionBar(id, message);
        }
    }

    /**
     * 处理一名参战玩家的死亡。
     *
     * <p>对局内死亡<b>不走原版死亡流程</b>（避免重生换 {@link ServerPlayer} 实例导致血条/计分引用失效，
     * 也避免被弹回世界出生点而脱离竞技场），由 {@link MatchManager} 取消死亡事件。这里对任何非结束阶段
     * 的参战玩家都立即满血以阻止原版死亡；仅在战斗阶段才按模式处理击倒/复活/淘汰与计分。
     *
     * @param victimId 死者
     * @param killerId 击杀者（可为 {@code null}，如自杀/环境伤害）
     * @return 是否由本对局接管了该死亡（调用方据此取消原版死亡）
     */
    public boolean onDeath(UUID victimId, UUID killerId) {
        if (phase == MatchPhase.ENDED || !sideOf.containsKey(victimId)) {
            return false;
        }
        lastHurtTick.put(victimId, (long) server.getTickCount());
        ServerPlayer victim = player(victimId);
        // 仅对局战斗阶段：25% 概率在死亡点掉落补给箱（拾取恢复弹药），且必须在传送前掉落。
        if (victim != null && phase == MatchPhase.COMBAT) {
            dropAmmoCrate(victim);
        }
        // 立即复活以阻止原版死亡：保持同一 ServerPlayer 实例，血条/计分引用始终有效。
        if (victim != null) {
            applyGlobalHealth(victim);
            victim.removeAllEffects();
            victim.clearFire();
            victim.getFoodData().setFoodLevel(20);
        }
        // 非战斗阶段（准备/倒计时/回合或对局结算）：仅阻止死亡，不计分、不触发复活流程。
        if (phase != MatchPhase.COMBAT) {
            return true;
        }
        if (settings.scoringMode() == ScoringMode.KILL_COUNT) {
            handleKillScoring(victimId, killerId);
        } else if (settings.scoringMode() == ScoringMode.HOT_ZONE) {
            handleHotZoneDeath(victimId, killerId);
        } else {
            handleRoundElimination(victimId, killerId);
        }
        return true;
    }

    public void onHurt(UUID playerId) {
        if (!sideOf.containsKey(playerId) || phase != MatchPhase.COMBAT) {
            return;
        }
        lastHurtTick.put(playerId, (long) server.getTickCount());
        ServerPlayer p = player(playerId);
        if (p != null) {
            p.removeEffect(MobEffects.REGENERATION);
        }
    }

    /** 取消同方玩家之间的伤害，避免团队模式误伤。 */
    public boolean shouldCancelDamage(UUID victimId, UUID attackerId) {
        if (phase == MatchPhase.ENDED || !sideOf.containsKey(victimId)) {
            return false;
        }
        if (phase == MatchPhase.COUNTDOWN || respawnTimers.containsKey(victimId) || deathCamView.containsKey(victimId)) {
            return true;
        }
        if (attackerId == null || attackerId.equals(victimId) || !sideOf.containsKey(attackerId)) {
            return false;
        }
        Integer victimSide = sideOf.get(victimId);
        Integer attackerSide = sideOf.get(attackerId);
        return victimSide != null && victimSide.equals(attackerSide);
    }

    private void tickBreathHealing() {
        int delay = ArcadeGlobalSettings.get(server).breathHealDelayTicks();
        long now = server.getTickCount();
        for (UUID id : sideOf.keySet()) {
            if (respawnTimers.containsKey(id) || deathCamView.containsKey(id)) {
                continue;
            }
            ServerPlayer p = player(id);
            if (p == null || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            if (p.getHealth() >= p.getMaxHealth()) {
                p.removeEffect(MobEffects.REGENERATION);
                continue;
            }
            long last = lastHurtTick.getOrDefault(id, now);
            if (now - last >= delay) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false, true));
            }
        }
    }

    /** 在玩家死亡点掉落一个补给箱（箱子物品 + 标记 NBT），拾取后补满虚拟弹药。 */
    private void dropAmmoCrate(ServerPlayer victim) {
        if (random.nextDouble() >= AMMO_CRATE_DROP_CHANCE) {
            return;
        }
        ItemStack crate = new ItemStack(Items.CHEST);
        crate.getOrCreateTag().putBoolean(AMMO_CRATE_KEY, true);
        crate.setHoverName(Component.literal("§b补给箱 §7(拾取恢复弹药)"));
        ItemEntity entity = new ItemEntity(victim.serverLevel(),
                victim.getX(), victim.getY() + 0.3, victim.getZ(), crate);
        entity.setDeltaMovement(0.0, 0.08, 0.0);
        entity.setGlowingTag(true);
        entity.setUnlimitedLifetime();
        entity.setDefaultPickUpDelay();
        victim.serverLevel().addFreshEntity(entity);
        ammoCrates.add(entity.getUUID());
    }

    private void handleKillScoring(UUID victimId, UUID killerId) {
        if (killerId != null && sideOf.containsKey(killerId)
                && !sideOf.get(killerId).equals(sideOf.get(victimId))) {
            int killerSide = sideOf.get(killerId);
            int total = score.addPoint(sideId(killerSide));
            kills.merge(killerId, 1, Integer::sum);
                broadcast(sideColor(killerSide) + nameOf(killerId) + " §7击杀了 "
                    + sideColor(sideOf.getOrDefault(victimId, -1)) + nameOf(victimId)
                    + " §7(" + sideLabel(killerSide) + ": " + total + "/" + score.pointsToWin() + ")");
            actionBar(killerId, "§a+1 击杀 §7(" + total + "/" + score.pointsToWin() + ")");
            playTo(killerId, SoundEvents.ARROW_HIT_PLAYER, 1.0f);
            updateBossBar();
            updateSidebar();
            if (score.isReached()) {
                winMatch(killerSide);
                return;
            }
        }
        // 击倒：进入观察者视角等待重生，倒计时结束后再发配装满血回到战场。
        ServerPlayer victim = player(victimId);
        if (victim != null) {
            victim.getInventory().clearContent();
            enterSpectator(victim, killerId);
        }
        PhaseTimer t = new PhaseTimer();
        t.startSeconds(settings.reEquipProtectionSeconds());
        respawnTimers.put(victimId, t);
        respawnLastSecond.put(victimId, -1);
    }

    private void handleHotZoneDeath(UUID victimId, UUID killerId) {
        if (killerId != null && sideOf.containsKey(killerId)
                && !sideOf.get(killerId).equals(sideOf.get(victimId))) {
            kills.merge(killerId, 1, Integer::sum);
            int killerSide = sideOf.get(killerId);
            broadcast(sideColor(killerSide) + nameOf(killerId) + " §7击杀了 "
                    + sideColor(sideOf.getOrDefault(victimId, -1)) + nameOf(victimId));
            actionBar(killerId, "§a击杀 §7· 继续控制热区");
            playTo(killerId, SoundEvents.ARROW_HIT_PLAYER, 1.0f);
        }
        ServerPlayer victim = player(victimId);
        if (victim != null) {
            victim.getInventory().clearContent();
            enterSpectator(victim, killerId);
        }
        PhaseTimer t = new PhaseTimer();
        t.startSeconds(settings.reEquipProtectionSeconds());
        respawnTimers.put(victimId, t);
        respawnLastSecond.put(victimId, -1);
    }

    private void handleRoundElimination(UUID victimId) {
        handleRoundElimination(victimId, null);
    }

    private void handleRoundElimination(UUID victimId, UUID killerId) {
        alive.remove(victimId);
        clearJumpCharge(victimId);
        ServerPlayer victim = player(victimId);
        if (victim != null) {
            victim.getInventory().clearContent();
            clearTeamHighlightFor(victim);
            clearTeamHighlightTarget(victim);
            if (isJumpSniper()) {
                enterFreeSpectator(victim);
            } else {
                enterSpectator(victim, killerId);
            }
            scheduleTeammateSpectate(victimId);
            actionBar(victimId, "§c阵亡 · 等待本回合结束");
        }
        evaluateRoundElimination();
    }

    /** 根据当前存活情况判定回合是否结束（仅决斗类模式调用）。 */
    private void evaluateRoundElimination() {
        Set<Integer> livingSides = new LinkedHashSet<>();
        for (UUID id : alive) {
            livingSides.add(sideOf.get(id));
        }
        if (livingSides.size() > 1) {
            return;
        }
        int roundWinner = livingSides.isEmpty() ? -1 : livingSides.iterator().next();
        if (roundWinner >= 0) {
            int total = score.addPoint(sideId(roundWinner));
            broadcast("§e本回合胜者：" + sideLabel(roundWinner)
                    + " §7(" + total + "/" + score.pointsToWin() + ")");
            showTitle("§e回合结束", "§f" + sideLabel(roundWinner) + " §7胜出 ("
                    + total + "/" + score.pointsToWin() + ")", 4, 30, 10);
            playToAll(SoundEvents.PLAYER_LEVELUP, 1.2f);
            updateBossBar();
            updateSidebar();
            if (score.isReached()) {
                winMatch(roundWinner);
                return;
            }
        } else {
            broadcast("§7本回合平局。");
            showTitle("§7回合平局", "", 4, 25, 10);
        }
        phase = MatchPhase.ROUND_RESULT;
        phaseTotalTicks = 3 * 20;
        lastCountdownSecond = -1;
        timer.startSeconds(3);
    }

    private void winMatch(int side) {
        this.winnerSide = side;
        this.draw = false;
        phase = MatchPhase.MATCH_RESULT;
        phaseTotalTicks = 5 * 20;
        timer.startSeconds(5);
        showMatchResultTitles(side);
        sendMatchResultSummary(side);
        bossBar.setName(Component.literal("§6§l对局结束 · " + sideLabel(side) + " 胜出"));
        bossBar.setProgress(1.0f);
        bossBar.setColor(BossEvent.BossBarColor.GREEN);
        showPersonalResultBars(side);
        broadcast("§6§l对局结束：" + sideLabel(side) + " §6§l胜出。");
        broadcastServerResult(side, false);
        broadcastMatchResult(side, false);
        playToAll(SoundEvents.PLAYER_LEVELUP, 1.0f);
        updateSidebar();
    }

    /** 平局收尾（限时到且并列最高时）。 */
    private void finishDraw() {
        this.winnerSide = -1;
        this.draw = true;
        phase = MatchPhase.MATCH_RESULT;
        phaseTotalTicks = 5 * 20;
        timer.startSeconds(5);
        showTitle("§7§l平局", "", 5, 60, 15);
        sendMatchResultSummary(-1);
        bossBar.setName(Component.literal("§7§l平局"));
        bossBar.setProgress(1.0f);
        bossBar.setColor(BossEvent.BossBarColor.WHITE);
        showPersonalDrawBars();
        broadcast("§7§l平局！");
        broadcastServerResult(-1, true);
        broadcastMatchResult(-1, true);
        updateSidebar();
    }

    private void showMatchResultTitles(int winningSide) {
        String subtitle = "§7" + sideLabel(winningSide) + " 胜出 · " + scoreLine();
        for (UUID id : sideOf.keySet()) {
            Integer side = sideOf.get(id);
            if (side != null && side == winningSide) {
                showTitleTo(id, "§a§l胜利", subtitle, 5, 60, 15);
            } else {
                showTitleTo(id, "§c§l失败", subtitle, 5, 60, 15);
            }
        }
    }

    private void sendMatchResultSummary(int winningSide) {
        for (UUID id : sideOf.keySet()) {
            ServerPlayer p = player(id);
            if (p == null) {
                continue;
            }
            boolean isDraw = winningSide < 0;
            boolean won = !isDraw && sideOf.getOrDefault(id, -1) == winningSide;
            String outcome = isDraw ? "§7平局" : (won ? "§a胜利" : "§c失败");
            String extra = settings.scoringMode() == ScoringMode.KILL_COUNT
                    ? " §7你的击杀 §e" + kills.getOrDefault(id, 0)
                    : "";
            p.sendSystemMessage(Component.literal("§6赛后结算 §8| " + outcome
                    + " §8| §7比分 §f" + scoreLine() + extra));
        }
    }

    private void broadcastServerResult(int winningSide, boolean isDraw) {
        TopKiller top = topKiller();
        String outcome = isDraw ? "§7平局" : "§a" + plainWinnerName(winningSide) + " §7胜出";
        String mvp = top.kills() > 0 ? " §8| §7击杀王 §e" + top.name() + " §7(" + top.kills() + "杀)" : "";
        Component message = Component.literal("§6[ACT0赛果] §f" + settings.displayName()
                + " §8| " + outcome + " §8| §7比分 §f" + plainScoreLine() + mvp);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            online.sendSystemMessage(message);
        }
    }

    private String plainWinnerName(int winningSide) {
        if (winningSide < 0) {
            return "平局";
        }
        List<String> names = sideMemberNames(winningSide);
        if (settings.scoringMode() == ScoringMode.KILL_COUNT && settings.teamSize() > 1) {
            return "队伍 " + (winningSide + 1);
        }
        return names.isEmpty() ? "胜利方" : String.join("、", names);
    }

    private void broadcastMatchResult(int winningSide, boolean isDraw) {
        TopKiller top = topKiller();
        List<String> winners = winningSide >= 0 ? sideMemberNames(winningSide) : List.of();
        String winnerDisplay;
        if (isDraw) {
            winnerDisplay = "平局";
        } else if (settings.scoringMode() == ScoringMode.KILL_COUNT && settings.teamSize() > 1) {
            winnerDisplay = "队伍 " + (winningSide + 1);
        } else {
            winnerDisplay = winners.isEmpty() ? "胜利方" : String.join("、", winners);
        }
        MatchResultBroadcaster.sendArcadeResult(
                matchId,
                settings.displayName(),
                matchDurationSeconds(),
                isDraw,
                winnerDisplay,
                winners,
                top.name(),
                top.kills(),
                plainScoreLine());
    }

    private void finish() {
        releaseCountdownLock();
        sendFireLockToAll(false);
        cleanupAmmoCrates();
        clearAllTeamHighlights();
        clearNameTagTeams();
        clearAllKillerGlow();
        respawnTimers.clear();
        respawnLastSecond.clear();
        deathCamView.clear();
        teammateSpectateSwitchTick.clear();
        teammateSpectateTarget.clear();
        lastHurtTick.clear();
        jumpChargeTicks.clear();
        jumpChargeCooldownUntil.clear();
        jumpChargeInput.clear();
        for (UUID id : sideOf.keySet()) {
            ServerPlayer player = player(id);
            if (player != null) {
                exitSpectator(player);
                clearJumpSniperEffects(player);
                TeleportHelper.teleport(player, arena.returnSpawn());
                restorePreMatchMode(player);
                player.getInventory().clearContent();
                sidebar.hideFrom(player);
                removeBossBarPlayer(player);
                String result = draw
                        ? "§7本局平局，已送你回大厅。"
                        : (sideOf.get(id) != null && sideOf.get(id) == winnerSide
                                ? "§a你所在的队伍获胜！已送你回大厅。"
                                : "§7对局结束，已送你回大厅。");
                player.sendSystemMessage(Component.literal(result));
                actionBar(id, "§8已返回大厅");
            }
        }
        bossBar.removeAllPlayers();
        bossBar.setVisible(false);
        for (ServerBossEvent bar : personalBossBars.values()) {
            bar.removeAllPlayers();
        }
        personalBossBars.clear();
        phase = MatchPhase.ENDED;
    }

    private void cleanupAmmoCrates() {
        if (ammoCrates.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null) {
                    continue;
                }
                if (ammoCrates.contains(entity.getUUID())) {
                    entity.discard();
                }
            }
        }
        ammoCrates.clear();
    }

    public void forgetAmmoCrate(UUID crateId) {
        if (crateId != null) {
            ammoCrates.remove(crateId);
        }
    }

    /** 处理一名参战玩家的掉线：立即移出对局，不再保留席位；重新加入视为中途加入。 */
    public void onPlayerLeft(UUID playerId) {
        if (!sideOf.containsKey(playerId)) {
            return;
        }
        if (phase == MatchPhase.ENDED) {
            return;
        }
        int side = sideOf.getOrDefault(playerId, 0);
        sides.get(side).remove(playerId);
        sideOf.remove(playerId);
        disconnected.remove(playerId);
        respawnTimers.remove(playerId);
        respawnLastSecond.remove(playerId);
        countdownLock.remove(playerId);
        deathCamView.remove(playerId);
        teammateSpectateSwitchTick.remove(playerId);
        teammateSpectateTarget.remove(playerId);
        lastHurtTick.remove(playerId);
        clearJumpCharge(playerId);
        kills.remove(playerId);
        boolean wasAlive = alive.remove(playerId);
        ServerPlayer p = player(playerId);
        if (p != null) {
            clearKillerGlow(p);
            clearTeamHighlightFor(p);
            clearTeamHighlightTarget(p);
            clearJumpSniperEffects(p);
            exitSpectator(p);
                removeBossBarPlayer(p);
            sidebar.hideFrom(p);
        }
        if (settings.scoringMode() != ScoringMode.KILL_COUNT && wasAlive && phase == MatchPhase.COMBAT) {
            evaluateRoundElimination();
        }
        setupNameTagTeams();
        updateSidebar();
        broadcast("§7" + nameOf(playerId) + " 离线，已退出本对局。重新加入将按中途加入处理。");
        checkLiveness();
    }

    public boolean quitPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!sideOf.containsKey(id) || phase == MatchPhase.ENDED) {
            return false;
        }
        int side = sideOf.getOrDefault(id, 0);
        sides.get(side).remove(id);
        sideOf.remove(id);
        disconnected.remove(id);
        alive.remove(id);
        respawnTimers.remove(id);
        respawnLastSecond.remove(id);
        countdownLock.remove(id);
        deathCamView.remove(id);
        teammateSpectateSwitchTick.remove(id);
        teammateSpectateTarget.remove(id);
        lastHurtTick.remove(id);
        clearJumpCharge(id);
        kills.remove(id);
        clearKillerGlow(player);
        clearTeamHighlightFor(player);
        clearTeamHighlightTarget(player);
        clearJumpSniperEffects(player);
        exitSpectator(player);
        restorePreMatchMode(player);
        TeleportHelper.teleport(player, arena.returnSpawn());
        player.getInventory().clearContent();
        sidebar.hideFrom(player);
        removeBossBarPlayer(player);
        ArcadeNetwork.sendFireLock(player, false);
        broadcast("§e" + player.getGameProfile().getName() + " §7退出了本对局。");
        player.sendSystemMessage(Component.literal("§7已退出对局，返回大厅。"));
        setupNameTagTeams();
        updateSidebar();
        checkLiveness();
        return true;
    }

    /**
     * 玩家重连归位：清除掉线标记，恢复血条/计分板，传送回战场并补发装备。
     *
     * @return 是否成功归位（该玩家确属本对局且处于掉线态）
     */
    public boolean onPlayerRejoin(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!sideOf.containsKey(id) || phase == MatchPhase.ENDED) {
            return false;
        }
        disconnected.remove(id);
        addBossBarPlayer(player);
        sidebar.showTo(player);
        int side = sideOf.getOrDefault(id, 0);
        if (phase == MatchPhase.COMBAT || phase == MatchPhase.COUNTDOWN) {
            exitSpectator(player);
            enterAdventureMode(player);
            clearJumpCharge(id);
            spawnForRound(player, id, side);
            equip(player);
            applyGlobalHealth(player);
            alive.add(id);
            if (phase == MatchPhase.COUNTDOWN) {
                applyCountdownLock(player);
                ArcadeNetwork.sendFireLock(player, true);
            } else {
                ArcadeNetwork.sendFireLock(player, false);
            }
        } else {
            TeleportHelper.teleport(player, arena.returnSpawn());
        }
        updateSidebar();
        player.sendSystemMessage(Component.literal("§a已重连回到对局。"));
        return true;
    }

    /**
     * 中途加入（补人）：把一名玩家放入未满的对局。
     *
     * @return 面向调用者的反馈文本；{@code null} 表示成功
     */
    public String addLatecomer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (phase == MatchPhase.ENDED || phase == MatchPhase.MATCH_RESULT) {
            return "§c对局即将结束，无法加入。";
        }
        if (sideOf.containsKey(id)) {
            return "§e你已在该对局中。";
        }
        if (isFullCapacity()) {
            return "§c对局人数已满。";
        }
        if (settings.respawnPolicy() == RespawnPolicy.RANDOM && arena.randomSpawns().size() <= sideOf.size()) {
            return "§c个人乱斗复活点不足，无法继续加入。";
        }
        int side = smallestSide();
        sides.get(side).add(id);
        sideOf.put(id, side);
        kills.put(id, 0);
        rememberPreMatchMode(player);
        setupNameTagTeams();
        addBossBarPlayer(player);
        sidebar.showTo(player);
        if (phase == MatchPhase.COMBAT || phase == MatchPhase.COUNTDOWN) {
            enterAdventureMode(player);
            clearJumpCharge(id);
            spawnForRound(player, id, side);
            equip(player);
            applyGlobalHealth(player);
            alive.add(id);
            ArcadeNetwork.sendFireLock(player, phase == MatchPhase.COUNTDOWN);
        }
        updateSidebar();
        broadcast("§b" + player.getGameProfile().getName() + " §7中途加入了对局！");
        return null;
    }

    /** 当前已占用坐位数（含掉线者）。 */
    public int occupancy() {
        return sideOf.size();
    }

    /** 对局容量（总参战人数）。 */
    public int capacity() {
        return settings.totalPlayers();
    }

    /** 是否可中途加入（弹性模式且未满且未结束）。 */
    public boolean acceptsLatecomers() {
        return settings.flexible() && !isFullCapacity()
                && (settings.respawnPolicy() != RespawnPolicy.RANDOM || arena.randomSpawns().size() > sideOf.size())
                && phase != MatchPhase.ENDED && phase != MatchPhase.MATCH_RESULT;
    }

    /** 是否为可增长（弹性）对局：弹性模式可保留房间供中途补人。 */
    public boolean canGrow() {
        return settings.flexible();
    }

    private boolean isFullCapacity() {
        return sideOf.size() >= settings.totalPlayers();
    }

    /** 选当前人数最少的一方（中途加入时平衡分配）。 */
    private int smallestSide() {
        int best = 0;
        int bestSize = Integer.MAX_VALUE;
        for (int i = 0; i < settings.sideCount(); i++) {
            int s = sides.get(i).size();
            if (s < bestSize) {
                bestSize = s;
                best = i;
            }
        }
        return best;
    }

    /** 检测在场（未掉线）参战方数；若只剩一方或无人则自动收尾，防止僵尸对局。 */
    private void checkLiveness() {
        if (phase == MatchPhase.ENDED || phase == MatchPhase.MATCH_RESULT) {
            return;
        }
        Set<Integer> connectedSides = new LinkedHashSet<>();
        int connectedPlayers = 0;
        for (Map.Entry<UUID, Integer> e : sideOf.entrySet()) {
            if (!disconnected.contains(e.getKey())) {
                connectedSides.add(e.getValue());
                connectedPlayers++;
            }
        }
        if (connectedPlayers == 0) {
            broadcast("§7全员掉线，对局结束。");
            finish();
        } else if (connectedSides.size() == 1 && sideOf.size() > 1) {
            int winner = connectedSides.iterator().next();
            broadcast("§7其余玩家已离开，对局结束。");
            winMatch(winner);
        }
    }

    private void setupNameTagTeams() {
        clearNameTagTeams();
        Scoreboard scoreboard = server.getScoreboard();
        for (int side = 0; side < sides.size(); side++) {
            String teamName = scoreboardTeamName(side);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
            team = scoreboard.addPlayerTeam(teamName);
            team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
            team.setColor(teamColor(side));
            nameTagTeams.add(team);
            for (UUID id : sides.get(side)) {
                ServerPlayer player = player(id);
                if (player != null) {
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                }
            }
        }
    }

    private void clearNameTagTeams() {
        if (nameTagTeams.isEmpty()) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        for (PlayerTeam team : new ArrayList<>(nameTagTeams)) {
            PlayerTeam live = scoreboard.getPlayerTeam(team.getName());
            if (live != null) {
                scoreboard.removePlayerTeam(live);
            }
        }
        nameTagTeams.clear();
    }

    private String scoreboardTeamName(int side) {
        String base = "a0" + Integer.toHexString(matchId.hashCode()) + "_" + side;
        return base.length() <= 16 ? base : base.substring(0, 16);
    }

    private ChatFormatting teamColor(int side) {
        if (usesPersonalBossBars()) {
            return ffaColor(side);
        }
        return side == 0 ? ChatFormatting.BLUE : (side == 1 ? ChatFormatting.RED : ChatFormatting.GRAY);
    }

    private ChatFormatting ffaColor(int side) {
        if (side < 0) {
            return ChatFormatting.GRAY;
        }
        return FFA_COLORS[side % FFA_COLORS.length];
    }

    private String sideColor(int side) {
        return teamColor(side).toString();
    }

    private boolean hasVisibleTeamMates() {
        return settings.teamSize() > 1;
    }

    private void syncTeamHighlights() {
        for (UUID viewerId : sideOf.keySet()) {
            ServerPlayer viewer = player(viewerId);
            if (viewer == null || !viewer.isAlive() || viewer.isSpectator() || deathCamView.containsKey(viewerId)) {
                continue;
            }
            Integer viewerSide = sideOf.get(viewerId);
            syncRelativeTeams(viewer, viewerId, viewerSide);
            if (!hasVisibleTeamMates()) {
                continue;
            }
            for (UUID targetId : sideOf.keySet()) {
                if (targetId.equals(viewerId)) {
                    continue;
                }
                ServerPlayer target = player(targetId);
                boolean teammate = viewerSide != null && viewerSide.equals(sideOf.get(targetId));
                boolean show = teammate && target != null && target.isAlive() && !target.isSpectator()
                        && !deathCamView.containsKey(targetId);
                if (target != null) {
                    if (show) {
                        GlowSync.showGlowTo(viewer, target);
                    } else {
                        GlowSync.hideGlowFrom(viewer, target);
                    }
                }
            }
        }
    }

    private void syncRelativeTeams(ServerPlayer viewer, UUID viewerId, Integer viewerSide) {
        RelativeTeamSync.sync(viewer, sideOf.keySet(), this::player,
                id -> id.equals(viewerId) || (viewerSide != null && viewerSide.equals(sideOf.get(id))));
    }

    private void clearTeamHighlightFor(ServerPlayer viewer) {
        RelativeTeamSync.clear(viewer);
        for (UUID targetId : sideOf.keySet()) {
            ServerPlayer target = player(targetId);
            if (target != null && !target.getUUID().equals(viewer.getUUID())) {
                GlowSync.hideGlowFrom(viewer, target);
            }
        }
    }

    private void clearTeamHighlightTarget(ServerPlayer target) {
        for (UUID viewerId : sideOf.keySet()) {
            ServerPlayer viewer = player(viewerId);
            if (viewer != null && !viewer.getUUID().equals(target.getUUID())) {
                RelativeTeamSync.removeTarget(viewer, target);
                GlowSync.hideGlowFrom(viewer, target);
            }
        }
    }

    private void clearAllTeamHighlights() {
        for (UUID viewerId : sideOf.keySet()) {
            ServerPlayer viewer = player(viewerId);
            if (viewer != null) {
                clearTeamHighlightFor(viewer);
            }
        }
    }

    /** 强制结束（如服务器关闭/管理员中止）。 */
    public void abort() {
        if (phase != MatchPhase.ENDED) {
            finish();
        }
    }

    // ---- 出生与装备 ----

    private void spawnAtSide(ServerPlayer player, int side) {
        TeleportHelper.teleport(player, arena.sideSpawn(side));
    }

    private void spawnForRound(ServerPlayer player, UUID playerId, int side) {
        if (settings.respawnPolicy() == RespawnPolicy.RANDOM) {
            TeleportHelper.teleport(player, initialFreeForAllSpawn(side));
        } else if ("duel_2v2".equals(settings.modeId()) || isJumpSniper()) {
            TeleportHelper.teleport(player, offsetTeamSpawn(playerId, side));
        } else {
            spawnAtSide(player, side);
        }
    }

    private SpawnPoint offsetTeamSpawn(UUID playerId, int side) {
        SpawnPoint base = arena.sideSpawn(side);
        int index = memberIndexInSide(playerId, side);
        int size = Math.max(1, sides.get(side).size());
        if (size <= 1) {
            return base;
        }
        double spacing = 1.25;
        double offset = (index - (size - 1) / 2.0) * spacing;
        double radians = Math.toRadians(base.yaw() + 90.0);
        double x = base.x() + Math.cos(radians) * offset;
        double z = base.z() + Math.sin(radians) * offset;
        return new SpawnPoint(base.dimension(), x, base.y(), z, base.yaw(), base.pitch());
    }

    private int memberIndexInSide(UUID playerId, int side) {
        int index = 0;
        for (UUID id : sides.get(side)) {
            if (id.equals(playerId)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private SpawnPoint initialFreeForAllSpawn(int side) {
        List<SpawnPoint> spawns = arena.randomSpawns();
        if (spawns.isEmpty()) {
            return arena.sideSpawn(side);
        }
        int index = Math.max(0, Math.min(side, spawns.size() - 1));
        return spawns.get(index);
    }

    private void rememberPreMatchMode(ServerPlayer player) {
        GameType current = player.gameMode.getGameModeForPlayer();
        if (current != GameType.SPECTATOR) {
            preMatchGameMode.putIfAbsent(player.getUUID(), current);
        }
    }

    private void enterAdventureMode(ServerPlayer player) {
        rememberPreMatchMode(player);
        player.setGameMode(GameType.ADVENTURE);
    }

    private void restorePreMatchMode(ServerPlayer player) {
        GameType original = preMatchGameMode.remove(player.getUUID());
        if (original != null && original != GameType.SPECTATOR) {
            player.setGameMode(original);
        }
        originalGameMode.remove(player.getUUID());
    }

    private void respawn(ServerPlayer player, int side) {
        switch (settings.respawnPolicy()) {
            case FIXED_SPAWN -> TeleportHelper.teleport(player, arena.sideSpawn(side));
            case NEAR_TEAMMATE -> {
                SpawnPoint near = livingTeammateSpawn(player, side);
                TeleportHelper.teleport(player, near != null ? near : arena.sideSpawn(side));
            }
            case RANDOM -> TeleportHelper.teleport(player, manualFreeForAllSpawn(player));
        }
    }

    private SpawnPoint manualFreeForAllSpawn(ServerPlayer player) {
        List<SpawnPoint> candidates = arena.randomSpawns();
        if (candidates.isEmpty()) {
            return arena.sideSpawn(sideOf.getOrDefault(player.getUUID(), 0));
        }
        int start = random.nextInt(candidates.size());
        SpawnPoint fallback = null;
        for (int i = 0; i < candidates.size(); i++) {
            SpawnPoint spawn = candidates.get((start + i) % candidates.size());
            if (fallback == null && TeleportHelper.resolveLevel(server, spawn.dimension()) != null) {
                fallback = spawn;
            }
            if (isSpawnClear(spawn, player.getUUID())) {
                return spawn;
            }
        }
        return fallback != null ? fallback : candidates.get(start);
    }

    private boolean isSpawnClear(SpawnPoint spawn, UUID self) {
        ServerLevel level = TeleportHelper.resolveLevel(server, spawn.dimension());
        if (level == null) {
            return false;
        }
        double radiusSq = RESPAWN_CLEAR_RADIUS * RESPAWN_CLEAR_RADIUS;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(self) || other.level() != level || !other.isAlive()) {
                continue;
            }
            double dx = other.getX() - spawn.x();
            double dy = other.getY() - spawn.y();
            double dz = other.getZ() - spawn.z();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return false;
            }
        }
        return true;
    }

    private SpawnPoint livingTeammateSpawn(ServerPlayer respawning, int side) {
        UUID self = respawning.getUUID();
        for (UUID mate : sides.get(side)) {
            if (mate.equals(self)) {
                continue;
            }
            ServerPlayer mp = player(mate);
            if (mp == null || !mp.isAlive() || mp.isSpectator() || deathCamView.containsKey(mate)) {
                continue;
            }
            if (isTeammateInCombat(mp, side)) {
                continue;
            }
            SpawnPoint safe = safeSpawnNearTeammate(respawning, mp, side);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private boolean isTeammateInCombat(ServerPlayer teammate, int side) {
        long now = server.getTickCount();
        long lastHurt = lastHurtTick.getOrDefault(teammate.getUUID(), Long.MIN_VALUE / 4);
        if (now - lastHurt <= TEAMMATE_RECENT_COMBAT_TICKS) {
            return true;
        }
        double radiusSq = TEAMMATE_COMBAT_RADIUS * TEAMMATE_COMBAT_RADIUS;
        for (UUID id : sideOf.keySet()) {
            if (sideOf.getOrDefault(id, -1) == side) {
                continue;
            }
            ServerPlayer enemy = player(id);
            if (enemy == null || enemy.level() != teammate.level() || !enemy.isAlive() || enemy.isSpectator()
                    || deathCamView.containsKey(id)) {
                continue;
            }
            if (enemy.distanceToSqr(teammate) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private SpawnPoint safeSpawnNearTeammate(ServerPlayer respawning, ServerPlayer teammate, int side) {
        ServerLevel level = teammate.serverLevel();
        int angleOffset = random.nextInt(12);
        for (double radius = TEAMMATE_RESPAWN_MIN_RADIUS; radius <= TEAMMATE_RESPAWN_MAX_RADIUS; radius += 1.0D) {
            for (int i = 0; i < 12; i++) {
                double angle = ((angleOffset + i) / 12.0D) * Math.PI * 2.0D;
                double x = teammate.getX() + Math.cos(angle) * radius;
                double y = teammate.getY();
                double z = teammate.getZ() + Math.sin(angle) * radius;
                if (isTeamRespawnSpotSafe(respawning, level, x, y, z, side)) {
                    String dim = level.dimension().location().toString();
                    return new SpawnPoint(dim, x, y, z, teammate.getYRot(), teammate.getXRot());
                }
            }
        }
        return null;
    }

    private boolean isTeamRespawnSpotSafe(ServerPlayer respawning, ServerLevel level, double x, double y, double z, int side) {
        if (!level.noCollision(respawning, respawning.getBoundingBox().move(
                x - respawning.getX(), y - respawning.getY(), z - respawning.getZ()))) {
            return false;
        }
        double clearSq = TEAMMATE_RESPAWN_ENEMY_CLEAR_RADIUS * TEAMMATE_RESPAWN_ENEMY_CLEAR_RADIUS;
        for (UUID id : sideOf.keySet()) {
            if (sideOf.getOrDefault(id, -1) == side) {
                continue;
            }
            ServerPlayer enemy = player(id);
            if (enemy == null || enemy.level() != level || !enemy.isAlive() || enemy.isSpectator()
                    || deathCamView.containsKey(id)) {
                continue;
            }
            double dx = enemy.getX() - x;
            double dy = enemy.getY() - y;
            double dz = enemy.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= clearSq) {
                return false;
            }
        }
        return true;
    }

    private void scheduleTeammateSpectate(UUID victimId) {
        if (!"duel_2v2".equals(settings.modeId())) {
            return;
        }
        teammateSpectateSwitchTick.put(victimId, (long) server.getTickCount() + 70L);
    }

    private void tickTeammateSpectate() {
        if (teammateSpectateSwitchTick.isEmpty() && teammateSpectateTarget.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(teammateSpectateTarget.entrySet())) {
            ServerPlayer viewer = player(e.getKey());
            ServerPlayer target = player(e.getValue());
            if (viewer == null || target == null || !target.isAlive() || target.isSpectator() || !alive.contains(e.getValue())) {
                if (viewer != null) {
                    viewer.setCamera(viewer);
                }
                teammateSpectateTarget.remove(e.getKey());
            }
        }
        for (Map.Entry<UUID, Long> e : new ArrayList<>(teammateSpectateSwitchTick.entrySet())) {
            if (now < e.getValue() || phase != MatchPhase.COMBAT) {
                continue;
            }
            UUID viewerId = e.getKey();
            ServerPlayer viewer = player(viewerId);
            if (viewer == null) {
                teammateSpectateSwitchTick.remove(viewerId);
                continue;
            }
            UUID teammateId = livingTeammateId(viewerId);
            ServerPlayer teammate = teammateId != null ? player(teammateId) : null;
            if (teammate == null) {
                teammateSpectateSwitchTick.remove(viewerId);
                continue;
            }
            deathCamView.remove(viewerId);
            clearKillerGlow(viewer);
            ArcadeNetwork.sendDeathCam(viewer, false, "");
            viewer.setCamera(teammate);
            teammateSpectateTarget.put(viewerId, teammateId);
            teammateSpectateSwitchTick.remove(viewerId);
            actionBar(viewerId, "§7正在观察队友 §a" + teammate.getGameProfile().getName());
        }
    }

    private UUID livingTeammateId(UUID self) {
        Integer side = sideOf.get(self);
        if (side == null) {
            return null;
        }
        for (UUID mate : sides.get(side)) {
            if (mate.equals(self)) {
                continue;
            }
            ServerPlayer mp = player(mate);
            if (mp != null && mp.isAlive() && !mp.isSpectator() && alive.contains(mate)) {
                return mate;
            }
        }
        return null;
    }

    // ---- 观察者 / 开局移动锁 ----

    /** 进入观察者视角（死亡等待时），记录原始游戏模式以便复活时还原，锁定死亡相机并朝向击杀者。 */
    private void enterSpectator(ServerPlayer player, UUID killerId) {
        GameType current = player.gameMode.getGameModeForPlayer();
        if (current != GameType.SPECTATOR) {
            originalGameMode.put(player.getUUID(), current);
        }
        // 死亡相机点：站在死亡点上方略高；若有击杀者则镜头朝向击杀者，否则俯视。
        float yaw = player.getYRot();
        float pitch = 35f;
        ServerPlayer killer = killerId != null ? player(killerId) : null;
        boolean validKiller = killer != null && killer.isAlive() && !killer.getUUID().equals(player.getUUID());
        double camX = player.getX();
        double camY = player.getEyeY() + 0.6;
        double camZ = player.getZ();
        if (validKiller) {
            float[] look = lookAt(camX, camY, camZ, killer.getX(), killer.getEyeY(), killer.getZ());
            yaw = look[0];
            pitch = look[1];
            // 击杀者高亮：只对死者一人发"发光"数据包，其他玩家完全看不到（不泄露击杀者位置）。
            GlowSync.showGlowTo(player, killer);
            deathCamKiller.put(player.getUUID(), killerId);
        }
        deathCamView.put(player.getUUID(), new DeathView(camX, camY, camZ, yaw, pitch));
        player.setGameMode(GameType.SPECTATOR);
        player.connection.teleport(camX, camY, camZ, yaw, pitch);
        String killerName = validKiller ? killer.getGameProfile().getName() : "";
        ArcadeNetwork.sendDeathCam(player, true, killerName);
    }

    /** 进入自由观察者视角：用于跳狙飞人回合制死亡后自由观战，直到下一回合复活。 */
    private void enterFreeSpectator(ServerPlayer player) {
        GameType current = player.gameMode.getGameModeForPlayer();
        if (current != GameType.SPECTATOR) {
            originalGameMode.put(player.getUUID(), current);
        }
        player.setCamera(player);
        deathCamView.remove(player.getUUID());
        teammateSpectateSwitchTick.remove(player.getUUID());
        teammateSpectateTarget.remove(player.getUUID());
        clearKillerGlow(player);
        ArcadeNetwork.sendDeathCam(player, false, "");
        player.setGameMode(GameType.SPECTATOR);
    }

    /** 退出观察者视角，还原至记录的原始游戏模式（默认生存），清除死亡相机与击杀者高亮。 */
    private void exitSpectator(ServerPlayer player) {
        player.setCamera(player);
        deathCamView.remove(player.getUUID());
        teammateSpectateSwitchTick.remove(player.getUUID());
        teammateSpectateTarget.remove(player.getUUID());
        clearKillerGlow(player);
        ArcadeNetwork.sendDeathCam(player, false, "");
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            return;
        }
        GameType original = originalGameMode.getOrDefault(player.getUUID(), GameType.SURVIVAL);
        player.setGameMode(original);
    }

    /** 只对该死者关闭其所盯击杀者的发光（发光本就只对他自己可见）。 */
    private void clearKillerGlow(ServerPlayer viewer) {
        UUID killerId = deathCamKiller.remove(viewer.getUUID());
        if (killerId == null) {
            return;
        }
        ServerPlayer killer = player(killerId);
        if (killer != null) {
            GlowSync.hideGlowFrom(viewer, killer);
        }
    }

    /** 清除所有死者各自看到的击杀者发光（对局结束/中止时）。 */
    private void clearAllKillerGlow() {
        for (Map.Entry<UUID, UUID> e : deathCamKiller.entrySet()) {
            ServerPlayer viewer = player(e.getKey());
            ServerPlayer killer = player(e.getValue());
            if (viewer != null && killer != null) {
                GlowSync.hideGlowFrom(viewer, killer);
            }
        }
        deathCamKiller.clear();
    }

    /** 死亡观战每刻把旁观相机钉回死亡点并持续朝向击杀者，禁止玩家自由飞行观战。 */
    private void enforceDeathCam() {
        if (deathCamView.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, DeathView> e : deathCamView.entrySet()) {
            UUID viewerId = e.getKey();
            ServerPlayer p = player(viewerId);
            if (p == null) {
                continue;
            }
            DeathView v = e.getValue();
            // 击杀者仍存活时持续追踪其方向，制造"死亡回放盯着凶手"的电影感。
            float yaw = p.getYRot();
            float pitch = p.getXRot();
            boolean reaim = false;
            UUID killerId = deathCamKiller.get(viewerId);
            if (killerId != null) {
                ServerPlayer killer = player(killerId);
                if (killer != null && killer.isAlive()) {
                    float[] look = lookAt(v.x(), v.y(), v.z(), killer.getX(), killer.getEyeY(), killer.getZ());
                    yaw = look[0];
                    pitch = look[1];
                    reaim = true;
                }
            }
            double dx = p.getX() - v.x();
            double dz = p.getZ() - v.z();
            boolean offPos = dx * dx + dz * dz > 0.04 || Math.abs(p.getY() - v.y()) > 0.5;
            if (offPos || reaim) {
                p.connection.teleport(v.x(), v.y(), v.z(), yaw, pitch);
                p.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
    }

    /** 计算从相机点看向目标点的 yaw/pitch（度）。 */
    private static float[] lookAt(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{yaw, pitch};
    }

    /** 死亡相机锁定点。 */
    private record DeathView(double x, double y, double z, float yaw, float pitch) {
    }

    /** 开局倒计时锁定：记录当前位置并施加隐藏减速，防止玩家在准备阶段乱跑。 */
    private void applyCountdownLock(ServerPlayer player) {
        countdownLock.put(player.getUUID(), player.position());
        int dur = Math.max(1, settings.countdownSeconds()) * 20 + 10;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 250, false, false, false));
    }

    /** 每刻校正：被锁定玩家若水平移动则拉回锁定点（保留视角自由）。 */
    private void enforceCountdownLock() {
        if (countdownLock.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Vec3> e : countdownLock.entrySet()) {
            ServerPlayer p = player(e.getKey());
            if (p == null) {
                continue;
            }
            Vec3 lock = e.getValue();
            double dx = p.getX() - lock.x;
            double dz = p.getZ() - lock.z;
            if (dx * dx + dz * dz > 0.0036) {
                p.connection.teleport(lock.x, lock.y, lock.z, p.getYRot(), p.getXRot());
                p.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
    }

    /** 解除开局移动锁（进入战斗阶段时）。 */
    private void releaseCountdownLock() {
        for (UUID id : countdownLock.keySet()) {
            ServerPlayer p = player(id);
            if (p != null) {
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
        countdownLock.clear();
    }

    /** 向单个玩家显示标题/副标题。 */
    private void showTitleTo(UUID id, String title, String sub, int fadeIn, int stay, int fadeOut) {
        ServerPlayer p = player(id);
        if (p == null) {
            return;
        }
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        if (sub != null && !sub.isEmpty()) {
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
        }
    }

    private void equip(ServerPlayer player) {
        if (settings.randomWeapons()) {
            equipRandom(player);
            applyApparel(player);
            return;
        }
        Loadout loadout = loadoutProvider.apply(player.getUUID());
        if (loadout == null) {
            return;
        }
        Set<String> unlocked = unlocksProvider.apply(player.getUUID());
        applier.apply(player, loadout, settings.ruleset(), unlocked, true);
        applyApparel(player);
    }

    private void applyGlobalHealth(ServerPlayer player) {
        ArcadeGlobalSettings.get(server).applyHealth(player);
    }

    private void applyApparel(ServerPlayer player) {
        Set<String> unlocked = unlocksProvider.apply(player.getUUID());
        applier.applyApparel(player, ArcadeApparelStore.get(server).getOrCreate(player.getUUID()),
                org.shee33.act0.arcade.Act0Arcade.services().apparel(), unlocked);
    }

    /** 随机武器：按房间选择的细分随机模式临时生成配装。 */
    private void equipRandom(ServerPlayer player) {
        PlayerClassType cls = PlayerClassType.defaultClass();
        Loadout base = loadoutProvider.apply(player.getUUID());
        if (base != null) {
            cls = base.classType();
        }
        RandomWeaponMode mode = settings.randomWeaponMode();
        Loadout temp = new Loadout(mode.displayName(), cls);
        Set<String> grant = new LinkedHashSet<>();

        LoadoutItem primary = null;
        LoadoutItem secondary;
        LoadoutItem melee;
        LoadoutItem throwable = null;

        if (mode == RandomWeaponMode.ALL) {
            primary = randomItemForSlot(LoadoutSlot.PRIMARY_WEAPON, cls);
            throwable = randomItemForCategory(WeaponCategory.THROWABLE, cls);
        } else if (isJumpSniper()) {
            primary = randomItemForCategory(WeaponCategory.SNIPER, cls);
        } else if (!mode.pistolOnly() && mode.primaryCategory() != null) {
            primary = randomItemForCategory(mode.primaryCategory(), cls);
        }
        secondary = isJumpSniper() ? null : randomItemForCategory(WeaponCategory.PISTOL, cls);
        melee = randomItemForCategory(WeaponCategory.MELEE, cls);

        grantRandomItem(temp, grant, primary);
        grantRandomItem(temp, grant, secondary);
        grantRandomItem(temp, grant, melee);
        grantRandomItem(temp, grant, throwable);
        applier.apply(player, temp, settings.ruleset(), grant, true);

        // 告知玩家本次抽到的武器，避免"不知道这局是随机武器"
        String primaryName = primary != null ? primary.displayName() : "无";
        String secondaryName = secondary != null ? secondary.displayName() : "无";
        String meleeName = melee != null ? melee.displayName() : "无";
        actionBar(player.getUUID(), "§6" + mode.displayName() + " §7» §f" + primaryName
                + " §8/ §f" + secondaryName + " §8/ §f" + meleeName);
        playTo(player.getUUID(), SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f);
    }

    private boolean isJumpSniper() {
        return "jump_sniper".equals(settings.modeId());
    }

    public boolean shouldCancelFallDamage(UUID playerId) {
        return isJumpSniper() && sideOf.containsKey(playerId) && phase != MatchPhase.ENDED;
    }

    /** 客户端蓄力跳按键状态同步入口。 */
    public void setJumpCharging(UUID playerId, boolean charging) {
        setJumpCharging(playerId, charging, false);
    }

    /** 客户端蓄力跳按键状态同步入口。 */
    public void setJumpCharging(UUID playerId, boolean charging, boolean normalJump) {
        if (!isJumpSniper() || !sideOf.containsKey(playerId) || phase != MatchPhase.COMBAT) {
            clearJumpCharge(playerId);
            return;
        }
        if (normalJump) {
            launchNormalJump(playerId);
            return;
        }
        if (charging) {
            jumpChargeInput.add(playerId);
        } else {
            jumpChargeInput.remove(playerId);
        }
    }

    /** 跳狙飞人专属：按住空格在地面蓄力，松开后向视线方向弹射；短按空格则普通起跳。 */
    private void tickJumpCharge() {
        if (!isJumpSniper() || phase != MatchPhase.COMBAT) {
            return;
        }
        long now = server.getTickCount();
        for (UUID id : sideOf.keySet()) {
            ServerPlayer player = player(id);
            if (player == null || !player.isAlive() || player.isSpectator()
                    || deathCamView.containsKey(id) || respawnTimers.containsKey(id)) {
                clearJumpCharge(id);
                continue;
            }
            if (jumpChargeCooldownUntil.getOrDefault(id, 0L) > now) {
                jumpChargeTicks.remove(id);
                continue;
            }
            if (jumpChargeInput.contains(id) && player.onGround()) {
                int charge = Math.min(JUMP_CHARGE_MAX_TICKS, jumpChargeTicks.getOrDefault(id, 0) + 1);
                jumpChargeTicks.put(id, charge);
                if (charge == 1 || charge == JUMP_CHARGE_MAX_TICKS || charge % 5 == 0) {
                    int percent = Math.round(charge * 100.0f / JUMP_CHARGE_MAX_TICKS);
                    actionBar(id, "§b蓄力跳 §7» §f" + percent + "% §8(松开空格起跳)");
                    if (charge == JUMP_CHARGE_MAX_TICKS) {
                        playTo(id, SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f);
                    }
                }
            } else {
                int charge = jumpChargeTicks.getOrDefault(id, 0);
                if (charge > 0) {
                    jumpChargeTicks.remove(id);
                    if (player.onGround() && charge >= JUMP_CHARGE_MIN_TICKS) {
                        launchChargedJump(player, charge, now);
                    }
                }
            }
        }
    }

    private void launchNormalJump(UUID playerId) {
        jumpChargeTicks.remove(playerId);
        jumpChargeInput.remove(playerId);
        ServerPlayer player = player(playerId);
        if (player == null || !player.isAlive() || player.isSpectator() || !player.onGround()) {
            return;
        }
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.x, JUMP_CHARGE_NORMAL_VERTICAL, current.z);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private void launchChargedJump(ServerPlayer player, int chargeTicks, long now) {
        UUID id = player.getUUID();
        double strength = Math.min(1.0D, Math.max(0.0D,
                (chargeTicks - JUMP_CHARGE_MIN_TICKS) / (double) (JUMP_CHARGE_MAX_TICKS - JUMP_CHARGE_MIN_TICKS)));
        double vertical = lerp(JUMP_CHARGE_MIN_VERTICAL, JUMP_CHARGE_MAX_VERTICAL, strength);
        double horizontal = lerp(JUMP_CHARGE_MIN_HORIZONTAL, JUMP_CHARGE_MAX_HORIZONTAL, strength);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 0.0001D) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        } else {
            flat = flat.normalize();
        }
        Vec3 motion = flat.scale(horizontal).add(0.0D, vertical, 0.0D);
        player.setDeltaMovement(motion);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        jumpChargeCooldownUntil.put(id, now + JUMP_CHARGE_COOLDOWN_TICKS);
        actionBar(id, "§b蓄力跳 §7» §a释放 " + Math.round(strength * 100.0D) + "%");
        playTo(id, SoundEvents.SLIME_BLOCK_PLACE, 0.9f + (float) strength * 0.5f);
    }

    private static double lerp(double min, double max, double t) {
        return min + (max - min) * t;
    }

    private void clearJumpCharge(UUID id) {
        jumpChargeInput.remove(id);
        jumpChargeTicks.remove(id);
        jumpChargeCooldownUntil.remove(id);
    }

    private void tickJumpSniperEffects() {
        if (!isJumpSniper()) {
            return;
        }
        if (server.getTickCount() % 30L != 0L) {
            return;
        }
        for (UUID id : sideOf.keySet()) {
            if (respawnTimers.containsKey(id) || deathCamView.containsKey(id)) {
                continue;
            }
            ServerPlayer p = player(id);
            if (p == null || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            applyJumpSniperEffects(p);
        }
    }

    private void applyJumpSniperEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JUMP_SNIPER_EFFECT_TICKS, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, JUMP_SNIPER_EFFECT_TICKS, 3, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, JUMP_SNIPER_EFFECT_TICKS, 1, false, false, true));
    }

    private void clearJumpSniperEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOW_FALLING);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    private void grantRandomItem(Loadout temp, Set<String> grant, LoadoutItem item) {
        if (item == null) {
            return;
        }
        temp.setSlot(item.slot(), item.key());
        grant.add(item.key());
    }

    private LoadoutItem randomItemForCategory(WeaponCategory category, PlayerClassType cls) {
        List<LoadoutItem> pool = registry.availableItemsInCategory(category, cls);
        if (pool.isEmpty()) {
            pool = registry.itemsForCategory(category);
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** 从注册表中随机取一件该槽位、对指定职业可用的装备；无则返回 {@code null}。 */
    private LoadoutItem randomItemForSlot(LoadoutSlot slot, PlayerClassType cls) {
        List<LoadoutItem> pool = registry.availableItems(slot, cls);
        if (pool.isEmpty()) {
            pool = registry.itemsForSlot(slot);
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    // ---- 工具 ----

    private ServerPlayer player(UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    private String nameOf(UUID id) {
        ServerPlayer p = player(id);
        return p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8);
    }

    private void broadcast(String message) {
        Component component = Component.literal(message);
        for (UUID id : sideOf.keySet()) {
            ServerPlayer p = player(id);
            if (p != null) {
                p.sendSystemMessage(component);
            }
        }
    }

    private void sendFireLockToAll(boolean locked) {
        for (UUID id : sideOf.keySet()) {
            ServerPlayer p = player(id);
            if (p != null) {
                ArcadeNetwork.sendFireLock(p, locked);
            }
        }
    }

    /** 向全体参战玩家显示标题/副标题。{@code sub} 为空串则不发送副标题。 */
    private void showTitle(String title, String sub, int fadeIn, int stay, int fadeOut) {
        ClientboundSetTitlesAnimationPacket anim =
                new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
        ClientboundSetTitleTextPacket titlePacket =
                new ClientboundSetTitleTextPacket(Component.literal(title));
        ClientboundSetSubtitleTextPacket subPacket = sub == null || sub.isEmpty()
                ? null : new ClientboundSetSubtitleTextPacket(Component.literal(sub));
        for (UUID id : sideOf.keySet()) {
            ServerPlayer p = player(id);
            if (p == null) {
                continue;
            }
            p.connection.send(anim);
            p.connection.send(titlePacket);
            if (subPacket != null) {
                p.connection.send(subPacket);
            }
        }
    }

    /** 向单个玩家发送动作栏（物品栏上方）提示。 */
    private void actionBar(UUID id, String message) {
        ServerPlayer p = player(id);
        if (p != null) {
            p.displayClientMessage(Component.literal(message), true);
        }
    }

    /** 向全体参战玩家播放音效（不受距离影响）。 */
    private void playToAll(SoundEvent sound, float pitch) {
        for (UUID id : sideOf.keySet()) {
            playTo(id, sound, pitch);
        }
    }

    /** 向单个玩家播放音效。 */
    private void playTo(UUID id, SoundEvent sound, float pitch) {
        ServerPlayer p = player(id);
        if (p != null) {
            p.playNotifySound(sound, SoundSource.PLAYERS, 1.0f, pitch);
        }
    }

    /** 收集当前在线的参战玩家，用于计分板推送。 */
    private List<ServerPlayer> participants() {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID id : sideOf.keySet()) {
            ServerPlayer p = player(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    /** 刷新侧边栏计分板：标题为赛制，每方一行比分，并附赛制目标行。 */
    private void updateSidebar() {
        List<String> lines = new ArrayList<>();
        lines.add("§7目标: §f" + score.pointsToWin()
                + (settings.scoringMode() == ScoringMode.KILL_COUNT ? " 击杀"
                : (settings.scoringMode() == ScoringMode.HOT_ZONE ? " 分" : " 回合")));
        if (matchClockTicks >= 0) {
            lines.add("§7剩余: §f" + formatClock(matchClockTicks));
        }
        if (isHotZoneMode()) {
            int zoneNo = hotZoneIndex >= 0 ? hotZoneIndex + 1 : 1;
            int secs = Math.max(0, hotZoneTicksRemaining / 20);
            lines.add("§6热区: §f#" + zoneNo + " §7" + secs + "秒");
        }
        lines.add("§8 ");
        boolean ffa = settings.scoringMode() == ScoringMode.KILL_COUNT && settings.teamSize() == 1;
        if (ffa) {
            // 个人乱斗：逐人击杀实时榜，按击杀降序
            List<UUID> ranked = new ArrayList<>(sideOf.keySet());
            ranked.sort(Comparator.comparingInt((UUID id) -> kills.getOrDefault(id, 0)).reversed());
            int rank = 1;
            for (UUID id : ranked) {
                String tag = disconnected.contains(id) ? " §8(掉线)" : "";
                int side = sideOf.getOrDefault(id, -1);
                lines.add("§f" + rank + ". " + sideColor(side) + nameOf(id)
                        + " §7- " + sideColor(side) + kills.getOrDefault(id, 0) + tag);
                rank++;
            }
        } else {
            for (int i = 0; i < settings.sideCount(); i++) {
                String marker = (i == winnerSide) ? "§6▶ " : "§f";
                lines.add(marker + sideLabel(i) + " §7- §e" + score.score(sideId(i)));
            }
        }
        sidebar.update(lines, participants());
    }

    /** 把剩余 tick 格式化为 mm:ss。 */
    private static String formatClock(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    /** 刷新 Boss 血条的标题、颜色与进度（倒计时按时间、战斗按比分）。 */
    private void updateBossBar() {
        if (usesPersonalBossBars()) {
            updatePersonalBossBars();
            return;
        }
        if (!bossBar.isVisible()) {
            return;
        }
        switch (phase) {
            case COUNTDOWN -> {
                bossBar.setName(Component.literal("§e" + roundLabel() + " · 即将开始"));
                bossBar.setColor(BossEvent.BossBarColor.YELLOW);
                bossBar.setProgress(clamp01((float) timer.remainingTicks() / phaseTotalTicks));
            }
            case COMBAT -> {
                bossBar.setName(Component.literal("§a" + roundLabel() + " §7| " + scoreLine()));
                bossBar.setColor(BossEvent.BossBarColor.GREEN);
                bossBar.setProgress(clamp01((float) leadingScore() / Math.max(1, score.pointsToWin())));
            }
            default -> {
            }
        }
    }

    private boolean usesPersonalBossBars() {
        return settings.scoringMode() == ScoringMode.KILL_COUNT && settings.teamSize() == 1;
    }

    private void addBossBarPlayer(ServerPlayer player) {
        if (usesPersonalBossBars()) {
            personalBossBar(player.getUUID()).addPlayer(player);
        } else {
            bossBar.addPlayer(player);
        }
    }

    private void removeBossBarPlayer(ServerPlayer player) {
        ServerBossEvent personal = personalBossBars.get(player.getUUID());
        if (personal != null) {
            personal.removePlayer(player);
        }
        bossBar.removePlayer(player);
    }

    private ServerBossEvent personalBossBar(UUID id) {
        return personalBossBars.computeIfAbsent(id, ignored -> {
            ServerBossEvent bar = new ServerBossEvent(
                    Component.literal("§6" + settings.displayName()),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
            bar.setVisible(true);
            return bar;
        });
    }

    private void updatePersonalBossBars() {
        for (UUID id : sideOf.keySet()) {
            ServerBossEvent bar = personalBossBars.get(id);
            if (bar == null) {
                continue;
            }
            switch (phase) {
                case COUNTDOWN -> {
                    bar.setName(Component.literal("§e击杀战 · 即将开始"));
                    bar.setColor(BossEvent.BossBarColor.YELLOW);
                    bar.setProgress(clamp01((float) timer.remainingTicks() / phaseTotalTicks));
                }
                case COMBAT -> {
                    int own = kills.getOrDefault(id, 0);
                    int leader = leadingScore();
                    int target = Math.max(1, score.pointsToWin());
                        int side = sideOf.getOrDefault(id, -1);
                        bar.setName(Component.literal(sideColor(side) + "击杀战 §7| 你的击杀 " + sideColor(side) + own + "/" + target
                            + " §7| 领先 §e" + leader));
                        bar.setColor(bossColor(side));
                    bar.setProgress(clamp01((float) own / target));
                }
                default -> {
                }
            }
        }
    }

    private void showPersonalResultBars(int winningSide) {
        if (!usesPersonalBossBars()) {
            return;
        }
        for (Map.Entry<UUID, ServerBossEvent> e : personalBossBars.entrySet()) {
            UUID id = e.getKey();
            ServerBossEvent bar = e.getValue();
            boolean won = sideOf.getOrDefault(id, -1) == winningSide;
            int side = sideOf.getOrDefault(id, -1);
            bar.setName(Component.literal(won ? sideColor(side) + "§l胜利" : "§7对局结束"));
            bar.setColor(won ? bossColor(side) : BossEvent.BossBarColor.RED);
            bar.setProgress(1.0f);
        }
    }

    private BossEvent.BossBarColor bossColor(int side) {
        return switch (ffaColor(side)) {
            case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> BossEvent.BossBarColor.BLUE;
            case RED, DARK_RED, GOLD, YELLOW -> BossEvent.BossBarColor.RED;
            case GREEN, DARK_GREEN -> BossEvent.BossBarColor.GREEN;
            case LIGHT_PURPLE, DARK_PURPLE -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.WHITE;
        };
    }

    private void showPersonalDrawBars() {
        if (!usesPersonalBossBars()) {
            return;
        }
        for (ServerBossEvent bar : personalBossBars.values()) {
            bar.setName(Component.literal("§7§l平局"));
            bar.setColor(BossEvent.BossBarColor.WHITE);
            bar.setProgress(1.0f);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** 当前各方比分的紧凑展示，如 "队伍1 2 - 1 队伍2"。 */
    private String scoreLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < settings.sideCount(); i++) {
            if (i > 0) {
                sb.append(" §7- ");
            }
            sb.append(sideColor(i)).append(score.score(sideId(i)));
        }
        sb.append(" §7/ ").append(score.pointsToWin());
        return sb.toString();
    }

    private String plainScoreLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < settings.sideCount(); i++) {
            if (i > 0) {
                sb.append(" - ");
            }
            sb.append(score.score(sideId(i)));
        }
        sb.append(" / ").append(score.pointsToWin());
        return sb.toString();
    }

    private int matchDurationSeconds() {
        return (int) Math.max(0L, (server.getTickCount() - startedTick) / 20L);
    }

    private List<String> sideMemberNames(int side) {
        List<String> names = new ArrayList<>();
        if (side < 0 || side >= sides.size()) {
            return names;
        }
        for (UUID id : sides.get(side)) {
            names.add(nameOf(id));
        }
        return names;
    }

    private TopKiller topKiller() {
        UUID topId = null;
        int topKills = -1;
        for (Map.Entry<UUID, Integer> e : kills.entrySet()) {
            int value = e.getValue() == null ? 0 : e.getValue();
            if (topId == null || value > topKills
                    || (value == topKills && nameOf(e.getKey()).compareToIgnoreCase(nameOf(topId)) < 0)) {
                topId = e.getKey();
                topKills = value;
            }
        }
        if (topId == null) {
            return new TopKiller("暂无击杀王", 0);
        }
        return new TopKiller(nameOf(topId), Math.max(0, topKills));
    }

    private record TopKiller(String name, int kills) {
    }

    private int leadingScore() {
        int max = 0;
        for (int i = 0; i < settings.sideCount(); i++) {
            max = Math.max(max, score.score(sideId(i)));
        }
        return max;
    }

    private String roundLabel() {
        if (settings.scoringMode() == ScoringMode.HOT_ZONE) {
            return "热区";
        }
        if (settings.scoringMode() == ScoringMode.KILL_COUNT) {
            return "击杀战";
        }
        return "第 " + (leadingScore() + remainingLosses() + 1) + " 回合";
    }

    private int remainingLosses() {
        int sum = 0;
        for (int i = 0; i < settings.sideCount(); i++) {
            sum += score.score(sideId(i));
        }
        return sum - leadingScore();
    }

    private static String sideId(int index) {
        return "side_" + index;
    }

    private String sideLabel(int index) {
        if (usesPersonalBossBars()) {
            for (UUID id : sides.get(index)) {
                return sideColor(index) + nameOf(id);
            }
        }
        if (settings.teamSize() == 1 && settings.scoringMode() != ScoringMode.KILL_COUNT) {
            for (UUID id : sides.get(index)) {
                return sideColor(index) + nameOf(id);
            }
        }
        return "队伍 " + (index + 1);
    }

    // ---- 访问器 ----

    public String matchId() {
        return matchId;
    }

    public String arenaId() {
        return arena.arenaId();
    }

    public MatchSettings settings() {
        return settings;
    }

    public MatchPhase phase() {
        return phase;
    }

    public boolean isEnded() {
        return phase == MatchPhase.ENDED;
    }

    public Optional<Integer> winnerSide() {
        return winnerSide >= 0 ? Optional.of(winnerSide) : Optional.empty();
    }

    public boolean contains(UUID playerId) {
        return sideOf.containsKey(playerId);
    }

    public String displayName() {
        return settings.displayName();
    }

    public String targetText() {
        int pts = score.pointsToWin();
        if (settings.scoringMode() == ScoringMode.KILL_COUNT) {
            return pts + " 杀";
        }
        if (settings.scoringMode() == ScoringMode.HOT_ZONE) {
            return pts + " 分";
        }
        return pts + " 胜";
    }

    public int elapsedSeconds() {
        return (int) Math.max(0L, (server.getTickCount() - startedTick) / 20L);
    }

    public String participantNames() {
        List<String> names = new ArrayList<>();
        for (UUID id : sideOf.keySet()) {
            names.add(nameOf(id));
        }
        return String.join(", ", names);
    }

    public boolean canChangeLoadout(UUID playerId) {
        if (!sideOf.containsKey(playerId) || phase == MatchPhase.ENDED || phase == MatchPhase.MATCH_RESULT) {
            return false;
        }
        return deathCamView.containsKey(playerId) || !alive.contains(playerId);
    }
}
