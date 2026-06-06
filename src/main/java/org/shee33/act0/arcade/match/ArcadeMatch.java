package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.mode.MatchSettings;
import org.shee33.act0.arcade.mode.ScoringMode;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.round.MatchPhase;
import org.shee33.act0.arcade.round.MatchScore;
import org.shee33.act0.arcade.round.PhaseTimer;
import org.shee33.act0.arcade.round.RespawnPolicy;

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
    /** 复活倒计时上次播报的整秒，用于逐秒 title + 音符盒去重（按玩家）。 */
    private final Map<UUID, Integer> respawnLastSecond = new HashMap<>();
    /** 开局倒计时期间锁定的位置（防乱跑）。 */
    private final Map<UUID, Vec3> countdownLock = new HashMap<>();
    /** 死亡观战期间锁定相机的位置与朝向（钉在死亡点，禁止自由飞）。 */
    private final Map<UUID, DeathView> deathCamView = new HashMap<>();

    /** 死亡补给箱标记 NBT 键：拾取后补满虚拟弹药。 */
    public static final String AMMO_CRATE_KEY = "Act0AmmoCrate";

    private MatchPhase phase = MatchPhase.WAITING;
    private int winnerSide = -1;
    /** 倒计时/复活阶段的总时长（刻），用于 Boss 血条进度归一化。 */
    private int phaseTotalTicks = 1;
    /** 倒计时阶段上次播报的整秒数，用于逐秒 3-2-1 提示去重。 */
    private int lastCountdownSecond = -1;
    /** 对局限时剩余刻（{@code <0} 表示不限时）。 */
    private int matchClockTicks = -1;
    private boolean draw = false;

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
                bossBar.addPlayer(player);
                sidebar.showTo(player);
            }
        }
        bossBar.setVisible(true);
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
            spawnAtSide(player, sideOf.get(id));
            equip(player);
            player.setHealth(player.getMaxHealth());
            applyCountdownLock(player);
        }
        phase = MatchPhase.COUNTDOWN;
        phaseTotalTicks = Math.max(1, settings.countdownSeconds() * 20);
        lastCountdownSecond = -1;
        timer.startSeconds(settings.countdownSeconds());
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
                    showTitle("§a§l战斗开始！", "", 2, 20, 8);
                    playToAll(SoundEvents.PLAYER_LEVELUP, 1.0f);
                    broadcast("§a战斗开始！");
                }
                updateBossBar();
            }
            case COMBAT -> {
                enforceDeathCam();
                tickRespawns();
                if (tickMatchClock()) {
                    return; // 限时到，已收尾
                }
                updateBossBar();
            }
            case ROUND_RESULT -> {
                enforceDeathCam();
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
            respawn(player, sideOf.getOrDefault(id, 0));
            equip(player);
            player.setHealth(player.getMaxHealth());
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
        ServerPlayer victim = player(victimId);
        // 仅对局战斗阶段：在死亡点掉落补给箱（拾取恢复弹药），且必须在传送前掉落。
        if (victim != null && phase == MatchPhase.COMBAT) {
            dropAmmoCrate(victim);
        }
        // 立即复活以阻止原版死亡：保持同一 ServerPlayer 实例，血条/计分引用始终有效。
        if (victim != null) {
            victim.setHealth(victim.getMaxHealth());
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
        } else {
            handleRoundElimination(victimId);
        }
        return true;
    }

    /** 在玩家死亡点掉落一个补给箱（箱子物品 + 标记 NBT），拾取后补满虚拟弹药。 */
    private void dropAmmoCrate(ServerPlayer victim) {
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
    }

    private void handleKillScoring(UUID victimId, UUID killerId) {
        if (killerId != null && sideOf.containsKey(killerId)
                && !sideOf.get(killerId).equals(sideOf.get(victimId))) {
            int killerSide = sideOf.get(killerId);
            int total = score.addPoint(sideId(killerSide));
            kills.merge(killerId, 1, Integer::sum);
            broadcast("§b" + nameOf(killerId) + " §7击杀了 §b" + nameOf(victimId)
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
            enterSpectator(victim);
        }
        PhaseTimer t = new PhaseTimer();
        t.startSeconds(settings.reEquipProtectionSeconds());
        respawnTimers.put(victimId, t);
        respawnLastSecond.put(victimId, -1);
    }

    private void handleRoundElimination(UUID victimId) {
        alive.remove(victimId);
        ServerPlayer victim = player(victimId);
        if (victim != null) {
            victim.getInventory().clearContent();
            enterSpectator(victim);
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
        showTitle("§6§l胜利", "§e" + sideLabel(side) + " §7获胜！", 5, 60, 15);
        bossBar.setName(Component.literal("§6§l" + sideLabel(side) + " 获胜！"));
        bossBar.setProgress(1.0f);
        bossBar.setColor(BossEvent.BossBarColor.GREEN);
        broadcast("§6§l胜利：" + sideLabel(side) + "！");
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
        bossBar.setName(Component.literal("§7§l平局"));
        bossBar.setProgress(1.0f);
        bossBar.setColor(BossEvent.BossBarColor.WHITE);
        broadcast("§7§l平局！");
        updateSidebar();
    }

    private void finish() {
        releaseCountdownLock();
        respawnTimers.clear();
        respawnLastSecond.clear();
        deathCamView.clear();
        for (UUID id : sideOf.keySet()) {
            ServerPlayer player = player(id);
            if (player != null) {
                exitSpectator(player);
                TeleportHelper.teleport(player, arena.returnSpawn());
                player.getInventory().clearContent();
                sidebar.hideFrom(player);
                bossBar.removePlayer(player);
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
        phase = MatchPhase.ENDED;
    }

    /**
     * 处理一名参战玩家的掉线：<b>保留其坐位</b>（重连可归位），仅将其移出存活/复活/血条/计分板。
     * 若掉线使决斗回合只剩一方则照常结算回合；若使整局只剩一方（或无人）则自动收尾。
     */
    public void onPlayerLeft(UUID playerId) {
        if (!sideOf.containsKey(playerId)) {
            return;
        }
        if (phase == MatchPhase.ENDED) {
            return;
        }
        disconnected.add(playerId);
        respawnTimers.remove(playerId);
        respawnLastSecond.remove(playerId);
        countdownLock.remove(playerId);
        deathCamView.remove(playerId);
        boolean wasAlive = alive.remove(playerId);
        ServerPlayer p = player(playerId);
        if (p != null) {
            exitSpectator(p);
            bossBar.removePlayer(p);
            sidebar.hideFrom(p);
        }
        if (settings.scoringMode() != ScoringMode.KILL_COUNT && wasAlive && phase == MatchPhase.COMBAT) {
            evaluateRoundElimination();
        }
        broadcast("§7" + nameOf(playerId) + " 掉线（坐位保留，重连可归位）。");
        checkLiveness();
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
        bossBar.addPlayer(player);
        sidebar.showTo(player);
        int side = sideOf.getOrDefault(id, 0);
        if (phase == MatchPhase.COMBAT || phase == MatchPhase.COUNTDOWN) {
            exitSpectator(player);
            spawnAtSide(player, side);
            equip(player);
            player.setHealth(player.getMaxHealth());
            alive.add(id);
            if (phase == MatchPhase.COUNTDOWN) {
                applyCountdownLock(player);
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
        int side = smallestSide();
        sides.get(side).add(id);
        sideOf.put(id, side);
        kills.put(id, 0);
        bossBar.addPlayer(player);
        sidebar.showTo(player);
        if (phase == MatchPhase.COMBAT || phase == MatchPhase.COUNTDOWN) {
            spawnAtSide(player, side);
            equip(player);
            player.setHealth(player.getMaxHealth());
            alive.add(id);
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

    private void respawn(ServerPlayer player, int side) {
        switch (settings.respawnPolicy()) {
            case FIXED_SPAWN -> TeleportHelper.teleport(player, arena.sideSpawn(side));
            case NEAR_TEAMMATE -> {
                SpawnPoint near = livingTeammateSpawn(player.getUUID(), side);
                TeleportHelper.teleport(player, near != null ? near : arena.sideSpawn(side));
            }
            case RANDOM -> TeleportHelper.teleport(player, arena.randomSpawn(random));
        }
    }

    private SpawnPoint livingTeammateSpawn(UUID self, int side) {
        for (UUID mate : sides.get(side)) {
            if (mate.equals(self)) {
                continue;
            }
            ServerPlayer mp = player(mate);
            if (mp != null && mp.isAlive()) {
                String dim = mp.level().dimension().location().toString();
                return new SpawnPoint(dim, mp.getX(), mp.getY(), mp.getZ(),
                        mp.getYRot(), mp.getXRot());
            }
        }
        return null;
    }

    // ---- 观察者 / 开局移动锁 ----

    /** 进入观察者视角（死亡等待时），记录原始游戏模式以便复活时还原，并锁定死亡相机。 */
    private void enterSpectator(ServerPlayer player) {
        GameType current = player.gameMode.getGameModeForPlayer();
        if (current != GameType.SPECTATOR) {
            originalGameMode.put(player.getUUID(), current);
        }
        // 记录死亡相机点：站在死亡点上方略高、俯视，营造"钉在死亡现场"的固定视角。
        deathCamView.put(player.getUUID(), new DeathView(
                player.getX(), player.getEyeY() + 0.6, player.getZ(), player.getYRot(), 35f));
        player.setGameMode(GameType.SPECTATOR);
        ArcadeNetwork.sendDeathCam(player, true);
    }

    /** 退出观察者视角，还原至记录的原始游戏模式（默认生存），清除死亡相机。 */
    private void exitSpectator(ServerPlayer player) {
        deathCamView.remove(player.getUUID());
        ArcadeNetwork.sendDeathCam(player, false);
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            return;
        }
        GameType original = originalGameMode.getOrDefault(player.getUUID(), GameType.SURVIVAL);
        player.setGameMode(original);
    }

    /** 死亡观战每刻把旁观相机钉回死亡点，禁止玩家自由飞行观战。 */
    private void enforceDeathCam() {
        if (deathCamView.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, DeathView> e : deathCamView.entrySet()) {
            ServerPlayer p = player(e.getKey());
            if (p == null) {
                continue;
            }
            DeathView v = e.getValue();
            double dx = p.getX() - v.x();
            double dz = p.getZ() - v.z();
            if (dx * dx + dz * dz > 0.04 || Math.abs(p.getY() - v.y()) > 0.5) {
                p.connection.teleport(v.x(), v.y(), v.z(), p.getYRot(), p.getXRot());
                p.setDeltaMovement(0.0, 0.0, 0.0);
            }
        }
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
            return;
        }
        Loadout loadout = loadoutProvider.apply(player.getUUID());
        if (loadout == null) {
            return;
        }
        Set<String> unlocked = unlocksProvider.apply(player.getUUID());
        applier.apply(player, loadout, settings.ruleset(), unlocked, true);
    }

    /**
     * 随机武器：忽略玩家配装，从注册表按槽位随机抽取主武器+副武器并发放（含元子弹）。
     * 近战与投掷物沿用玩家配装中的选择（若有）。每次重生/回合都会重新随机。
     */
    private void equipRandom(ServerPlayer player) {
        PlayerClassType cls = PlayerClassType.defaultClass();
        Loadout base = loadoutProvider.apply(player.getUUID());
        if (base != null) {
            cls = base.classType();
        }
        Loadout temp = new Loadout("随机武器", cls);
        Set<String> grant = new LinkedHashSet<>();

        LoadoutItem primary = randomItemForSlot(LoadoutSlot.PRIMARY_WEAPON, cls);
        if (primary != null) {
            temp.setSlot(LoadoutSlot.PRIMARY_WEAPON, primary.key());
            grant.add(primary.key());
        }
        LoadoutItem secondary = randomItemForSlot(LoadoutSlot.SECONDARY_WEAPON, cls);
        if (secondary != null) {
            temp.setSlot(LoadoutSlot.SECONDARY_WEAPON, secondary.key());
            grant.add(secondary.key());
        }
        // 近战/投掷沿用玩家配装（也加入授权集，避免被判未解锁）
        if (base != null) {
            for (LoadoutSlot slot : new LoadoutSlot[]{LoadoutSlot.MELEE, LoadoutSlot.THROWABLE}) {
                base.slotItemKey(slot).ifPresent(key -> {
                    temp.setSlot(slot, key);
                    grant.add(key);
                });
            }
        }
        applier.apply(player, temp, settings.ruleset(), grant, true);

        // 告知玩家本次抽到的武器，避免"不知道这局是随机武器"
        String primaryName = primary != null ? primary.displayName() : "无";
        String secondaryName = secondary != null ? secondary.displayName() : "无";
        actionBar(player.getUUID(), "§6随机武器 §7» §f" + primaryName + " §8/ §f" + secondaryName);
        playTo(player.getUUID(), SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f);
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
                + (settings.scoringMode() == ScoringMode.KILL_COUNT ? " 击杀" : " 回合"));
        if (matchClockTicks >= 0) {
            lines.add("§7剩余: §f" + formatClock(matchClockTicks));
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
                lines.add("§f" + rank + ". §e" + nameOf(id) + " §7- §a" + kills.getOrDefault(id, 0) + tag);
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
            sb.append("§f").append(score.score(sideId(i)));
        }
        sb.append(" §7/ ").append(score.pointsToWin());
        return sb.toString();
    }

    private int leadingScore() {
        int max = 0;
        for (int i = 0; i < settings.sideCount(); i++) {
            max = Math.max(max, score.score(sideId(i)));
        }
        return max;
    }

    private String roundLabel() {
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
        if (settings.teamSize() == 1 && settings.scoringMode() != ScoringMode.KILL_COUNT) {
            for (UUID id : sides.get(index)) {
                return nameOf(id);
            }
        }
        return "队伍 " + (index + 1);
    }

    // ---- 访问器 ----

    public String matchId() {
        return matchId;
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
}
