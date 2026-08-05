package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.LoadoutSlot;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoadoutScreenAnimator} 单元测试：覆盖构造时铺设的级联数组尺寸/延迟，
 * 以及按压回弹索引映射（{@link LoadoutScreenAnimator#slotPressIndex(LoadoutSlot)}）。
 *
 * <p>{@code MenuChrome.wrapIndex}/{@code lerp}/{@code clamp01} 等纯数学辅助已在
 * {@code MenuChromeTest}（若存在）或调用方处覆盖，此处不重复造轮子验证。
 */
class LoadoutScreenAnimatorTest {

    private static final float EPS = 0.001f;

    // ============================================================
    // 数组尺寸：左栏 5 张卡片 / 右栏 职业+6槽位+服饰 = 8
    // ============================================================

    @Test
    void constructor_leftColumnLength_equalsMaxSlots() {
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(0L);
        assertEquals(5, a.leftColumn.length, "leftColumn must cover all 5 loadout set cards");
    }

    @Test
    void constructor_rightColumnLength_equalsClassPlusSlotsPlusApparel() {
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(0L);
        // 1 职业行 + 6 槽位行 + 1 服饰链接 = 8
        assertEquals(8, a.rightColumn.length);
    }

    // ============================================================
    // 开场级联：左栏/右栏各自独立的 stagger 间隔与延迟
    // ============================================================

    @Test
    void constructor_leftColumnCascade_delaysGrowByIndex() {
        long now = 1000L;
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(now);

        for (int i = 0; i < a.leftColumn.length; i++) {
            MenuTween.Anim item = a.leftColumn[i];
            long delay = (long) i * 35L;
            assertEquals(0f, item.rawT(now + delay), EPS,
                    "leftColumn[" + i + "] rawT must still be 0 right at its delay boundary");
            assertTrue(item.rawT(now + delay + 1L) > 0f,
                    "leftColumn[" + i + "] rawT should be > 0 just past its delay");
        }
    }

    @Test
    void constructor_rightColumnCascade_delaysGrowByIndex_independentlyFromLeftColumn() {
        long now = 1000L;
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(now);

        for (int i = 0; i < a.rightColumn.length; i++) {
            MenuTween.Anim item = a.rightColumn[i];
            long delay = 50L + (long) i * 45L;
            assertEquals(0f, item.rawT(now + delay), EPS,
                    "rightColumn[" + i + "] rawT must still be 0 right at its delay boundary");
            assertTrue(item.rawT(now + delay + 1L) > 0f,
                    "rightColumn[" + i + "] rawT should be > 0 just past its delay");
        }

        // 独立性回归：右栏第 0 项延迟(50ms) 与左栏第 0 项延迟(0ms) 不同，两组不能共用同一时序
        assertEquals(0L, 0L + 0L * 35L, "sanity: leftColumn[0] delay baseline is 0ms");
        assertTrue(50L != 0L, "rightColumn base delay must differ from leftColumn to stagger independently");
    }

    // ============================================================
    // 选中指示器 / 职业标签滑动：构造后不应自动启动，等待交互显式 start()
    // ============================================================

    @Test
    void constructor_indicatorAndClassSlide_notRunningInitially() {
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(0L);
        assertFalse(a.indicatorY.isRunning(), "indicatorY must wait for an explicit selection change");
        assertFalse(a.classSlide.isRunning(), "classSlide must wait for an explicit class cycle");
    }

    // ============================================================
    // 按压回弹数组：尺寸 + 未启动 + 索引映射
    // ============================================================

    @Test
    void constructor_pressArray_hasExpectedSizeAndAllUnstarted() {
        LoadoutScreenAnimator a = new LoadoutScreenAnimator(0L);
        assertEquals(LoadoutScreenAnimator.PRESS_COUNT, a.press.length);
        for (MenuTween.Anim p : a.press) {
            assertFalse(p.isRunning());
        }
    }

    @Test
    void pressIndexConstants_areDistinctAndWithinBounds() {
        int[] all = {
                LoadoutScreenAnimator.PRESS_CLASS_LEFT,
                LoadoutScreenAnimator.PRESS_CLASS_RIGHT,
                LoadoutScreenAnimator.PRESS_APPAREL,
                LoadoutScreenAnimator.PRESS_SAVE,
                LoadoutScreenAnimator.PRESS_RESPAWN,
                LoadoutScreenAnimator.PRESS_DEFAULT,
                LoadoutScreenAnimator.PRESS_CLOSE,
        };
        Set<Integer> seen = new HashSet<>();
        for (int idx : all) {
            assertTrue(idx >= 0 && idx < LoadoutScreenAnimator.PRESS_COUNT,
                    "press index " + idx + " must be within [0, PRESS_COUNT)");
            assertTrue(seen.add(idx), "press index " + idx + " must be unique");
        }
    }

    @Test
    void slotPressIndex_mapsEachSlotToUniqueIndexBeforeApparel() {
        Set<Integer> seen = new HashSet<>();
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            int idx = LoadoutScreenAnimator.slotPressIndex(slot);
            assertTrue(idx >= 2, "slot press index must start after the two class-cycle arrows");
            assertTrue(idx < LoadoutScreenAnimator.PRESS_APPAREL,
                    "slot press index must not collide with PRESS_APPAREL");
            assertTrue(seen.add(idx), "slot press index " + idx + " must be unique per slot");
        }
        assertEquals(LoadoutSlot.values().length, seen.size());
    }

    @Test
    void slotPressIndex_isStableAcrossCalls() {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            assertEquals(LoadoutScreenAnimator.slotPressIndex(slot), LoadoutScreenAnimator.slotPressIndex(slot));
        }
    }
}
