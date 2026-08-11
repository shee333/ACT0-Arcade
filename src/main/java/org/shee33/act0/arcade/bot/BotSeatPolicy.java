package org.shee33.act0.arcade.bot;

import java.util.List;
import java.util.OptionalInt;

/**
 * bot 席位策略：决定"还能加几个 bot"与"真人补位时该踢掉哪个 bot"。MC-free，可单测。
 *
 * <p><b>为什么单独成类</b>：这两个判断是纯算术与排序，却直接决定玩家看到的房间人数是否
 * 自洽（点了加 bot 却没反应、真人加入后一边多一人）。放在 {@code RoomManager} 里就只能
 * 靠开服点鼠标验证，抽出来后可以用 JUnit 把满员、名池耗尽、阵营倾斜等边界一次钉死。
 *
 * <p><b>为什么真人补位要踢"人多那侧"的 bot</b>：真人会被对局层补进人少的一侧。若从人少侧
 * 踢，结果是少的更少、多的照旧；从人多侧踢，则一边 -1、另一边 +1，两侧同时收敛到均衡。
 * 这是"补位不破坏平衡性"在算法上的落点，对应 AGENTS.md 的平衡性原则。
 */
public final class BotSeatPolicy {

    private BotSeatPolicy() {
    }

    /**
     * 一个 bot 席位的可排序描述。
     *
     * @param order     加入房间的先后序号，越大越晚加入
     * @param sideIndex 所属阵营下标；等待期尚未分边时统一传 {@link #NO_SIDE}
     */
    public record BotSeat(int order, int sideIndex) {
    }

    /** 尚未分配阵营（房间等待期）。 */
    public static final int NO_SIDE = -1;

    /**
     * 还能再加入多少个 bot。
     *
     * <p>三个上限同时生效：房间剩余空位、名池剩余可用名。名池会耗尽是真实约束——
     * {@link BotNames#POOL_SIZE} 个名字要同时避开在线真人与已在场 bot，
     * 32 人房挤满时确实可能挑不出新名字，此时必须如实返回较小值而不是让调用方生成失败。
     *
     * @param memberCount 当前房间成员数（含真人与已在场 bot）
     * @param capacity    房间容量
     * @param freeNames   名池中当前可用的名字数量
     * @return 可追加的 bot 数量，永不为负
     */
    public static int addableCount(int memberCount, int capacity, int freeNames) {
        int freeSeats = capacity - memberCount;
        return Math.max(0, Math.min(freeSeats, Math.max(0, freeNames)));
    }

    /** 是否至少还能加入一个 bot。 */
    public static boolean canAdd(int memberCount, int capacity, int freeNames) {
        return addableCount(memberCount, capacity, freeNames) > 0;
    }

    /** 是否有 bot 可供移除。 */
    public static boolean canRemove(int botCount) {
        return botCount > 0;
    }

    /**
     * 真人补位时挑选被替换的 bot，返回其在 {@code bots} 中的下标。
     *
     * <p>规则：优先从"当前人数最多的阵营"里踢（理由见类头注释），同一阵营内踢最后加入的那个
     * ——后加入的 bot 在玩家心理上是"临时凑数"的，先撤它比撤开局就在的更符合直觉。
     * 若无阵营信息（房间等待期，{@link #NO_SIDE}），退化为纯粹的后进先出。
     *
     * @param bots       候选 bot 席位，可为空
     * @param sideCounts 各阵营当前总人数（含真人）；{@code null} 或空数组表示无阵营信息
     * @return 被选中的下标；{@code bots} 为空时返回 {@link OptionalInt#empty()}
     */
    public static OptionalInt pickKickIndex(List<BotSeat> bots, int[] sideCounts) {
        if (bots == null || bots.isEmpty()) {
            return OptionalInt.empty();
        }
        int targetSide = heaviestSideWithBot(bots, sideCounts);
        int bestIndex = -1;
        int bestOrder = Integer.MIN_VALUE;
        for (int i = 0; i < bots.size(); i++) {
            BotSeat seat = bots.get(i);
            if (targetSide != NO_SIDE && seat.sideIndex() != targetSide) {
                continue;
            }
            if (seat.order() > bestOrder) {
                bestOrder = seat.order();
                bestIndex = i;
            }
        }
        // targetSide 由"确实有 bot 的阵营"推出，故循环必然命中；兜底仅为防御异常输入。
        return bestIndex >= 0 ? OptionalInt.of(bestIndex) : OptionalInt.of(0);
    }

    /**
     * 找出"人数最多且其中确实有 bot"的阵营。
     *
     * <p>必须附加"其中确实有 bot"这一条：人数最多的一侧可能全是真人，此时踢不动任何人，
     * 若直接返回该侧会让 {@link #pickKickIndex} 空转，真人就永远补不进来。
     *
     * @return 目标阵营下标；无阵营信息或无可踢阵营时返回 {@link #NO_SIDE}
     */
    private static int heaviestSideWithBot(List<BotSeat> bots, int[] sideCounts) {
        if (sideCounts == null || sideCounts.length == 0) {
            return NO_SIDE;
        }
        int target = NO_SIDE;
        int best = Integer.MIN_VALUE;
        for (int side = 0; side < sideCounts.length; side++) {
            if (!hasBotOnSide(bots, side)) {
                continue;
            }
            if (sideCounts[side] > best) {
                best = sideCounts[side];
                target = side;
            }
        }
        return target;
    }

    private static boolean hasBotOnSide(List<BotSeat> bots, int side) {
        for (BotSeat seat : bots) {
            if (seat.sideIndex() == side) {
                return true;
            }
        }
        return false;
    }
}
