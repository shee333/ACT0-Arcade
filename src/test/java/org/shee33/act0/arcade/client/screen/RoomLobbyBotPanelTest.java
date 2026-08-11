package org.shee33.act0.arcade.client.screen;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.bot.AimModel;

import java.util.List;
import java.util.Locale;
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

    /**
     * 直接引用 {@code RoomLobbyScreen} 的真实布局常量，而非抄一份字面量。
     *
     * <p>抄字面量的版本在面板高度调整时仍会全绿——测试看着像在锁布局，实际早已与布局脱节。
     * 这几个常量都是编译期常量，引用它们会被 javac 内联，不会加载 MC 的 {@code Screen}。
     */
    private static final int SLOT_TOP_DY = RoomLobbyScreen.SLOT_TOP_DY;
    private static final int SLOT_PITCH = 22;
    private static final int SLOT_ROWS_MAX = 6;
    private static final int TEAM_ROW_H = 18;
    private static final int BOT_ROW_H = 20;
    private static final int CONTROLS_DY = RoomLobbyScreen.H - 30;

    /**
     * 缩放后 GUI 高度的下限：{@code Window.calculateScale} 只在 {@code 高/(缩放+1) >= 240}
     * 时才继续提升缩放，故任何分辨率下可用高度都不小于 240，面板超过即被上下切掉。
     */
    private static final int MIN_GUI_HEIGHT = 240;

    @Test
    void panelHeightFitsMinimumGuiHeight() {
        assertTrue(RoomLobbyScreen.H <= MIN_GUI_HEIGHT,
                "面板高 " + RoomLobbyScreen.H + " 超过 GUI 高度下限 " + MIN_GUI_HEIGHT
                        + "，1280×720 自动缩放下会被切掉 " + (RoomLobbyScreen.H - MIN_GUI_HEIGHT) + "px");
    }

    @Test
    void rowsDoNotOverlap() {
        assertTrue(RoomLobbyScreen.TEAM_ROW_DY + TEAM_ROW_H <= RoomLobbyScreen.BOT_DIVIDER_DY,
                "队伍选择行压到 AI 士兵分隔线");
        assertTrue(RoomLobbyScreen.BOT_DIVIDER_DY < RoomLobbyScreen.BOT_ROW_DY,
                "分隔线未在 AI 士兵行之上");
        assertTrue(RoomLobbyScreen.BOT_ROW_DY + BOT_ROW_H <= CONTROLS_DY,
                "AI 士兵行压到底部控件行");
        assertTrue(CONTROLS_DY + BOT_ROW_H <= RoomLobbyScreen.H, "底部控件行溢出面板");
    }

    @Test
    void slotGridNeverOverlapsTheRowBelowIt() {
        for (int limit : new int[]{RoomLobbyScreen.TEAM_ROW_DY, RoomLobbyScreen.BOT_DIVIDER_DY}) {
            int rows = RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH, limit, SLOT_ROWS_MAX);
            int overflowTextBottom = SLOT_TOP_DY + rows * SLOT_PITCH + 4 + 8;
            assertTrue(overflowTextBottom <= limit,
                    "槽位网格加溢出行落到 " + overflowTextBottom + "，压过下界 " + limit);
        }
    }

    @Test
    void teamModeStillShowsAFullFourPlayerRoom() {
        int rows = RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH,
                RoomLobbyScreen.TEAM_ROW_DY, SLOT_ROWS_MAX);
        // 2v2 与 4 人团队房是团队模式最常见的容量，必须能一屏显示满员
        assertTrue(rows * 2 >= 4, "团队模式仅能显示 " + (rows * 2) + " 格，4 人房都放不下");
    }

    @Test
    void nonTeamModeShowsMoreRowsThanTeamMode() {
        int team = RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH,
                RoomLobbyScreen.TEAM_ROW_DY, SLOT_ROWS_MAX);
        int nonTeam = RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, SLOT_PITCH,
                RoomLobbyScreen.BOT_DIVIDER_DY, SLOT_ROWS_MAX);
        assertTrue(nonTeam > team, "无队伍选择行时应能多放槽位");
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

    // ============================================================
    // 难度档位：客户端副本必须与 AimModel.Difficulty 完全对齐
    // ============================================================

    @Test
    void difficultyArraysCoverEveryEnumConstant() {
        int expected = AimModel.Difficulty.values().length;
        assertEquals(expected, RoomLobbyBotPanel.DIFFICULTY_NAMES.length,
                "新增难度档后客户端展示名数组未同步，大厅将循环不到该档");
        assertEquals(expected, RoomLobbyBotPanel.DIFFICULTY_ARGS.length,
                "新增难度档后客户端指令参数数组未同步");
    }

    @Test
    void difficultyArgsMatchCommandLiterals() {
        AimModel.Difficulty[] tiers = AimModel.Difficulty.values();
        for (int i = 0; i < tiers.length; i++) {
            // ArcadeCommand 按 tier.name().toLowerCase(Locale.ROOT) 生成命令字面量
            assertEquals(tiers[i].name().toLowerCase(Locale.ROOT), RoomLobbyBotPanel.DIFFICULTY_ARGS[i],
                    "第 " + i + " 档的指令参数与服务端命令树不一致，点击后服务端将拒绝该指令");
        }
    }

    @Test
    void difficultyNamesMatchServerDisplayNames() {
        AimModel.Difficulty[] tiers = AimModel.Difficulty.values();
        for (int i = 0; i < tiers.length; i++) {
            // 服务端经 RoomDto.botDifficulty() 下发的正是 displayName()，对不上则滚轮方向判定失效
            assertEquals(tiers[i].displayName(), RoomLobbyBotPanel.DIFFICULTY_NAMES[i],
                    "第 " + i + " 档的展示名与服务端下发值不一致");
        }
    }

    @Test
    void difficultyIndexResolvesEveryServerDisplayName() {
        AimModel.Difficulty[] tiers = AimModel.Difficulty.values();
        for (int i = 0; i < tiers.length; i++) {
            assertEquals(i, RoomLobbyBotPanel.difficultyIndex(tiers[i].displayName()),
                    "服务端下发的展示名在客户端解析不出档位下标");
        }
    }
}
