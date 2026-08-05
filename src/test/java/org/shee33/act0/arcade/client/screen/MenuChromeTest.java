package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MenuChrome} 单元测试：覆盖从 {@code CreateRoomScreen} 搬迁出来的纯逻辑方法
 * （{@code inRect} / {@code lerpColor} / {@code pressScale}）以及从 {@link CreateRoomAnimator}
 * 复制而来的纯数学辅助方法（{@code clamp01} / {@code lerp} / {@code wrapIndex} /
 * {@code scrollDir} / {@code expandProgress} / {@code hoverState} / {@code rollOffset}）。
 *
 * <p>后者这组方法在 {@code CreateRoomAnimatorTest} 中已被逐条穷尽覆盖同一套数学，这里只做
 * 轻量 sanity check，确认 MenuChrome 的新副本行为与原版一致，不重复穷举所有分支。
 */
class MenuChromeTest {

    private static final float EPS = 0.001f;

    // ============================================================
    // inRect —— 半开矩形命中检测边界
    // ============================================================

    @Test
    void inRect_pointInsideRect_returnsTrue() {
        assertTrue(MenuChrome.inRect(15, 15, 10, 10, 20, 20));
    }

    @Test
    void inRect_pointOutsideRect_returnsFalse() {
        assertFalse(MenuChrome.inRect(5, 5, 10, 10, 20, 20));
        assertFalse(MenuChrome.inRect(35, 15, 10, 10, 20, 20));
    }

    @Test
    void inRect_topLeftEdge_isInclusive() {
        // 左/上边界闭区间：x==mx, y==my 应算命中
        assertTrue(MenuChrome.inRect(10, 10, 10, 10, 20, 20));
    }

    @Test
    void inRect_bottomRightEdge_isExclusive() {
        // 右/下边界开区间：mx == x+w 或 my == y+h 不应算命中（半开矩形）
        assertFalse(MenuChrome.inRect(30, 15, 10, 10, 20, 20));
        assertFalse(MenuChrome.inRect(15, 30, 10, 10, 20, 20));
    }

    @Test
    void inRect_justInsideBottomRightEdge_returnsTrue() {
        assertTrue(MenuChrome.inRect(29.999, 29.999, 10, 10, 20, 20));
    }

    // ============================================================
    // lerpColor —— 颜色线性插值，alpha 通道恒强制为 0xFF
    // ============================================================

    @Test
    void lerpColor_atZero_returnsFirstColorRgb_withForcedOpaqueAlpha() {
        int c1 = 0xFFFFD76A; // GOLD
        int c2 = 0xFF7EE2A8; // GREEN
        assertEquals(0xFFFFD76A, MenuChrome.lerpColor(c1, c2, 0f));
    }

    @Test
    void lerpColor_atOne_returnsSecondColorRgb_withForcedOpaqueAlpha() {
        int c1 = 0xFFFFD76A;
        int c2 = 0xFF7EE2A8;
        assertEquals(0xFF7EE2A8, MenuChrome.lerpColor(c1, c2, 1f));
    }

    @Test
    void lerpColor_atHalf_averagesChannels() {
        // GOLD (0xFF,0xD7,0x6A) -> GREEN (0x7E,0xE2,0xA8)，逐通道取中点
        int c1 = 0xFFFFD76A;
        int c2 = 0xFF7EE2A8;
        int mid = MenuChrome.lerpColor(c1, c2, 0.5f);
        int r = (mid >> 16) & 0xFF, g = (mid >> 8) & 0xFF, b = mid & 0xFF;
        assertEquals(Math.round((0xFF + 0x7E) / 2f), r);
        assertEquals(Math.round((0xD7 + 0xE2) / 2f), g);
        assertEquals(Math.round((0x6A + 0xA8) / 2f), b);
        assertEquals(0xFF, (mid >>> 24) & 0xFF, "alpha channel must always be forced fully opaque");
    }

