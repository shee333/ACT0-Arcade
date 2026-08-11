package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PauseMenuAnim} 的边界单测。
 *
 * <p>这份测试的存在理由不只是"覆盖率"：{@link PauseMenuAnim} 是姊妹仓库 ACT0-Battlefield
 * {@code core.PauseMenuAnim} 的强制副本（两个 jar 无法共享代码），两边时序必须逐值一致。
 * 断言里因此刻意写出<b>字面数值</b>（800/180/220/1400/300/0.92/0.82/0.55）而非只引用常量——
 * 若哪天有人只改了一边的常量值，字面断言会立刻失败，而"常量对比常量"的写法永远绿。
 */
class PauseMenuAnimTest {

    private static final float EPS = 1e-5f;

    /** 街机菜单是 4 项（回到游戏/设置/退出战局/退出游戏）。 */
    private static final int ARCADE_ITEMS = 4;

    @Test
    void progressStaysAtZeroUntilTheDelayElapses() {
        assertEquals(0f, PauseMenuAnim.progress(0, 140, 260), EPS);
        assertEquals(0f, PauseMenuAnim.progress(140, 140, 260), EPS, "延迟刚满时仍是 0，不得闪现");
        assertEquals(0.5f, PauseMenuAnim.progress(270, 140, 260), EPS);
        assertEquals(1f, PauseMenuAnim.progress(400, 140, 260), EPS);
        assertEquals(1f, PauseMenuAnim.progress(9999, 140, 260), EPS);
    }

    /** 级联的意义就是各项延迟不同：第 i 项必须比第 i-1 项晚 55ms 起步。 */
    @Test
    void itemsCascadeWithFiftyFiveMillisecondStagger() {
        assertEquals(140, PauseMenuAnim.ITEM_DELAY_MS, "开场级联起点必须与战地一致");
        assertEquals(55, PauseMenuAnim.ITEM_STAGGER_MS, "项间错峰必须与战地一致");
        for (int i = 0; i < ARCADE_ITEMS; i++) {
            long startAt = 140L + i * 55L;
            assertEquals(0f, PauseMenuAnim.itemOpenProgress(startAt, i), EPS,
                    "第 " + i + " 项在自己的延迟点上应仍未开始");
            assertTrue(PauseMenuAnim.itemOpenProgress(startAt + 10, i) > 0f,
                    "第 " + i + " 项过了延迟点应已开始");
        }
        long t = 140L + 30L;
        assertTrue(PauseMenuAnim.itemOpenProgress(t, 0) > 0f, "首项已在推进");
        assertEquals(0f, PauseMenuAnim.itemOpenProgress(t, 1), EPS, "次项此刻还没开始");
    }

    @Test
    void allItemsFinishByTheEndOfTheOpeningSequence() {
        long total = 140L + (ARCADE_ITEMS - 1) * 55L + PauseMenuAnim.ITEM_IN_MS;
        for (int i = 0; i < ARCADE_ITEMS; i++) {
            assertEquals(1f, PauseMenuAnim.itemOpenProgress(total, i), EPS);
        }
    }

    /** 隐藏菜单不能早于遮罩淡完，否则会看到世界"啪"一下露出来。 */
    @Test
    void closeSequenceCoversBothItemsAndOverlay() {
        int total = PauseMenuAnim.closeTotalMs(ARCADE_ITEMS);
        assertEquals(340, total, "4 项时关闭总时长由遮罩（120+220）决定");
        for (int i = 0; i < ARCADE_ITEMS; i++) {
            assertEquals(1f, PauseMenuAnim.itemCloseProgress(total, i), EPS, "第 " + i + " 项必须已淡出完毕");
        }
        assertEquals(22, PauseMenuAnim.CLOSE_ITEM_STAGGER_MS, "关闭错峰必须与战地一致");
        assertTrue(PauseMenuAnim.closeTotalMs(20) > 340, "项数很多时应由项级联决定总时长");
    }

    @Test
    void indicatorStretchPeaksMidSlideAndReturnsToOneAtBothEnds() {
        assertEquals(1f, PauseMenuAnim.indicatorStretch(0f), EPS, "起点必须无拉伸");
        assertEquals(1f, PauseMenuAnim.indicatorStretch(1f), EPS, "终点必须回到无拉伸");
        assertEquals(1.6f, PauseMenuAnim.indicatorStretch(0.5f), EPS, "峰值 ×1.6");
        assertTrue(PauseMenuAnim.indicatorStretch(0.25f) > 1f);
    }

    @Test
    void holdFillReachesOneExactlyAtEightHundredMillis() {
        assertEquals(800, PauseMenuAnim.HOLD_CONFIRM_MS);
        assertEquals(0f, PauseMenuAnim.holdFill(0, -1f, 0), EPS);
        assertEquals(0.5f, PauseMenuAnim.holdFill(400, -1f, 0), EPS);
        assertTrue(PauseMenuAnim.holdFill(799, -1f, 0) < 1f, "799ms 还不能算填满");
        assertEquals(1f, PauseMenuAnim.holdFill(800, -1f, 0), EPS);
        assertFalse(PauseMenuAnim.holdCompleted(799));
        assertTrue(PauseMenuAnim.holdCompleted(800));
    }

