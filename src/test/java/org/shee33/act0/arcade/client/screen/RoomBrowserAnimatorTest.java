package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomBrowserAnimator} 单元测试：验证构造时铺设的 {@link MenuTween.Anim} 是否符合
 * 《游戏浏览器动效双版本规格文档》第 1 节（共享底盘）与第 2 节（浏览器 A）的时长/错峰/延迟，
 * 以及各"按可见槽位"分配的数组长度。写法对照 {@code CreateRoomAnimatorTest} 的构造时序断言风格。
 */
class RoomBrowserAnimatorTest {

    private static final float EPS = 0.001f;
    private static final int VISIBLE_ROWS = 5;

    private static RoomBrowserAnimator at(long now) {
        return new RoomBrowserAnimator(now, VISIBLE_ROWS);
    }

    private static void assertStartsAt(MenuTween.Anim a, long now, long delay, String what) {
        if (delay > 0) {
            assertEquals(0f, a.rawT(now + delay - 1L), EPS, what + " must still be 0 right before its delay");
        }
        assertTrue(a.rawT(now + delay + 1L) > 0f, what + " should be progressing just past its delay");
    }

    // ============================================================
    // 开场级联：.ch 块 70ms 错峰 / 下划线 outExpo 延迟 280 / 行 50ms 错峰
    // ============================================================

    @Test
    void chromeBlocks_staggerBy70msEach() {
        long now = 1000L;
        RoomBrowserAnimator a = at(now);

        assertEquals(RoomBrowserAnimator.CH_COUNT, a.chrome.length);
        assertEquals(70L, RoomBrowserAnimator.CH_STAGGER_MS);
        for (int i = 0; i < a.chrome.length; i++) {
            assertStartsAt(a.chrome[i], now, i * RoomBrowserAnimator.CH_STAGGER_MS, "chrome[" + i + "]");
        }
    }

    @Test
    void tabLine_expandsAfter280msDelay() {
        long now = 1000L;
        RoomBrowserAnimator a = at(now);

        assertEquals(280L, RoomBrowserAnimator.TAB_LINE_DELAY);
        assertStartsAt(a.tabLine, now, RoomBrowserAnimator.TAB_LINE_DELAY, "tabLine");
        assertTrue(a.tabLine.isDone(now + RoomBrowserAnimator.TAB_LINE_DELAY + RoomBrowserAnimator.TAB_LINE_MS));
    }

    @Test
    void rows_staggerBy50msPerRow() {
        long now = 1000L;
        RoomBrowserAnimator a = at(now);

        assertEquals(VISIBLE_ROWS, a.rows.length);
        assertEquals(50L, RoomBrowserAnimator.ROW_STAGGER_MS);
        for (int i = 0; i < a.rows.length; i++) {
            assertStartsAt(a.rows[i], now, i * RoomBrowserAnimator.ROW_STAGGER_MS, "rows[" + i + "]");
        }
    }

    @Test
    void emptyState_fadesInAfter150msDelay() {
        long now = 1000L;
        RoomBrowserAnimator a = at(now);
        assertStartsAt(a.empty, now, RoomBrowserAnimator.EMPTY_DELAY, "empty");
    }

    // ============================================================
    // 标签切换 / 刷新：退场错峰总时长
    // ============================================================

    @Test
    void rowExitTotal_isDurationPlusLastRowStagger() {
        RoomBrowserAnimator a = at(0L);
        assertEquals(RoomBrowserAnimator.ROW_EXIT_MS
                        + (VISIBLE_ROWS - 1) * RoomBrowserAnimator.ROW_EXIT_STAGGER_MS,
                a.rowExitTotalMs());
    }

    @Test
    void rowFadeTotal_isDurationPlusLastRowStagger() {
        RoomBrowserAnimator a = at(0L);
        assertEquals(RoomBrowserAnimator.ROW_FADE_MS
                        + (VISIBLE_ROWS - 1) * RoomBrowserAnimator.ROW_FADE_STAGGER_MS,
                a.rowFadeTotalMs());
    }

    @Test
    void startRowExit_restartsEveryRowFromItsOwnStagger() {
        long now = 5000L;
        RoomBrowserAnimator a = at(now);
        a.startRowExit(now);
        for (int i = 0; i < a.rowExit.length; i++) {
            assertStartsAt(a.rowExit[i], now, i * RoomBrowserAnimator.ROW_EXIT_STAGGER_MS, "rowExit[" + i + "]");
        }
    }

    // ============================================================
    // 抽屉：玩家列表 40ms 错峰；开/关/压暗/回亮时长照抄规格
    // ============================================================