    @Test
    void lerpColor_outOfRangeT_clampsToBounds() {
        int c1 = 0xFFFFD76A;
        int c2 = 0xFF7EE2A8;
        assertEquals(MenuChrome.lerpColor(c1, c2, 0f), MenuChrome.lerpColor(c1, c2, -5f));
        assertEquals(MenuChrome.lerpColor(c1, c2, 1f), MenuChrome.lerpColor(c1, c2, 5f));
    }

    @Test
    void lerpColor_inputWithZeroAlpha_stillForcesOpaqueOutput() {
        // 即便传入完全透明的输入色，输出 alpha 通道也必须被强制为 0xFF（既有行为，不"修正"）
        int transparentGold = 0x00FFD76A;
        int result = MenuChrome.lerpColor(transparentGold, transparentGold, 0.5f);
        assertEquals(0xFF, (result >>> 24) & 0xFF);
    }

    // ============================================================
    // clamp01 / lerp
    // ============================================================

    @Test
    void clamp01_belowZero_clampsToZero() {
        assertEquals(0f, MenuChrome.clamp01(-1f), EPS);
    }

    @Test
    void clamp01_withinRange_isUnchanged() {
        assertEquals(0.42f, MenuChrome.clamp01(0.42f), EPS);
    }

    @Test
    void clamp01_aboveOne_clampsToOne() {
        assertEquals(1f, MenuChrome.clamp01(2f), EPS);
    }

    @Test
    void lerp_atZeroAndOne_returnsEndpoints() {
        assertEquals(10f, MenuChrome.lerp(10f, 20f, 0f), EPS);
        assertEquals(20f, MenuChrome.lerp(10f, 20f, 1f), EPS);
    }

    @Test
    void lerp_atHalf_returnsMidpoint() {
        assertEquals(15f, MenuChrome.lerp(10f, 20f, 0.5f), EPS);
    }

    // ============================================================
    // wrapIndex
    // ============================================================

    @Test
    void wrapIndex_forward_wrapsAtSize() {
        assertEquals(0, MenuChrome.wrapIndex(2, 1, 3));
    }

    @Test
    void wrapIndex_backward_keepsPositive() {
        assertEquals(2, MenuChrome.wrapIndex(0, -1, 3));
    }

    @Test
    void wrapIndex_sizeZero_returnsZero_safeGuard() {
        assertEquals(0, MenuChrome.wrapIndex(7, 3, 0));
    }

    // ============================================================
    // scrollDir
    // ============================================================

    @Test
    void scrollDir_toGreater_returnsPositiveOne() {
        assertEquals(1, MenuChrome.scrollDir(2, 5));
    }

    @Test
    void scrollDir_toSmaller_returnsNegativeOne() {
        assertEquals(-1, MenuChrome.scrollDir(5, 2));
    }

    @Test
    void scrollDir_toEqual_returnsZero() {
        assertEquals(0, MenuChrome.scrollDir(3, 3));
    }

    // ============================================================
    // expandProgress
    // ============================================================

    @Test
    void expandProgress_visibleTrue_followsEasedDirectly() {
        assertEquals(0f, MenuChrome.expandProgress(true, 0f), EPS);
        assertEquals(0.5f, MenuChrome.expandProgress(true, 0.5f), EPS);
        assertEquals(1f, MenuChrome.expandProgress(true, 1f), EPS);
    }

    @Test
    void expandProgress_visibleFalse_invertsEased() {
        assertEquals(1f, MenuChrome.expandProgress(false, 0f), EPS);
        assertEquals(0.5f, MenuChrome.expandProgress(false, 0.5f), EPS);
        assertEquals(0f, MenuChrome.expandProgress(false, 1f), EPS);
    }

    // ============================================================
    // hoverState
    // ============================================================

