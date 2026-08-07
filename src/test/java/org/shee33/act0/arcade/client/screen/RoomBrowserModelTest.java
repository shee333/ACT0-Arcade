package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomBrowserModel} 单元测试：房间浏览器的全部纯逻辑 —— 状态三态派生、标签筛选判据、
 * 抽屉设定键值对生成、摇头位移公式、滚轮方向判定，以及取代"伪随机跳动"的新旧两帧快照差分。
 */
class RoomBrowserModelTest {

    private static final float EPS = 1e-5f;

    // ============================================================
    // 状态派生：已满 > 进行中 > 等待中
    // ============================================================

    @Test
    void status_fullTakesPrecedenceOverRunning() {
        assertEquals(RoomBrowserModel.Status.FULL, RoomBrowserModel.status(8, 8, true));
        assertEquals(RoomBrowserModel.Status.FULL, RoomBrowserModel.status(9, 8, false));
    }

    @Test
    void status_runningWhenNotFull() {
        assertEquals(RoomBrowserModel.Status.RUNNING, RoomBrowserModel.status(6, 8, true));
    }

    @Test
    void status_waitingWhenNeitherFullNorRunning() {
        assertEquals(RoomBrowserModel.Status.WAITING, RoomBrowserModel.status(1, 8, false));
    }

    @Test
    void status_zeroCapacityIsNeverFull() {
        assertEquals(RoomBrowserModel.Status.WAITING, RoomBrowserModel.status(0, 0, false));
        assertEquals(RoomBrowserModel.Status.RUNNING, RoomBrowserModel.status(0, 0, true));
    }

    @Test
    void statusColors_areTheThreeSpecColours() {
        assertEquals(0xFF6EE27E, RoomBrowserModel.Status.WAITING.color());
        assertEquals(0xFFFFD76A, RoomBrowserModel.Status.RUNNING.color());
        assertEquals(0x59E8EDF2, RoomBrowserModel.Status.FULL.color());
    }

    @Test
    void statusLabels_areTheThreeSpecLabels() {
        assertEquals("等待中", RoomBrowserModel.Status.WAITING.label());
        assertEquals("进行中", RoomBrowserModel.Status.RUNNING.label());
        assertEquals("已满", RoomBrowserModel.Status.FULL.label());
    }

    @Test
    void fullStatus_dimsTextTo035() {
        assertEquals(0.35f, RoomBrowserModel.Status.FULL.textAlpha(), EPS);
        assertEquals(1f, RoomBrowserModel.Status.WAITING.textAlpha(), EPS);
        assertEquals(1f, RoomBrowserModel.Status.RUNNING.textAlpha(), EPS);
    }

    // ============================================================
    // 标签筛选
    // ============================================================

    @Test
    void tabAll_passesEverything() {
        assertTrue(RoomBrowserModel.pass(RoomBrowserModel.TAB_ALL, 8, 8, true, false));
        assertTrue(RoomBrowserModel.pass(RoomBrowserModel.TAB_ALL, 0, 8, false, false));
    }

    @Test
    void tabWaiting_requiresNotRunningAndNotFull() {
        assertTrue(RoomBrowserModel.pass(RoomBrowserModel.TAB_WAITING, 3, 8, false, false));
        assertFalse(RoomBrowserModel.pass(RoomBrowserModel.TAB_WAITING, 8, 8, false, false));
        assertFalse(RoomBrowserModel.pass(RoomBrowserModel.TAB_WAITING, 3, 8, true, false));
    }

    @Test
    void tabRunning_requiresRunning() {
        assertTrue(RoomBrowserModel.pass(RoomBrowserModel.TAB_RUNNING, 8, 8, true, false));
        assertFalse(RoomBrowserModel.pass(RoomBrowserModel.TAB_RUNNING, 3, 8, false, false));
    }

    @Test
    void tabMine_requiresMembership() {
        assertTrue(RoomBrowserModel.pass(RoomBrowserModel.TAB_MINE, 3, 8, false, true));
        assertFalse(RoomBrowserModel.pass(RoomBrowserModel.TAB_MINE, 3, 8, false, false));
    }

    @Test
    void tabLabels_areFourAndMatchTabConstants() {
        assertEquals(4, RoomBrowserModel.TAB_LABELS.length);
        assertEquals("全部", RoomBrowserModel.TAB_LABELS[RoomBrowserModel.TAB_ALL]);
        assertEquals("等待中", RoomBrowserModel.TAB_LABELS[RoomBrowserModel.TAB_WAITING]);
        assertEquals("进行中", RoomBrowserModel.TAB_LABELS[RoomBrowserModel.TAB_RUNNING]);
        assertEquals("我的", RoomBrowserModel.TAB_LABELS[RoomBrowserModel.TAB_MINE]);
    }

