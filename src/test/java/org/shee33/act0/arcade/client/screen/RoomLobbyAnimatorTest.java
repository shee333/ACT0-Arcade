package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomLobbyAnimator} 单元测试：覆盖对角线级联延迟公式与构造时铺设的
 * {@link MenuTween.Anim} 数组规模/时序，风格与 {@code CreateRoomAnimatorTest} 一致。
 */
class RoomLobbyAnimatorTest {

    private static final float EPS = 0.001f;

    // ============================================================
    // cascadeDelayMs —— 对角线错峰级联（2 列布局，规格见 RoomLobbyAnimator 类头注释）
    // ============================================================

    @Test
    void cascadeDelayMs_firstSlot_hasZeroDelay() {
        assertEquals(0L, RoomLobbyAnimator.cascadeDelayMs(0, 2));
    }

    @Test
    void cascadeDelayMs_sameDiagonal_sharesSameDelay() {
        // index1=(row0,col1) 与 index2=(row1,col0) 同处一条反对角线，均延迟 40ms，同时出现
        assertEquals(40L, RoomLobbyAnimator.cascadeDelayMs(1, 2));
        assertEquals(40L, RoomLobbyAnimator.cascadeDelayMs(2, 2));
    }

    @Test
    void cascadeDelayMs_furtherDiagonal_delaysMore() {
        // index3=(row1,col1)=(1+1)*40=80ms
        assertEquals(80L, RoomLobbyAnimator.cascadeDelayMs(3, 2));
    }

    @Test
    void cascadeDelayMs_lastSlotOfTwelve_hasLargestDelay() {
        // index11=(row5,col1)=(5+1)*40=240ms —— 12 槽位里最后一个最晚出现
        assertEquals(240L, RoomLobbyAnimator.cascadeDelayMs(11, 2));
    }

    // ============================================================
    // 构造铺设：槽位级联应立即 start，且延迟与 cascadeDelayMs 一致
    // ============================================================

    @Test
    void constructor_startsAllTwelveSlotsImmediately() {
        RoomLobbyAnimator a = new RoomLobbyAnimator(1000L);
        assertEquals(RoomLobbyAnimator.SLOT_COUNT, a.slotCascade.length);
        for (MenuTween.Anim slot : a.slotCascade) {
            assertTrue(slot.isRunning(), "every slot cascade Anim must be started at construction time");
        }
    }

    @Test
    void constructor_slotDelaysMatchCascadeFormula() {
        long now = 1000L;
        RoomLobbyAnimator a = new RoomLobbyAnimator(now);
        for (int i = 0; i < RoomLobbyAnimator.SLOT_COUNT; i++) {
            long delay = RoomLobbyAnimator.cascadeDelayMs(i, RoomLobbyAnimator.SLOT_COLS);
            MenuTween.Anim slot = a.slotCascade[i];
            assertEquals(0f, slot.rawT(now + delay), EPS,
                    "slot[" + i + "] rawT must still be 0 exactly at its delay boundary");
            assertTrue(slot.rawT(now + delay + 1L) > 0f,
                    "slot[" + i + "] rawT should be > 0 just past its delay");
        }
    }

    @Test
    void constructor_slotDuration_completesAt200msAfterDelay() {
        RoomLobbyAnimator a = new RoomLobbyAnimator(0L);
        MenuTween.Anim first = a.slotCascade[0]; // delay=0
        assertFalse(first.isDone(199L));
        assertTrue(first.isDone(200L));
    }

    // ============================================================
    // press[] —— 按压回弹数组规模，未点击前不得自动播放
    // ============================================================

    @Test
    void constructor_pressArray_hasSevenControls() {
        RoomLobbyAnimator a = new RoomLobbyAnimator(0L);
        assertEquals(RoomLobbyAnimator.PRESS_COUNT, a.press.length);
        assertEquals(7, a.press.length);
    }

    @Test
    void constructor_pressAnims_notRunningUntilClicked() {
        RoomLobbyAnimator a = new RoomLobbyAnimator(0L);
        for (MenuTween.Anim p : a.press) {
            assertFalse(p.isRunning(), "press feedback must not auto-play; only real clicks start it");
        }
    }

    // ============================================================
    // teamIndicator / capacityRoll —— 未点击前保持未启动
    // ============================================================

    @Test
    void constructor_teamIndicatorAndCapacityRoll_startUnstarted() {
        RoomLobbyAnimator a = new RoomLobbyAnimator(0L);
        assertFalse(a.teamIndicator.isRunning());
        assertFalse(a.capacityRoll.isRunning());
    }
}