    /**
     * 回退必须从<b>松手当刻的宽度</b>开始，而不是从满条开始。
     *
     * <p>按到 10% 就松手却看到满条缩回去，会让玩家误以为自己差点触发了退出战局。
     */
    @Test
    void holdReleaseRewindsFromTheWidthAtReleaseNotFromFull() {
        float atRelease = PauseMenuAnim.holdFill(80, -1f, 0);
        assertEquals(0.1f, atRelease, EPS);
        assertEquals(atRelease, PauseMenuAnim.holdFill(80, atRelease, 0), EPS, "松手瞬间不得跳变");
        assertTrue(PauseMenuAnim.holdFill(80, atRelease, 90) < atRelease, "应当在回退");
        assertEquals(0f, PauseMenuAnim.holdFill(80, atRelease, 180), EPS, "180ms 回退到零");
        assertEquals(180, PauseMenuAnim.HOLD_RELEASE_MS);
    }

    @Test
    void holdFillNeverLeavesTheUnitRange() {
        for (long ms = 0; ms <= 1200; ms += 37) {
            float v = PauseMenuAnim.holdFill(ms, -1f, 0);
            assertTrue(v >= 0f && v <= 1f, "填充比例越界: " + v);
        }
        for (long ms = 0; ms <= 400; ms += 13) {
            float v = PauseMenuAnim.holdFill(500, 0.62f, ms);
            assertTrue(v >= 0f && v <= 0.62f, "回退比例越界: " + v);
        }
    }

    @Test
    void toastFadesInHoldsThenFadesOut() {
        assertEquals(220, PauseMenuAnim.TOAST_IN_MS);
        assertEquals(1400, PauseMenuAnim.TOAST_HOLD_MS);
        assertEquals(300, PauseMenuAnim.TOAST_OUT_MS);
        assertEquals(0f, PauseMenuAnim.toastAlpha(0), EPS);
        assertEquals(0f, PauseMenuAnim.toastAlpha(-50), EPS, "尚未开始弹出时不可见");
        assertTrue(PauseMenuAnim.toastAlpha(110) > 0f && PauseMenuAnim.toastAlpha(110) < 1f, "淡入中途");
        assertEquals(1f, PauseMenuAnim.toastAlpha(220), EPS);
        assertEquals(1f, PauseMenuAnim.toastAlpha(220 + 700), EPS, "停留期恒为 1");
        assertEquals(1f, PauseMenuAnim.toastAlpha(220 + 1400), EPS, "停留末刻仍满不透明");
        assertTrue(PauseMenuAnim.toastAlpha(220 + 1400 + 200) < 1f, "淡出已开始");
        assertEquals(0f, PauseMenuAnim.toastAlpha(220 + 1400 + 300), EPS);
        assertEquals(0f, PauseMenuAnim.toastAlpha(220 + 1400 + 300 + 5000), EPS);
    }

    /** 左重右轻是"游戏没停"的载体：右缘必须明显比左缘透，否则战场看不见。 */
    @Test
    void overlayIsHeavyOnTheLeftAndLightOnTheRight() {
        assertEquals(0.92f, PauseMenuAnim.overlayAlphaAt(0f), EPS);
        assertEquals(0.82f, PauseMenuAnim.overlayAlphaAt(0.45f), EPS);
        assertEquals(0.55f, PauseMenuAnim.overlayAlphaAt(1f), EPS);
        assertTrue(PauseMenuAnim.overlayAlphaAt(1f) < PauseMenuAnim.overlayAlphaAt(0f) - 0.3f,
                "右缘必须显著更透，战场要保持可见");
        assertEquals(0.92f, PauseMenuAnim.overlayAlphaAt(-1f), EPS, "越界输入按左缘钳住");
        assertEquals(0.55f, PauseMenuAnim.overlayAlphaAt(2f), EPS, "越界输入按右缘钳住");
    }

    @Test
    void overlayAlphaIsMonotonicallyDecreasing() {
        float prev = 1f;
        for (float x = 0f; x <= 1.0001f; x += 0.02f) {
            float a = PauseMenuAnim.overlayAlphaAt(x);
            assertTrue(a <= prev + EPS, "遮罩不透明度不得回升: x=" + x);
            prev = a;
        }
    }

    @Test
    void easingsAreClampedAndHitTheirEndpoints() {
        assertEquals(0f, PauseMenuAnim.outCubic(0f), EPS);
        assertEquals(1f, PauseMenuAnim.outCubic(1f), EPS);
        assertEquals(0f, PauseMenuAnim.inCubic(0f), EPS);
        assertEquals(1f, PauseMenuAnim.inCubic(1f), EPS);
        assertEquals(1f, PauseMenuAnim.outExpo(1f), EPS);
        assertEquals(0f, PauseMenuAnim.outBack(0f), EPS);
        assertEquals(1f, PauseMenuAnim.outBack(1f), EPS);
        assertEquals(0f, PauseMenuAnim.outCubic(-5f), EPS, "越界输入必须钳住");
        assertEquals(1f, PauseMenuAnim.outCubic(5f), EPS);
        assertTrue(PauseMenuAnim.outBack(0.75f) > 1f, "outBack 应有过冲");
    }

    /** 与 {@link MenuTween.Ease} 同名曲线必须逐点一致，否则同一界面里两套缓动会手感分裂。 */
    @Test
    void easingsMatchTheSharedMenuTweenEngine() {
        for (float t = 0f; t <= 1.0001f; t += 0.05f) {
            assertEquals(MenuTween.Ease.OUT_CUBIC.apply(t), PauseMenuAnim.outCubic(t), 1e-4f);
            assertEquals(MenuTween.Ease.IN_CUBIC.apply(t), PauseMenuAnim.inCubic(t), 1e-4f);
            assertEquals(MenuTween.Ease.OUT_EXPO.apply(t), PauseMenuAnim.outExpo(t), 1e-4f);
            assertEquals(MenuTween.Ease.OUT_BACK.apply(t), PauseMenuAnim.outBack(t), 1e-4f);
        }
    }
}