    // ============================================================
    // 抽屉设定键值对（DSET）
    // ============================================================

    @Test
    void targetKey_isDerivedFromModeSemantics() {
        assertEquals("击杀目标", RoomBrowserModel.targetKey("team_deathmatch"));
        assertEquals("击杀目标", RoomBrowserModel.targetKey("free_for_all"));
        assertEquals("分数目标", RoomBrowserModel.targetKey("hot_zone"));
        assertEquals("等级目标", RoomBrowserModel.targetKey("arms_race"));
        assertEquals("等级目标", RoomBrowserModel.targetKey("team_arms_race"));
        assertEquals("回合目标", RoomBrowserModel.targetKey("duel_1v1"));
        assertEquals("回合目标", RoomBrowserModel.targetKey("duel_2v2"));
        assertEquals("回合目标", RoomBrowserModel.targetKey("jump_sniper"));
    }

    @Test
    void targetKey_fallsBackForUnknownOrMissingMode() {
        assertEquals("计分目标", RoomBrowserModel.targetKey("something_new"));
        assertEquals("计分目标", RoomBrowserModel.targetKey(null));
        assertEquals("计分目标", RoomBrowserModel.targetKey(""));
    }

    @Test
    void timeLimitText_waitingRoomShowsWholeMinutes() {
        assertEquals("15 分钟", RoomBrowserModel.timeLimitText(900, false, 0));
        assertEquals("45 秒", RoomBrowserModel.timeLimitText(45, false, 0));
    }

    @Test
    void timeLimitText_runningRoomShowsRemaining() {
        assertEquals("剩余 6:00", RoomBrowserModel.timeLimitText(900, true, 540));
        assertEquals("剩余 0:00", RoomBrowserModel.timeLimitText(900, true, 5000));
    }

    @Test
    void timeLimitText_zeroLimitIsUnlimited() {
        assertEquals("不限时", RoomBrowserModel.timeLimitText(0, false, 0));
        assertEquals("不限时 · 已进行 2:05", RoomBrowserModel.timeLimitText(0, true, 125));
    }

    @Test
    void clock_padsSecondsToTwoDigits() {
        assertEquals("0:00", RoomBrowserModel.clock(0));
        assertEquals("0:09", RoomBrowserModel.clock(9));
        assertEquals("1:00", RoomBrowserModel.clock(60));
        assertEquals("10:59", RoomBrowserModel.clock(659));
        assertEquals("0:00", RoomBrowserModel.clock(-5));
    }

    @Test
    void settings_areSixRowsInSpecOrder() {
        List<RoomBrowserModel.Setting> rows = RoomBrowserModel.settings(
                "hot_zone", "热区", "沙漠废墟", "先到 150 分", 600, false, 0, true, false);

        assertEquals(6, rows.size());
        assertEquals(new RoomBrowserModel.Setting("模式", "热区"), rows.get(0));
        assertEquals(new RoomBrowserModel.Setting("地图", "沙漠废墟"), rows.get(1));
        assertEquals(new RoomBrowserModel.Setting("分数目标", "先到 150 分"), rows.get(2));
        assertEquals(new RoomBrowserModel.Setting("对局限时", "10 分钟"), rows.get(3));
        assertEquals(new RoomBrowserModel.Setting("随机武器", "开"), rows.get(4));
        assertEquals(new RoomBrowserModel.Setting("友伤", "关"), rows.get(5));
    }

    @Test
    void settings_blankValuesBecomeEmDash() {
        List<RoomBrowserModel.Setting> rows = RoomBrowserModel.settings(
                "", "", "  ", null, 0, false, 0, false, true);

        assertEquals("—", rows.get(0).value());
        assertEquals("—", rows.get(1).value());
        assertEquals("—", rows.get(2).value());
        assertEquals("开", rows.get(5).value());
    }

    // ============================================================
    // 摇头位移 / 滚轮方向
    // ============================================================

    @Test
    void shakeOffset_startsAndEndsAtZero() {
        assertEquals(0f, RoomBrowserModel.shakeOffset(0f), EPS);
        assertEquals(0f, RoomBrowserModel.shakeOffset(1f), EPS);
    }

    @Test
    void shakeOffset_reachesBothDirectionsAndDecays() {
        float firstPeak = RoomBrowserModel.shakeOffset(0.125f);
        float firstTrough = RoomBrowserModel.shakeOffset(0.375f);
        float lastPeak = RoomBrowserModel.shakeOffset(0.625f);

        assertTrue(firstPeak > 0f, "first quarter swings positive");
        assertTrue(firstTrough < 0f, "second swing goes negative");
        assertTrue(Math.abs(lastPeak) < Math.abs(firstPeak), "amplitude decays via (1-v)");
        assertTrue(Math.abs(firstPeak) <= 4f, "amplitude never exceeds the 4px spec bound");
    }

