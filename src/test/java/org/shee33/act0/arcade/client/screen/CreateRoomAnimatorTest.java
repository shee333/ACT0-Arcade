package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CreateRoomAnimator} 单元测试：覆盖纯逻辑辅助方法（滚动方向 / 钳制 / 循环索引）
 * 以及构造时铺设的 {@link MenuTween.Anim} 字段是否符合规格文档 §3 / §4 / §5 的时序参数。
 *
 * <p>{@link CreateRoomAnimator#clamp01(float)} / {@link CreateRoomAnimator#lerp(float, float, float)}
 * 是对 {@code Math.max/min} 与基础线性插值的直译（无分支、无业务规则），不在此处追加单测——
 * 见 {@code MenuTweenTest} 的写法，保持测试聚焦在带"业务意义"的分支上。
 */
class CreateRoomAnimatorTest {

    private static final float EPS = 0.001f;

    // ============================================================
    // scrollDir —— 方向性滚轮的来源（规格 §4.2）
    // ============================================================

    @Test
    void scrollDir_toGreater_returnsPositiveOne() {
        assertEquals(1, CreateRoomAnimator.scrollDir(2, 5));
    }

    @Test
    void scrollDir_toSmaller_returnsNegativeOne() {
        assertEquals(-1, CreateRoomAnimator.scrollDir(5, 2));
    }

    @Test
    void scrollDir_toEqual_returnsZero() {
        assertEquals(0, CreateRoomAnimator.scrollDir(3, 3));
    }

    // ============================================================
    // wrapIndex —— 循环容器索引推进（规格 §5 竞技场 + 1 / -1）
    // ============================================================

    @Test
    void wrapIndex_forward_wrapsAtSize() {
        assertEquals(0, CreateRoomAnimator.wrapIndex(2, 1, 3));
    }

    @Test
    void wrapIndex_backward_keepsPositive() {
        // (0 + (-1)) mod 3 == 2（Math.floorMod 给我们正数，不要 -1）
        assertEquals(2, CreateRoomAnimator.wrapIndex(0, -1, 3));
    }

    @Test
    void wrapIndex_sizeZero_returnsZero_safeGuard() {
        assertEquals(0, CreateRoomAnimator.wrapIndex(7, 3, 0));
    }

    // ============================================================
    // 构造铺设：开场级联 Anim 字段的 duration / delay 应严格对应规格文档 §3 表
    // ============================================================

    @Test
    void constructor_openingCascadeDelaysMatchSpec() {
        long now = 1000L;
        CreateRoomAnimator a = new CreateRoomAnimator(now, 8);

        // 遮罩 / 面板：delay = 0
        assertEquals(0f, a.backdrop.rawT(now), EPS);
        assertEquals(0f, a.panel.rawT(now), EPS);

        // 金线：delay = 300，所以 now=1000（elapsed=-300）应返回 0
        assertEquals(0f, a.titleLine.rawT(now), EPS);

        for (int i = 0; i < a.leftColumn.length; i++) {
            MenuTween.Anim item = a.leftColumn[i];
            long delay = 300L + (long) i * 35L;
            assertEquals(0f, item.rawT(now), EPS,
                    "leftColumn[" + i + "] rawT must be 0 at start time (before its delay)");
            assertEquals(0f, item.rawT(now + delay - 1L), EPS,
                    "leftColumn[" + i + "] rawT must still be 0 right before its delay");
            assertTrue(item.rawT(now + delay + 1L) > 0f,
                    "leftColumn[" + i + "] rawT should be > 0 just past its delay");
        }

        // 右栏：delay = 120 + i*60
        assertEquals(6, a.rightColumn.length, "rightColumn must cover all 6 sections per spec §3 #5");
        long lastRightDelay = 120L + 5L * 60L; // 420ms
        assertEquals(0f, a.rightColumn[5].rawT(now), EPS);
        assertEquals(0f, a.rightColumn[5].rawT(now + lastRightDelay - 1L), EPS);

        // 底部：delay = 120 + 6*60 = 480
        assertEquals(0f, a.footer.rawT(now), EPS);
        assertEquals(0f, a.footer.rawT(now + 479L), EPS);
        assertTrue(a.footer.rawT(now + 481L) > 0f, "footer should start progressing just past delay=480");
    }

    @Test
    void constructor_openingCascadeDurationsMatchSpec() {
        // 直接通过 Anim 的 duration 反向验证（用反射或纯行为验证）
        // 用一个简单办法：在 start + durationMs 边界检查 isDone
        CreateRoomAnimator a = new CreateRoomAnimator(0L, 8);
        // backdrop 的 duration = 200
        a.backdrop.start(0L);
        // 在 199 时尚未完成，200 时刚好完成（duration 边界语义）
        // elapsed 校验：rawT >= 1 ⇒ isDone = true
        // 边界检查 —— rawT 在 200 时等于 200/200 = 1，isDone 应为 true
        assertTrue(a.backdrop.isDone(200L));
        assertFalse(a.backdrop.isDone(199L));

        // titleLine 的 delay = 300, duration = 350
        a.titleLine.start(0L);
        // 在 elapsed = 300 (now = 300) 前 rawT = 0（delay 还没过）
        assertEquals(0f, a.titleLine.rawT(299L), EPS);
        // 在 now = 300 (elapsed = 0, delay 恰好结束) rawT = 0；刚出 delay 也要 0
        assertEquals(0f, a.titleLine.rawT(300L), EPS);
        // elapsed = 1, rawT = 1/350
        assertTrue(a.titleLine.rawT(301L) > 0f);
        // 在 now = 300 + 350 = 650 时，rawT = 1, isDone
        assertTrue(a.titleLine.isDone(650L));
    }

    @Test
    void constructor_assignsHoverProgressAndRollAnimArrays() {
        CreateRoomAnimator a = new CreateRoomAnimator(0L, 8);
        assertEquals(8, a.hoverProgress.length);
        assertEquals(3, a.scoreTimePlayersRoll.length);
    }

    @Test
    void constructor_zeroModeCount_keepsEmptyHoverArray() {
        // 边界：modeCount=0 时 hoverProgress 应为空数组而非抛 NPE
        CreateRoomAnimator a = new CreateRoomAnimator(0L, 0);
        assertEquals(0, a.hoverProgress.length);
    }

    @Test
    void defaultModeCount_matchesCreateRoomModes() {
        // 与 CreateRoomScreen.MODES 同为 8（防止两个文件漂移；具体值见规格 §2.3）
        assertEquals(8, CreateRoomAnimator.defaultModeCount());
    }

    // ============================================================
    // 回归测试（P1⑥）：模式数超过原先硬编码的 10 不应导致左栏级联数组越界
    // ============================================================

    @Test
    void constructor_modeCountAboveTen_leftColumnGrowsWithoutOverflow() {
        CreateRoomAnimator a = new CreateRoomAnimator(0L, 12);
        assertEquals(14, a.leftColumn.length, "leftColumn must be modeCount + 2 (label + N modes + settings button)");
        assertEquals(12, a.hoverProgress.length);
    }

    @Test
    void constructor_modeCountEight_leftColumnStillTen() {
        // 8 模式时 leftColumn 长度应与旧硬编码常量 10 保持一致，不因重构改变现有行为
        CreateRoomAnimator a = new CreateRoomAnimator(0L, 8);
        assertEquals(10, a.leftColumn.length);
    }

    // ============================================================
    // expandProgress —— 房间人数区块收展方向（P0②回归）
    // ============================================================

    @Test
    void expandProgress_visibleTrue_followsEasedDirectly() {
        assertEquals(0f, CreateRoomAnimator.expandProgress(true, 0f), EPS);
        assertEquals(0.5f, CreateRoomAnimator.expandProgress(true, 0.5f), EPS);
        assertEquals(1f, CreateRoomAnimator.expandProgress(true, 1f), EPS);
    }

    @Test
    void expandProgress_visibleFalse_invertsEased() {
        // 收起时必须是 1→0（随 eased 增大而减小），不能和展开用同一方向，否则区块会反向跳变
        assertEquals(1f, CreateRoomAnimator.expandProgress(false, 0f), EPS);
        assertEquals(0.5f, CreateRoomAnimator.expandProgress(false, 0.5f), EPS);
        assertEquals(0f, CreateRoomAnimator.expandProgress(false, 1f), EPS);
    }

    // ============================================================
    // hoverState —— 模式列表项悬停进入/离开的目标 padding / 文字透明度（§4.3）
    // ============================================================

    @Test
    void hoverState_entering_startsAtBaseEndsAtHover() {
        CreateRoomAnimator.HoverState start = CreateRoomAnimator.hoverState(true, 0f);
        assertEquals(6f, start.padding(), EPS);
        assertEquals(0.55f, start.textAlpha(), EPS);

        CreateRoomAnimator.HoverState mid = CreateRoomAnimator.hoverState(true, 0.5f);
        assertEquals(8f, mid.padding(), EPS);
        assertEquals(0.725f, mid.textAlpha(), EPS);

        CreateRoomAnimator.HoverState end = CreateRoomAnimator.hoverState(true, 1f);
        assertEquals(10f, end.padding(), EPS);
        assertEquals(0.9f, end.textAlpha(), EPS);
    }

    @Test
    void hoverState_leaving_isSymmetricReverseOfEntering() {
        CreateRoomAnimator.HoverState start = CreateRoomAnimator.hoverState(false, 0f);
        assertEquals(10f, start.padding(), EPS);
        assertEquals(0.9f, start.textAlpha(), EPS);

        CreateRoomAnimator.HoverState mid = CreateRoomAnimator.hoverState(false, 0.5f);
        assertEquals(8f, mid.padding(), EPS);
        assertEquals(0.725f, mid.textAlpha(), EPS);

        CreateRoomAnimator.HoverState end = CreateRoomAnimator.hoverState(false, 1f);
        assertEquals(6f, end.padding(), EPS);
        assertEquals(0.55f, end.textAlpha(), EPS);
    }

    // ============================================================
    // rollOffset —— 数值滚轮/竞技场滑动的位移公式（P1⑤：振幅须覆盖裁剪区尺寸）
    // ============================================================

    @Test
    void rollOffset_dirPositive_oldSlidesUp_newArrivesFromBelow() {
        int clip = 18;
        assertEquals(0f, CreateRoomAnimator.rollOffset(1, 0f, clip, true), EPS);
        assertEquals(-9f, CreateRoomAnimator.rollOffset(1, 0.5f, clip, true), EPS);
        assertEquals(-18f, CreateRoomAnimator.rollOffset(1, 1f, clip, true), EPS);

        assertEquals(18f, CreateRoomAnimator.rollOffset(1, 0f, clip, false), EPS);
        assertEquals(9f, CreateRoomAnimator.rollOffset(1, 0.5f, clip, false), EPS);
        assertEquals(0f, CreateRoomAnimator.rollOffset(1, 1f, clip, false), EPS);
    }

    @Test
    void rollOffset_dirNegative_isMirroredAcrossZero() {
        int clip = 92;
        assertEquals(0f, CreateRoomAnimator.rollOffset(-1, 0f, clip, true), EPS);
        assertEquals(92f, CreateRoomAnimator.rollOffset(-1, 1f, clip, true), EPS);

        assertEquals(-92f, CreateRoomAnimator.rollOffset(-1, 0f, clip, false), EPS);
        assertEquals(0f, CreateRoomAnimator.rollOffset(-1, 1f, clip, false), EPS);
    }

    @Test
    void rollOffset_atFullProgress_oldIsFullyClearOfClipSize() {
        // e=1 时旧文案偏移量的绝对值必须等于裁剪区尺寸本身，否则会有一帧还残留在裁剪区内可见
        int clip = 92;
        assertEquals((float) clip, Math.abs(CreateRoomAnimator.rollOffset(1, 1f, clip, true)), EPS);
        assertEquals((float) clip, Math.abs(CreateRoomAnimator.rollOffset(-1, 1f, clip, true)), EPS);
    }
}