    @Test
    void players_staggerBy40msPerRow() {
        long now = 2000L;
        RoomBrowserAnimator a = at(now);
        a.startPlayers(now);

        assertEquals(RoomBrowserAnimator.PLAYER_SLOTS, a.players.length);
        assertEquals(40L, RoomBrowserAnimator.PLAYER_STAGGER_MS);
        for (int i = 0; i < a.players.length; i++) {
            assertStartsAt(a.players[i], now, i * RoomBrowserAnimator.PLAYER_STAGGER_MS, "players[" + i + "]");
        }
    }

    @Test
    void drawerDurations_matchSpec() {
        assertEquals(300L, RoomBrowserAnimator.DRAWER_OPEN_MS);
        assertEquals(240L, RoomBrowserAnimator.DRAWER_CLOSE_MS);
        assertEquals(120L, RoomBrowserAnimator.DRAWER_DIM_MS);
        assertEquals(160L, RoomBrowserAnimator.DRAWER_LIT_MS);
    }

    @Test
    void drawerAnims_notRunningUntilSelectionHappens() {
        RoomBrowserAnimator a = at(0L);
        assertFalse(a.drawerOpen.isRunning());
        assertFalse(a.drawerClose.isRunning());
        assertFalse(a.drawerDim.isRunning());
        assertFalse(a.drawerLit.isRunning());
        assertFalse(a.tabSlide.isRunning());
        assertFalse(a.scan.isRunning());
        assertFalse(a.refreshSpin.isRunning());
    }

    // ============================================================
    // 加入转场：单段进度条 950ms + 延迟 250，「已连接」停 700ms
    // ============================================================

    @Test
    void joinBar_runs950msAfter250msDelay() {
        long now = 100L;
        RoomBrowserAnimator a = at(now);
        a.joinBar.start(now);

        assertEquals(950L, RoomBrowserAnimator.JOIN_BAR_MS);
        assertEquals(250L, RoomBrowserAnimator.JOIN_BAR_DELAY);
        assertEquals(0f, a.joinBar.rawT(now + 249L), EPS);
        assertFalse(a.joinBar.isDone(now + 250L + 949L));
        assertTrue(a.joinBar.isDone(now + 250L + 950L));
    }

    @Test
    void joinTransitionTimings_matchSpec() {
        assertEquals(200L, RoomBrowserAnimator.JOIN_HANDOFF_MS);
        assertEquals(250L, RoomBrowserAnimator.OVERLAY_IN_MS);
        assertEquals(700L, RoomBrowserAnimator.JOIN_HOLD_MS);
        assertEquals(300L, RoomBrowserAnimator.OVERLAY_OUT_MS);
        assertEquals(320L, RoomBrowserAnimator.SHAKE_MS);
    }

    // ============================================================
    // press[] / 槽位数组长度与边界
    // ============================================================

    @Test
    void pressArray_holdsExactlyTheThreeStaticControls() {
        RoomBrowserAnimator a = at(0L);
        assertEquals(RoomBrowserAnimator.STATIC_PRESS_COUNT, a.press.length);
        assertEquals(3, RoomBrowserAnimator.STATIC_PRESS_COUNT);
        for (MenuTween.Anim p : a.press) {
            assertFalse(p.isRunning(), "press anim must not be running until a click starts it");
        }
    }

    @Test
    void slotArrays_allSizedToVisibleRows() {
        RoomBrowserAnimator a = at(0L);
        assertEquals(VISIBLE_ROWS, a.rows.length);
        assertEquals(VISIBLE_ROWS, a.rowExit.length);
        assertEquals(VISIBLE_ROWS, a.rowFade.length);
        assertEquals(VISIBLE_ROWS, a.rowHover.length);
        assertEquals(VISIBLE_ROWS, a.countRoll.length);
        assertEquals(VISIBLE_ROWS, a.statusRoll.length);
        assertEquals(VISIBLE_ROWS, a.rowFlash.length);
    }

    @Test
    void zeroOrNegativeVisibleRows_keepsSlotArraysEmpty() {
        for (int visible : new int[] {0, -3}) {
            RoomBrowserAnimator a = new RoomBrowserAnimator(0L, visible);
            assertEquals(0, a.rows.length);
            assertEquals(0, a.rowHover.length);
            assertEquals(0, a.rowFlash.length);
            assertEquals(RoomBrowserAnimator.ROW_EXIT_MS, a.rowExitTotalMs());
            assertEquals(RoomBrowserAnimator.STATIC_PRESS_COUNT, a.press.length);
        }
    }

    @Test
    void playOpening_canBeReplayedFromANewTimestamp() {
        RoomBrowserAnimator a = at(0L);
        long later = 10_000L;
        a.playOpening(later);
        assertEquals(0f, a.chrome[0].rawT(later), EPS);
        assertEquals(0f, a.rows[0].rawT(later), EPS);
        assertTrue(a.rows[0].rawT(later + RoomBrowserAnimator.ROW_MS) >= 1f);
    }
}