    @Test
    void shakeOffset_clampsOutOfRangeProgress() {
        assertEquals(0f, RoomBrowserModel.shakeOffset(-1f), EPS);
        assertEquals(0f, RoomBrowserModel.shakeOffset(2f), EPS);
    }

    @Test
    void rollDir_encodesIncreaseUpDecreaseDown() {
        assertEquals(1, RoomBrowserModel.rollDir(3, 4));
        assertEquals(-1, RoomBrowserModel.rollDir(4, 3));
        assertEquals(0, RoomBrowserModel.rollDir(4, 4));
    }

    // ============================================================
    // 新旧两帧快照差分（取代原型的伪随机跳动）
    // ============================================================

    private static RoomBrowserModel.Snapshot snap(String id, int cur, int max, boolean run) {
        return new RoomBrowserModel.Snapshot(id, cur, max, run);
    }

    @Test
    void diff_reportsCountIncreaseWithUpwardRoll() {
        List<RoomBrowserModel.RowDelta> deltas = RoomBrowserModel.diff(
                List.of(snap("a", 3, 8, false)),
                List.of(snap("a", 4, 8, false)));

        assertEquals(1, deltas.size());
        assertEquals("a", deltas.get(0).roomId());
        assertEquals(1, deltas.get(0).countDir());
        assertFalse(deltas.get(0).statusChanged());
    }

    @Test
    void diff_reportsCountDecreaseWithDownwardRoll() {
        List<RoomBrowserModel.RowDelta> deltas = RoomBrowserModel.diff(
                List.of(snap("a", 4, 8, false)),
                List.of(snap("a", 3, 8, false)));

        assertEquals(-1, deltas.get(0).countDir());
    }

    @Test
    void diff_flagsStatusFlipWhenRoomFillsUp() {
        List<RoomBrowserModel.RowDelta> deltas = RoomBrowserModel.diff(
                List.of(snap("a", 7, 8, false)),
                List.of(snap("a", 8, 8, false)));

        assertEquals(1, deltas.size());
        assertTrue(deltas.get(0).statusChanged());
        assertEquals(1, deltas.get(0).countDir());
    }

    @Test
    void diff_flagsStatusFlipWhenMatchStartsWithoutCountChange() {
        List<RoomBrowserModel.RowDelta> deltas = RoomBrowserModel.diff(
                List.of(snap("a", 4, 8, false)),
                List.of(snap("a", 4, 8, true)));

        assertEquals(1, deltas.size());
        assertEquals(0, deltas.get(0).countDir());
        assertTrue(deltas.get(0).statusChanged());
    }

    @Test
    void diff_ignoresUnchangedRows() {
        assertTrue(RoomBrowserModel.diff(
                List.of(snap("a", 4, 8, false), snap("b", 8, 8, true)),
                List.of(snap("a", 4, 8, false), snap("b", 8, 8, true))).isEmpty());
    }

    @Test
    void diff_ignoresBrandNewRoomsSoTheyOnlyPlayTheCascade() {
        assertTrue(RoomBrowserModel.diff(
                List.of(),
                List.of(snap("a", 4, 8, false))).isEmpty());
    }

    @Test
    void diff_ignoresRemovedRooms() {
        assertTrue(RoomBrowserModel.diff(
                List.of(snap("a", 4, 8, false)),
                List.of()).isEmpty());
    }

    @Test
    void diff_toleratesNullFrames() {
        assertTrue(RoomBrowserModel.diff(null, null).isEmpty());
        assertTrue(RoomBrowserModel.diff(null, List.of(snap("a", 1, 8, false))).isEmpty());
        assertTrue(RoomBrowserModel.diff(List.of(snap("a", 1, 8, false)), null).isEmpty());
    }

    @Test
    void diff_handlesMultipleChangedRowsInOneFrame() {
        List<RoomBrowserModel.RowDelta> deltas = RoomBrowserModel.diff(
                List.of(snap("a", 3, 8, false), snap("b", 8, 8, true), snap("c", 2, 4, false)),
                List.of(snap("a", 4, 8, false), snap("b", 7, 8, true), snap("c", 2, 4, false)));

        assertEquals(2, deltas.size());
        assertEquals("a", deltas.get(0).roomId());
        assertEquals("b", deltas.get(1).roomId());
        assertTrue(deltas.get(1).statusChanged(), "b left the FULL state");
    }
}
