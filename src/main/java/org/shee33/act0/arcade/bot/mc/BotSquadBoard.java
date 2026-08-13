package org.shee33.act0.arcade.bot.mc;

import org.shee33.act0.arcade.bot.SquadIntel;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 按「对局 × 阵营」划分的小队共享黑板：谁在打谁，以及限时限精度的接触情报。
 *
 * <p><b>为什么需要一块共享状态。</b>集火要求 A 知道 B 在打谁，情报共享要求 A 能读到 B 看见了什么。
 * 这类信息无法从单个 bot 自身的状态推出，必须有一处共同可见的地方。按「对局 × 阵营」分桶而不是
 * 全局一张表，是因为敌方的交火登记对我方毫无用处，混在一起还会把敌人的目标也算进"队友已在交火"。
 *
 * <p><b>真人队友不会向黑板报告。</b>登记只由 bot 自己写入，因此人机混队时 bot 之间会集火，
 * 但不会"读到"真人队友在打谁——那需要窥探真人的准星，既做不到也不该做。
 *
 * <p>单例、仅在服务端主线程访问（与 {@code BotManager} 同一 tick 循环），故用普通 {@code HashMap}。
 */
final class BotSquadBoard {

    static final BotSquadBoard INSTANCE = new BotSquadBoard();

    private record Key(String matchId, int side) {
    }

    /** 每个阵营一份情报板。 */
    private final Map<Key, SquadIntel> intel = new HashMap<>();

    /** 每个阵营内 bot → 它当前的交火目标。 */
    private final Map<Key, Map<UUID, UUID>> engagements = new HashMap<>();

    private BotSquadBoard() {
    }

    /**
     * 登记本 bot 的当前交火目标；{@code targetId} 为 {@code null} 表示脱火。
     */
    void reportEngagement(String matchId, int side, UUID botId, @Nullable UUID targetId) {
        Map<UUID, UUID> perSide = engagements.computeIfAbsent(new Key(matchId, side),
                k -> new HashMap<>());
        if (targetId == null) {
            perSide.remove(botId);
        } else {
            perSide.put(botId, targetId);
        }
    }

    /**
     * 是否有<b>别的</b>队友已经在打这个目标。
     *
     * <p>排除自己是关键：否则 bot 会因为"我自己在打他"而给当前目标叠一次集火加成，与黏滞重复计权，
     * 把本该只影响初次选目标的机制变成"锁死第一个目标永不切换"。
     */
    boolean teammateEngaging(String matchId, int side, UUID askerId, UUID targetId) {
        Map<UUID, UUID> perSide = engagements.get(new Key(matchId, side));
        if (perSide == null || targetId == null) {
            return false;
        }
        for (Map.Entry<UUID, UUID> e : perSide.entrySet()) {
            if (!e.getKey().equals(askerId) && targetId.equals(e.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 登记一次目视接触，坐标由 {@link SquadIntel} 量化后存入。 */
    void reportSighting(String matchId, int side, UUID enemyId,
                        double x, double y, double z, long tick) {
        intel.computeIfAbsent(new Key(matchId, side), k -> new SquadIntel())
                .report(enemyId, x, y, z, tick);
    }

    /**
     * 取距离给定位置最近的一条未过期共享情报。
     *
     * <p>取最近而非最新：bot 需要的是"我该去哪"，跑向半张地图外那个刚被看到的敌人不如就近处理。
     */
    Optional<SquadIntel.Contact> nearestContact(String matchId, int side,
                                                double x, double z, long tick) {
        SquadIntel board = intel.get(new Key(matchId, side));
        if (board == null) {
            return Optional.empty();
        }
        SquadIntel.Contact best = null;
        double bestSq = Double.MAX_VALUE;
        for (SquadIntel.Contact contact : board.active(tick)) {
            double dx = contact.x() - x;
            double dz = contact.z() - z;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = contact;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 周期性清理过期情报。
     *
     * <p>{@link SquadIntel#lookup} 与 {@code active} 本就会过滤过期项，行为不依赖这次清理；但条目
     * 本身不会自行消失，不清就是一处随对局时长线性增长的泄漏。
     */
    void prune(long tick) {
        for (SquadIntel board : intel.values()) {
            board.pruneExpired(tick);
        }
    }

    /**
     * 对局结束时清空该局的全部分桶。
     *
     * <p>不清则每一局都会在两个 map 里留下一组永不再被读取的键——服务器长期运行下这是纯粹的累积。
     */
    void clearMatch(String matchId) {
        removeByMatch(intel.keySet().iterator(), matchId);
        removeByMatch(engagements.keySet().iterator(), matchId);
    }

    private static void removeByMatch(Iterator<Key> keys, String matchId) {
        while (keys.hasNext()) {
            if (keys.next().matchId().equals(matchId)) {
                keys.remove();
            }
        }
    }

    /** 全量清空（服务器停止 / 测试隔离）。 */
    void clear() {
        intel.clear();
        engagements.clear();
    }
}
