package org.shee33.act0.arcade.arena;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.arena.HotZoneRotation.TickResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotZoneRotationTest {

    private static final int DEFAULT_TICKS = 25 * 20;

    /** 推进 n tick，返回最后一 tick 的结果。 */
    private static TickResult advance(HotZoneRotation r, int n) {
        TickResult last = TickResult.NONE;
        for (int i = 0; i < n; i++) {
            last = r.tick();
        }
        return last;
    }

    @Test
    void startsOnFirstZoneWithFullDuration() {
        HotZoneRotation r = new HotZoneRotation(3, DEFAULT_TICKS);
        assertEquals(0, r.activeIndex());
        assertEquals(1, r.nextIndex());
        assertEquals(DEFAULT_TICKS, r.remainingTicks());
        assertEquals(25, r.remainingSeconds());
        assertEquals(1.0f, r.progress(), 1.0e-6f);
        assertFalse(r.warning());
        assertTrue(r.rotates());
    }

    @Test
    void warningFiresExactlyFiveSecondsBeforeMigration() {
        HotZoneRotation r = new HotZoneRotation(3, DEFAULT_TICKS);
        // 第 400 tick 后剩余正好 100 tick（5 秒），应当刚好跨入预告窗口
        assertEquals(TickResult.NONE, advance(r, DEFAULT_TICKS - HotZoneRotation.WARNING_TICKS - 1));
        assertFalse(r.warning());
        assertEquals(TickResult.WARNING_STARTED, r.tick());
        assertTrue(r.warning());
        assertEquals(HotZoneRotation.WARNING_TICKS, r.remainingTicks());
        assertEquals(5, r.remainingSeconds());
        // 预告窗口内不再重复触发 WARNING_STARTED
        assertEquals(TickResult.NONE, advance(r, HotZoneRotation.WARNING_TICKS - 1));
        assertTrue(r.warning());
        assertEquals(1, r.remainingSeconds());
    }

    @Test
    void migratesOnDurationBoundaryAndResetsTimer() {
        HotZoneRotation r = new HotZoneRotation(3, DEFAULT_TICKS);
        assertEquals(TickResult.MIGRATED, advance(r, DEFAULT_TICKS));
        assertEquals(1, r.activeIndex());
        assertEquals(2, r.nextIndex());
        assertEquals(DEFAULT_TICKS, r.remainingTicks());
        assertFalse(r.warning(), "迁移后应立刻退出预告窗口");
    }

    @Test
    void rotatesSequentiallyAndWrapsAround() {
        HotZoneRotation r = new HotZoneRotation(3, DEFAULT_TICKS);
        int[] expected = {1, 2, 0, 1, 2, 0};
        for (int round = 0; round < expected.length; round++) {
            assertEquals(TickResult.MIGRATED, advance(r, DEFAULT_TICKS));
            assertEquals(expected[round], r.activeIndex(), "第 " + (round + 1) + " 次迁移后的激活索引");
        }
    }

    @Test
    void nextIndexNeverEqualsActiveWhenRotating() {
        HotZoneRotation r = new HotZoneRotation(4, HotZoneRotation.MIN_DURATION_TICKS);
        for (int i = 0; i < 12; i++) {
            assertTrue(r.activeIndex() != r.nextIndex(),
                    "轮换中不允许连续两次激活同一个热区（第 " + i + " 轮）");
            advance(r, HotZoneRotation.MIN_DURATION_TICKS);
        }
    }

    @Test
    void twoZonesAlternate() {
        HotZoneRotation r = new HotZoneRotation(2, DEFAULT_TICKS);
        assertEquals(0, r.activeIndex());
        advance(r, DEFAULT_TICKS);
        assertEquals(1, r.activeIndex());
        assertEquals(0, r.nextIndex());
        advance(r, DEFAULT_TICKS);
        assertEquals(0, r.activeIndex());
    }

    @Test
    void singleZoneNeverRotatesOrWarns() {
        HotZoneRotation r = new HotZoneRotation(1, DEFAULT_TICKS);
        assertFalse(r.rotates());
        assertEquals(0, r.activeIndex());
        assertEquals(0, r.nextIndex());
        assertEquals(TickResult.NONE, advance(r, DEFAULT_TICKS * 3));
        assertEquals(0, r.activeIndex());
        assertEquals(DEFAULT_TICKS, r.remainingTicks(), "不轮换时剩余时间不应递减");
        assertFalse(r.warning());
    }

    @Test
    void zeroZonesIsInertAndDoesNotThrow() {
        HotZoneRotation r = new HotZoneRotation(0, DEFAULT_TICKS);
        assertFalse(r.rotates());
        assertEquals(TickResult.NONE, advance(r, 100));
        assertEquals(0, r.activeIndex());
    }

    @Test
    void durationClampedToDocumentedRange() {
        assertEquals(HotZoneRotation.MIN_DURATION_TICKS, new HotZoneRotation(3, 0).durationTicks());
        assertEquals(HotZoneRotation.MIN_DURATION_TICKS, new HotZoneRotation(3, -500).durationTicks());
        assertEquals(HotZoneRotation.MAX_DURATION_TICKS, new HotZoneRotation(3, 999_999).durationTicks());
        assertEquals(DEFAULT_TICKS, new HotZoneRotation(3, DEFAULT_TICKS).durationTicks());
    }

    @Test
    void minimumDurationStillLeavesRoomBeforeWarning() {
        HotZoneRotation r = new HotZoneRotation(3, HotZoneRotation.MIN_DURATION_TICKS);
        assertFalse(r.warning(), "最短时长下开局也不应立刻处于预告窗口");
        assertTrue(HotZoneRotation.MIN_DURATION_TICKS > HotZoneRotation.WARNING_TICKS);
    }

    @Test
    void progressDrainsMonotonicallyToNearZero() {
        HotZoneRotation r = new HotZoneRotation(3, DEFAULT_TICKS);
        float previous = r.progress();
        for (int i = 0; i < DEFAULT_TICKS - 1; i++) {
            r.tick();
            float current = r.progress();
            assertTrue(current <= previous, "进度必须单调不增");
            previous = current;
        }
        assertEquals(1.0f / DEFAULT_TICKS, r.progress(), 1.0e-6f);
    }

    @Test
    void remainingSecondsRoundsUpSoCountdownNeverShowsZero() {
        HotZoneRotation r = new HotZoneRotation(2, DEFAULT_TICKS);
        advance(r, DEFAULT_TICKS - 20);
        assertEquals(20, r.remainingTicks());
        assertEquals(1, r.remainingSeconds());
        advance(r, 19);
        assertEquals(1, r.remainingTicks());
        assertEquals(1, r.remainingSeconds(), "剩最后 1 tick 也显示 1 秒，不显示 0");
        assertEquals(TickResult.MIGRATED, r.tick());
    }
}
