package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomLobbyBotPanel} 纯逻辑单测：bot 名单解析、难度档位索引与循环方向、
 * 可见槽位行数上限。绘制部分（GuiGraphics）无法在无头环境验证，故不覆盖。
 */
class RoomLobbyBotPanelTest {

    // ============================================================
    // parseNames —— 与 RoomDto.botNames() 的 "A, B" 串对齐
    // ============================================================

    @Test
    void parseNames_blankOrPlaceholder_yieldsEmptySet() {
        assertTrue(RoomLobbyBotPanel.parseNames(null).isEmpty());
        assertTrue(RoomLobbyBotPanel.parseNames("").isEmpty());
        assertTrue(RoomLobbyBotPanel.parseNames("   ").isEmpty());
        assertTrue(RoomLobbyBotPanel.parseNames("-").isEmpty());
    }

    @Test
    void parseNames_trimsAndKeepsInsertionOrder() {
        Set<String> names = RoomLobbyBotPanel.parseNames("Kowalski, Fischer ,  Novak");
        assertEquals(List.of("Kowalski", "Fischer", "Novak"), List.copyOf(names));
    }

    @Test
    void parseNames_dropsEmptySegments() {
        assertEquals(Set.of("Kowalski"), RoomLobbyBotPanel.parseNames("Kowalski,, ,"));
    }

    // ============================================================
    // difficultyIndex —— 中文档位名 → 下标
    // ============================================================

    @Test
    void difficultyIndex_mapsFourTiersInOrder() {
        assertEquals(0, RoomLobbyBotPanel.difficultyIndex("简单"));
        assertEquals(1, RoomLobbyBotPanel.difficultyIndex("普通"));
        assertEquals(2, RoomLobbyBotPanel.difficultyIndex("困难"));
        assertEquals(3, RoomLobbyBotPanel.difficultyIndex("精英"));
    }

    @Test
    void difficultyIndex_unknownName_isNegative() {
        assertEquals(-1, RoomLobbyBotPanel.difficultyIndex("—"));
        assertEquals(-1, RoomLobbyBotPanel.difficultyIndex(null));
    }

    // ============================================================
    // cycleDir —— 环形序列的方向判定（方向即语义）
    // ============================================================

    @Test
    void cycleDir_sameTier_isSilent() {
        assertEquals(0, RoomLobbyBotPanel.cycleDir(2, 2, 4));
    }

    @Test
    void cycleDir_oneStepForward_isPositive() {
        assertEquals(1, RoomLobbyBotPanel.cycleDir(0, 1, 4));
        assertEquals(1, RoomLobbyBotPanel.cycleDir(2, 3, 4));
    }

    @Test
    void cycleDir_wrapAroundFromLastToFirst_stillReadsForward() {
        // 精英(3) → 简单(0) 是点击循环的回绕，必须仍读作向前，否则滚轮会倒着滑
        assertEquals(1, RoomLobbyBotPanel.cycleDir(3, 0, 4));
    }

    @Test
    void cycleDir_oneStepBackward_isNegative() {
        assertEquals(-1, RoomLobbyBotPanel.cycleDir(1, 0, 4));
        assertEquals(-1, RoomLobbyBotPanel.cycleDir(3, 2, 4));
    }

    @Test
    void cycleDir_unknownIndex_defaultsForward() {
        assertEquals(1, RoomLobbyBotPanel.cycleDir(-1, 2, 4));
        assertEquals(1, RoomLobbyBotPanel.cycleDir(1, -1, 4));
        assertEquals(1, RoomLobbyBotPanel.cycleDir(0, 1, 0));
    }

    // ============================================================
    // visibleSlotRows —— 槽位网格不得压到下方元素
    // ============================================================

    /** 槽位网格起始偏移与行距，与 {@code RoomLobbyScreen} 的实际布局一致。 */
    private static final int SLOT_TOP_DY = 75;
    private static final int SLOT_PITCH = 22;

    @Test
    void visibleSlotRows_teamModeLimit_fitsThreeRows() {
        // 队伍选择行在 top+154：3 行网格 + "还有 N 个槽位" 刚好落在其上方
        assertEquals(3, RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, 154, 6));
    }

    @Test
    void visibleSlotRows_botDividerLimit_fitsFourRows() {
        // 无队伍选择行时下界是 AI 士兵分隔线 top+176，可多放一行
        assertEquals(4, RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, 176, 6));
    }

    @Test
    void visibleSlotRows_neverExceedsGridCapacity() {
        assertEquals(6, RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, 1000, 6));
    }

    @Test
    void visibleSlotRows_absurdlyTightLimit_stillShowsOneRow() {
        assertEquals(1, RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, 0, 6));
        assertFalse(RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, 0, 6) == 0);
    }
}