    @Test
    void hoverState_entering_startsAtBaseEndsAtHover() {
        MenuChrome.HoverState start = MenuChrome.hoverState(true, 0f);
        assertEquals(6f, start.padding(), EPS);
        assertEquals(0.55f, start.textAlpha(), EPS);

        MenuChrome.HoverState end = MenuChrome.hoverState(true, 1f);
        assertEquals(10f, end.padding(), EPS);
        assertEquals(0.9f, end.textAlpha(), EPS);
    }

    @Test
    void hoverState_leaving_isSymmetricReverseOfEntering() {
        MenuChrome.HoverState start = MenuChrome.hoverState(false, 0f);
        assertEquals(10f, start.padding(), EPS);
        assertEquals(0.9f, start.textAlpha(), EPS);

        MenuChrome.HoverState end = MenuChrome.hoverState(false, 1f);
        assertEquals(6f, end.padding(), EPS);
        assertEquals(0.55f, end.textAlpha(), EPS);
    }

    // ============================================================
    // rollOffset
    // ============================================================

    @Test
    void rollOffset_dirPositive_oldSlidesUp_newArrivesFromBelow() {
        int clip = 18;
        assertEquals(0f, MenuChrome.rollOffset(1, 0f, clip, true), EPS);
        assertEquals(-18f, MenuChrome.rollOffset(1, 1f, clip, true), EPS);

        assertEquals(18f, MenuChrome.rollOffset(1, 0f, clip, false), EPS);
        assertEquals(0f, MenuChrome.rollOffset(1, 1f, clip, false), EPS);
    }

    @Test
    void rollOffset_dirNegative_isMirroredAcrossZero() {
        int clip = 92;
        assertEquals(0f, MenuChrome.rollOffset(-1, 0f, clip, true), EPS);
        assertEquals(92f, MenuChrome.rollOffset(-1, 1f, clip, true), EPS);

        assertEquals(-92f, MenuChrome.rollOffset(-1, 0f, clip, false), EPS);
        assertEquals(0f, MenuChrome.rollOffset(-1, 1f, clip, false), EPS);
    }

    @Test
    void rollOffset_atFullProgress_oldIsFullyClearOfClipSize() {
        int clip = 92;
        assertEquals((float) clip, Math.abs(MenuChrome.rollOffset(1, 1f, clip, true)), EPS);
        assertEquals((float) clip, Math.abs(MenuChrome.rollOffset(-1, 1f, clip, true)), EPS);
    }

    // ============================================================
    // pressScale —— 按压回弹缩放
    // ============================================================

    @Test
    void pressScale_neverStartedAnim_returnsExactlyOne() {
        MenuTween.Anim anim = new MenuTween.Anim(220L, 0L);
        assertEquals(1.0f, MenuChrome.pressScale(anim, 12345L), 0f);
    }

    @Test
    void pressScale_runningAnim_followsOutBackFormula() {
        MenuTween.Anim anim = new MenuTween.Anim(220L, 0L);
        anim.start(1000L);
        long now = 1110L; // elapsed=110 -> rawT=0.5
        float expectedEase = MenuTween.Ease.OUT_BACK.apply(0.5f);
        float expected = 0.82f + 0.18f * expectedEase;
        assertEquals(expected, MenuChrome.pressScale(anim, now), EPS);
    }

    @Test
    void pressScale_runningAnimAtStart_isNearBaseFloor() {
        MenuTween.Anim anim = new MenuTween.Anim(220L, 0L);
        anim.start(1000L);
        // outBack(0) ~ 0 -> scale ~ 0.82
        assertEquals(0.82f, MenuChrome.pressScale(anim, 1000L), EPS);
    }

    @Test
    void pressScale_runningAnimAfterDuration_returnsOne() {
        MenuTween.Anim anim = new MenuTween.Anim(220L, 0L);
        anim.start(1000L);
        // outBack(1) == 1 -> scale = 0.82 + 0.18*1 = 1.0
        assertEquals(1.0f, MenuChrome.pressScale(anim, 1220L), EPS);
    }
}
