package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomBrowserAnimator} 单元测试：覆盖构造时铺设的 {@link MenuTween.Anim} 字段
 * 是否符合类头注释所声明的开场级联时序（标题栏 → 列表行错峰 → 底部按钮）与
 * {@code press} 数组的槽位分配规则。写法对照 {@code CreateRoomAnimatorTest} 的
 * 构造时序断言风格。
 */
class RoomBrowserAnimatorTest {

    private static final float EPS = 0.001f;
    private static final int VISIBLE_ROWS = 4;

    // ============================================================
    // 标题栏 —— 0ms 延迟，最先出现
    // ============================================================

    @Test
    void constructor_titleBar_startsImmediatelyWithNoDelay() {
        long now = 1000L;
        RoomBrowserAnimator a = new RoomBrowserAnimator(now, VISIBLE_ROWS);

        assertEquals(0f, a.titleBar.rawT(now), EPS, "titleBar must not have progressed at t=0");
        assertTrue(a.titleBar.rawT(now + 1L) > 0f, "titleBar should start progressing immediately (0ms delay)");
    }

    // ============================================================
    // 列表行 —— 逐行错峰 35ms/行，基础延迟 90ms
    // ============================================================

    @Test
    void constructor_rowDelays_staggerByRowStaggerMs() {
        long now = 1000L;
        RoomBrowserAnimator a = new RoomBrowserAnimator(now, VISIBLE_ROWS);

        assertEquals(VISIBLE_ROWS, a.rows.length);
        for (int i = 0; i < a.rows.length; i++) {
            long delay = RoomBrowserAnimator.ROW_BASE_DELAY + (long) i * RoomBrowserAnimator.ROW_STAGGER_MS;
            MenuTween.Anim row = a.rows[i];
            assertEquals(0f, row.rawT(now + delay - 1L), EPS,
                    "rows[" + i + "] must still be 0 right before its delay (" + delay + "ms)");
            assertTrue(row.rawT(now + delay + 1L) > 0f,
                    "rows[" + i + "] should be > 0 just past its delay");
        }
    }

    // ============================================================
    // 底部按钮行 —— 在最后一行开始动画后再等 FOOTER_EXTRA_DELAY 才出现
    // ============================================================

    @Test
    void constructor_footerDelay_followsLastRowByExtraDelay() {
        long now = 1000L;
        RoomBrowserAnimator a = new RoomBrowserAnimator(now, VISIBLE_ROWS);

        long expectedDelay = RoomBrowserAnimator.ROW_BASE_DELAY
                + (long) VISIBLE_ROWS * RoomBrowserAnimator.ROW_STAGGER_MS
                + RoomBrowserAnimator.FOOTER_EXTRA_DELAY;

        assertEquals(0f, a.footer.rawT(now + expectedDelay - 1L), EPS,
                "footer must still be 0 right before its computed delay");
        assertTrue(a.footer.rawT(now + expectedDelay + 1L) > 0f,
                "footer should start progressing just past its computed delay");
    }

    // ============================================================
    // press[] —— 前 STATIC_PRESS_COUNT 位固定 + 每个可见行一位
    // ============================================================

    @Test
    void constructor_pressArrayLength_isStaticCountPlusVisibleRows() {
        RoomBrowserAnimator a = new RoomBrowserAnimator(0L, VISIBLE_ROWS);
        assertEquals(RoomBrowserAnimator.STATIC_PRESS_COUNT + VISIBLE_ROWS, a.press.length);
    }

    @Test
    void constructor_pressEntries_areFreshAndNotRunningUntilStarted() {
        RoomBrowserAnimator a = new RoomBrowserAnimator(0L, VISIBLE_ROWS);
        for (MenuTween.Anim p : a.press) {
            assertTrue(!p.isRunning(), "press anim must not be running until a click starts it");
        }
    }

    // ============================================================
    // 边界：visibleRows=0 不应导致数组越界或 NPE
    // ============================================================

    @Test
    void constructor_zeroVisibleRows_keepsRowsEmptyAndPressStaticOnly() {
        RoomBrowserAnimator a = new RoomBrowserAnimator(0L, 0);
        assertEquals(0, a.rows.length);
        assertEquals(RoomBrowserAnimator.STATIC_PRESS_COUNT, a.press.length);
    }

    @Test
    void constructor_negativeVisibleRows_isClampedToZero() {
        RoomBrowserAnimator a = new RoomBrowserAnimator(0L, -3);
        assertEquals(0, a.rows.length);
        assertEquals(RoomBrowserAnimator.STATIC_PRESS_COUNT, a.press.length);
    }
}
