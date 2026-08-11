package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bot 席位策略（MC-free）单元测试：可加数量的三重上限，与真人补位时的踢人选择。
 */
class BotSeatPolicyTest {

    private static BotSeatPolicy.BotSeat seat(int order, int side) {
        return new BotSeatPolicy.BotSeat(order, side);
    }

    // ---------------- 可加数量 ----------------

    @Test
    void addableIsLimitedByFreeSeats() {
        assertEquals(5, BotSeatPolicy.addableCount(3, 8, 32));
    }

    @Test
    void addableIsLimitedByNamePool() {
        // 空位 30 个，但名池只剩 2 个可用名 → 只能加 2 个
        assertEquals(2, BotSeatPolicy.addableCount(2, 32, 2));
    }

    @Test
    void addableIsZeroWhenRoomFull() {
        assertEquals(0, BotSeatPolicy.addableCount(8, 8, 32));
        assertFalse(BotSeatPolicy.canAdd(8, 8, 32));
    }

    @Test
    void addableNeverNegativeWhenOverCapacity() {
        // 弹性模式中途补人可能让成员数暂时超过容量，此时必须返回 0 而非负数
        assertEquals(0, BotSeatPolicy.addableCount(10, 8, 32));
    }

    @Test
    void addableTreatsNegativeFreeNamesAsZero() {
        assertEquals(0, BotSeatPolicy.addableCount(1, 8, -3));
    }

    @Test
    void removableOnlyWhenBotsPresent() {
        assertTrue(BotSeatPolicy.canRemove(1));
        assertFalse(BotSeatPolicy.canRemove(0));
    }

    // ---------------- 踢人选择 ----------------

    @Test
    void kickPicksNothingWhenNoBots() {
        assertEquals(OptionalInt.empty(), BotSeatPolicy.pickKickIndex(List.of(), new int[]{2, 2}));
        assertEquals(OptionalInt.empty(), BotSeatPolicy.pickKickIndex(null, null));
    }

    @Test
    void kickIsLastInFirstOutWithoutSideInfo() {
        List<BotSeatPolicy.BotSeat> bots = List.of(
                seat(1, BotSeatPolicy.NO_SIDE),
                seat(7, BotSeatPolicy.NO_SIDE),
                seat(4, BotSeatPolicy.NO_SIDE));
        // 房间等待期无阵营，踢最后加入的（order 最大 = 下标 1）
        assertEquals(OptionalInt.of(1), BotSeatPolicy.pickKickIndex(bots, null));
        assertEquals(OptionalInt.of(1), BotSeatPolicy.pickKickIndex(bots, new int[0]));
    }

    @Test
    void kickPrefersHeaviestSide() {
        List<BotSeatPolicy.BotSeat> bots = List.of(
                seat(1, 0),
                seat(2, 1),
                seat(3, 0));
        // 阵营 0 有 5 人、阵营 1 有 3 人 → 从 0 侧踢，且取 order 最大的（下标 2）
        assertEquals(OptionalInt.of(2), BotSeatPolicy.pickKickIndex(bots, new int[]{5, 3}));
    }

    @Test
    void kickSkipsHeaviestSideWhenItHasNoBots() {
        List<BotSeatPolicy.BotSeat> bots = List.of(
                seat(1, 1),
                seat(9, 1));
        // 人最多的 0 侧全是真人，踢不动；必须退到确实有 bot 的 1 侧，否则真人永远补不进来
        assertEquals(OptionalInt.of(1), BotSeatPolicy.pickKickIndex(bots, new int[]{6, 2}));
    }

    @Test
    void kickFallsBackToLastAddedOnTiedSides() {
        List<BotSeatPolicy.BotSeat> bots = List.of(
                seat(5, 0),
                seat(8, 1));
        // 两侧人数相同时取先遍历到的阵营 0，其中 order 最大者为下标 0
        assertEquals(OptionalInt.of(0), BotSeatPolicy.pickKickIndex(bots, new int[]{4, 4}));
    }

    @Test
    void kickHandlesBotsOnSideBeyondSideCounts() {
        List<BotSeatPolicy.BotSeat> bots = List.of(seat(3, 5));
        // bot 记录的阵营下标超出 sideCounts 长度（异常输入）时仍须给出可执行结果
        assertEquals(OptionalInt.of(0), BotSeatPolicy.pickKickIndex(bots, new int[]{1, 1}));
    }
}
