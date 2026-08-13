package org.shee33.act0.arcade.bot.mc;

import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.arena.HotZoneArea;
import org.shee33.act0.arcade.bot.BotMode;
import org.shee33.act0.arcade.bot.ModeTactics;
import org.shee33.act0.arcade.match.ArcadeMatch;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个 bot 当前所处对局的只读快照：模式、阵营、队友、目标点、血量比例。
 *
 * <p><b>为什么是快照而不是长期持有的引用。</b>bot 的生命周期与对局并不重合——它可能在对局外被
 * {@code /arcade bot spawn} 直接生成、可能在对局结束后仍在场。持有引用就要处理"引用已失效"的所有分支；
 * 每 tick 重建一个不可变快照则让"不在对局中"退化成一个 {@code null}，调用方只需一次判空。
 *
 * <p>查找路径沿用 {@code BotHostility} 与 {@code BotPatrol} 已在用的
 * {@code Act0Arcade.services().matches().matchOf(uuid)}，不新增对象图穿线。
 */
final class BotMatchContext {

    private final ArcadeMatch match;
    private final BotPlayer bot;
    private final UUID selfId;
    private final BotMode mode;
    private final ModeTactics tactics;
    private final int sideIndex;
    private final List<UUID> teammates;
    @Nullable
    private final HotZoneArea objectiveArea;
    private final float healthFraction;

    private BotMatchContext(ArcadeMatch match, BotPlayer bot, UUID selfId, BotMode mode,
                            ModeTactics tactics,
                            int sideIndex, List<UUID> teammates,
                            @Nullable HotZoneArea objectiveArea, float healthFraction) {
        this.match = match;
        this.bot = bot;
        this.selfId = selfId;
        this.mode = mode;
        this.tactics = tactics;
        this.sideIndex = sideIndex;
        this.teammates = teammates;
        this.objectiveArea = objectiveArea;
        this.healthFraction = healthFraction;
    }

    /**
     * 为该 bot 建立当前对局快照；不在任何进行中的对局里则返回 {@code null}。
     */
    @Nullable
    static BotMatchContext of(BotPlayer bot) {
        ArcadeMatch match = Act0Arcade.services().matches().matchOf(bot.getUUID());
        if (match == null || match.isEnded()) {
            return null;
        }
        int side = match.sideIndexOf(bot.getUUID());
        BotMode mode = BotMode.fromModeId(match.settings().modeId());
        List<UUID> mates = new ArrayList<>();
        for (UUID id : match.playersOnSide(side)) {
            if (!id.equals(bot.getUUID())) {
                mates.add(id);
            }
        }
        // 稳定排序：角色分配靠序号，序号必须跨 tick 一致，否则压制/绕侧会每 tick 重排。
        mates.sort(UUID::compareTo);
        float max = Math.max(1.0F, bot.getMaxHealth());
        return new BotMatchContext(match, bot, bot.getUUID(), mode, ModeTactics.forMode(mode),
                side, mates, objectiveOf(match), bot.getHealth() / max);
    }

    /**
     * 本模式当前应当争夺的地图目标点；无目标模式返回 {@code null}。
     *
     * <p><b>预告期直接切到下一个热区</b>，而不是等迁移发生：玩家看到白色预告线框就会提前转移，
     * bot 若等到迁移瞬间才动身，就永远比玩家慢一整段路程，热区会退化成"玩家白拿分"。
     */
    @Nullable
    private static HotZoneArea objectiveOf(ArcadeMatch match) {
        if (!match.isHotZoneMode()) {
            return null;
        }
        HotZoneArea upcoming = match.upcomingHotZone();
        return upcoming != null ? upcoming : match.activeHotZone();
    }

    ArcadeMatch match() {
        return match;
    }

    BotMode mode() {
        return mode;
    }

    ModeTactics tactics() {
        return tactics;
    }

    int sideIndex() {
        return sideIndex;
    }

    /** 同阵营的其他参战者（含真人与 bot），按 UUID 稳定排序，不含自己。 */
    List<UUID> teammates() {
        return teammates;
    }

    /** 当前应当争夺的热区本体；无目标模式为 {@code null}。 */
    @Nullable
    HotZoneArea objectiveArea() {
        return objectiveArea;
    }

    /** 目标点的寻路落点（区域中心的地面高度）；无目标模式为 {@code null}。 */
    @Nullable
    Vec3 objective() {
        return objectiveArea == null ? null
                : new Vec3(objectiveArea.centerX(), objectiveArea.floorY(), objectiveArea.centerZ());
    }

    /** bot 当前是否站在目标区域内。 */
    boolean insideObjective() {
        return objectiveArea != null && objectiveArea.contains(
                bot.level().dimension().location().toString(), bot.getX(), bot.getY(), bot.getZ());
    }

    float healthFraction() {
        return healthFraction;
    }

    /**
     * 本 bot 在队内的稳定序号（0 起），供 {@code SquadTactics} 分配压制／绕侧角色。
     *
     * <p>定义为"UUID 排在自己前面的队友数"。UUID 由 bot 名字确定性派生（见
     * {@code BotNames#uuidOf}），故同一批 bot 的角色分配跨重启可复现；队友阵亡退场时序号会
     * 紧凑地前移，不会留下空洞导致全队都变成绕侧。
     */
    int squadRank() {
        int rank = 0;
        for (UUID mate : teammates) {
            if (mate.compareTo(selfId) < 0) {
                rank++;
            }
        }
        return rank;
    }
}
