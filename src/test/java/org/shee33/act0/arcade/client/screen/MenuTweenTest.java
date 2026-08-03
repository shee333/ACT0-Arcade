package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MenuTween} 单元测试：覆盖规格文档 §1.5 的五条缓动公式 + {@link MenuTween#pulse}
 * 脉冲函数 + {@link MenuTween.Anim} 的 delay/duration 边界语义。
 *
 * <p>零 MC 依赖：缓动函数 + Anim 状态机纯 Java 逻辑，可单测。
 */
class MenuTweenTest {

    private static final float EPS = 0.001f;

    // ============================================================
    // Ease.apply 公式
    // ============================================================

    @Test
    void outBack_applyZero_returnsNearZero() {
        // outBack(t) = 1 + (c+1)*(t-1)^3 + c*(t-1)^2, c=1.70158; 在 t=0 处约为 0
        assertEquals(0f, MenuTween.Ease.OUT_BACK.apply(0f), EPS);
    }

    @Test
    void outBack_applyOne_returnsNearOne() {
        // outBack(1) = 1 + d*0 + c*0 = 1
        assertEquals(1f, MenuTween.Ease.OUT_BACK.apply(1f), EPS);
    }

    @Test
    void outExpo_applyOne_exactlyOne_specialCase() {
        // outExpo 在 t===1 时强制返回 1（特判，避免 -Math.pow(2,-10) 引入的浮点尾差）
        assertEquals(1f, MenuTween.Ease.OUT_EXPO.apply(1f), 0f);
    }

    @Test
    void inCubic_applyHalf_returnsOneEighth() {
        // inCubic(0.5) = 0.5^3 = 0.125
        assertEquals(0.125f, MenuTween.Ease.IN_CUBIC.apply(0.5f), EPS);
    }

    @Test
    void outCubic_isMonotonicIncreasingOnUnitInterval() {
        // 1 - (1-t)^3 在 [0,1] 严格递增
        float[] samples = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
        float prev = -1f;
        for (float s : samples) {
            float v = MenuTween.Ease.OUT_CUBIC.apply(s);
            assertTrue(v >= prev - EPS,
                    "outCubic must be monotonically non-decreasing on [0,1]; got "
                            + v + " after " + prev + " at t=" + s);
            prev = v;
        }
    }

    @Test
    void linear_applyExactlyIdentity() {
        assertEquals(0.37f, MenuTween.Ease.LINEAR.apply(0.37f), 0f);
    }

    // ============================================================
    // pulse()
    // ============================================================

    @Test
    void pulse_applyZero_returnsOne() {
        // sin(0) = 0 → 1 + 0 = 1
        assertEquals(1f, MenuTween.pulse(0f), EPS);
    }

    @Test
    void pulse_applyHalf_returnsOneAndQuarter() {
        // sin(π/2) = 1 → 1 + 0.25 = 1.25
        assertEquals(1.25f, MenuTween.pulse(0.5f), EPS);
    }

    @Test
    void pulse_applyOne_returnsOne() {
        // sin(π) = 0 → 1 + 0 = 1
        assertEquals(1f, MenuTween.pulse(1f), EPS);
    }

    // ============================================================
    // Anim 边界：delay / duration
    // ============================================================

    @Test
    void anim_rawT_beforeDelay_returnsZero() {
        // duration=200, delay=100；在 elapsed <= 0（即 now <= start + delay）时应返回 0
        MenuTween.Anim anim = new MenuTween.Anim(200L, 100L);
        anim.start(1000L);
        assertEquals(0f, anim.rawT(1050L), EPS);
        // 恰好踩到 delay 边界（now == start + delay）
        assertEquals(0f, anim.rawT(1100L), EPS);
    }

    @Test
    void anim_rawT_afterDuration_clampsToOne() {
        // 已超过 duration 后，rawT 必须被钳制到 1 而非继续增长
        MenuTween.Anim anim = new MenuTween.Anim(200L, 0L);
        anim.start(1000L);
        assertEquals(1f, anim.rawT(1300L), EPS);   // elapsed = 300，已超出
        assertEquals(1f, anim.rawT(9999L), EPS);   // 远超
    }

    @Test
    void anim_isDone_becomesTrueOnlyAfterDuration() {
        MenuTween.Anim anim = new MenuTween.Anim(200L, 0L);
        anim.start(1000L);
        assertFalse(anim.isDone(1000L));
        assertFalse(anim.isDone(1199L));
        assertTrue(anim.isDone(1200L));
        assertTrue(anim.isDone(1500L));
    }

    @Test
    void anim_easedT_appliesEaseToRawProgress() {
        // rawT(1100) 在 duration=200,delay=0,start=1000 下 = 0.5
        // eased with IN_CUBIC = 0.5^3 = 0.125
        MenuTween.Anim anim = new MenuTween.Anim(200L, 0L);
        anim.start(1000L);
        assertEquals(0.125f, anim.easedT(1100L, MenuTween.Ease.IN_CUBIC), EPS);
    }
}
