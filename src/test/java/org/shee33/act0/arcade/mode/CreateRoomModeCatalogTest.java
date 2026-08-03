package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CreateRoomModeCatalog} 单元测试：模式目录数据（id/name/单位/上下限/步长/击杀制）、
 * 命令参数引号转义 {@code quoteArg} 与离散值最近索引 {@code nearestIndex} 的纯逻辑。
 *
 * <p>这些方法原本内嵌在 {@code CreateRoomScreen} 中，本测试确保它们被抽到独立类后
 * 行为完全一致——为下一轮 UI 重写提供安全网。
 */
class CreateRoomModeCatalogTest {

    // ------------------------------------------------------------------
    // ModeDef / MODES —— 8 个模式的字段快照必须与原 CreateRoomScreen 完全一致
    // ------------------------------------------------------------------

    @Test
    void modesListExposesExactlyEightEntriesInOriginalOrder() {
        assertEquals(8, CreateRoomModeCatalog.MODES.size());

        // 顺序：与 CreateRoomScreen.java 中 List.of(...) 一一对应
        assertEquals("duel_1v1",        CreateRoomModeCatalog.MODES.get(0).id());
        assertEquals("duel_2v2",        CreateRoomModeCatalog.MODES.get(1).id());
        assertEquals("team_deathmatch", CreateRoomModeCatalog.MODES.get(2).id());
        assertEquals("hot_zone",        CreateRoomModeCatalog.MODES.get(3).id());
        assertEquals("arms_race",       CreateRoomModeCatalog.MODES.get(4).id());
        assertEquals("team_arms_race",  CreateRoomModeCatalog.MODES.get(5).id());
        assertEquals("free_for_all",    CreateRoomModeCatalog.MODES.get(6).id());
        assertEquals("jump_sniper",     CreateRoomModeCatalog.MODES.get(7).id());
    }

    @Test
    void duelModesAreRoundBasedNotKillBased() {
        for (int i = 0; i <= 1; i++) {
            CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(i);
            assertFalse(m.killBased(), () -> "duel_1v1/2v2 必须不是击杀制: " + m.id());
            assertEquals("胜", m.targetUnit());
            assertEquals(3, m.defTarget());
            assertEquals(1, m.minTarget());
            assertEquals(15, m.maxTarget());
            assertEquals(1, m.step());
        }
    }

    @Test
    void teamDeathmatchIsKillBasedWithKillUnit() {
        CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(2);
        assertEquals("团队死斗", m.name());
        assertTrue(m.killBased());
        assertEquals("杀", m.targetUnit());
        assertEquals(30, m.defTarget());
        assertEquals(5, m.minTarget());
        assertEquals(99, m.maxTarget());
        assertEquals(5, m.step());
    }

    @Test
    void hotZoneUsesScoreUnitWithHigherBounds() {
        CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(3);
        assertEquals("热区", m.name());
        assertFalse(m.killBased());
        assertEquals("分", m.targetUnit());
        assertEquals(150, m.defTarget());
        assertEquals(30, m.minTarget());
        assertEquals(300, m.maxTarget());
        assertEquals(10, m.step());
    }

    @Test
    void armsRaceAndTeamArmsRaceUseLevelUnit() {
        for (int i = 4; i <= 5; i++) {
            CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(i);
            assertFalse(m.killBased(), () -> "军备竞赛不应当是击杀制: " + m.id());
            assertEquals("级", m.targetUnit());
            assertEquals(8, m.defTarget());
            assertEquals(4, m.minTarget());
            assertEquals(16, m.maxTarget());
            assertEquals(1, m.step());
        }
    }

    @Test
    void freeForAllIsKillBased() {
        CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(6);
        assertEquals("个人乱斗", m.name());
        assertTrue(m.killBased());
        assertEquals("杀", m.targetUnit());
        assertEquals(20, m.defTarget());
        assertEquals(5, m.minTarget());
        assertEquals(99, m.maxTarget());
        assertEquals(5, m.step());
    }

    @Test
    void jumpSniperIsRoundBasedLikeDuels() {
        CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(7);
        assertEquals("跳狙飞人", m.name());
        assertFalse(m.killBased());
        assertEquals("胜", m.targetUnit());
        assertEquals(5, m.defTarget());
        assertEquals(1, m.minTarget());
        assertEquals(15, m.maxTarget());
        assertEquals(1, m.step());
    }

    @Test
    void modeDefRecordExposesAllEightFields() {
        // 防御性：保证 record 的访问器签名稳定，未来若有人改字段顺序/命名会立刻在此失败
        CreateRoomModeCatalog.ModeDef m = CreateRoomModeCatalog.MODES.get(0);
        assertEquals("duel_1v1", m.id());
        assertEquals("单挑 1v1", m.name());
        assertFalse(m.killBased());
        assertEquals("胜", m.targetUnit());
        assertEquals(3, m.defTarget());
        assertEquals(1, m.minTarget());
        assertEquals(15, m.maxTarget());
        assertEquals(1, m.step());
    }

    // ------------------------------------------------------------------
    // quoteArg —— 命令行参数引号转义
    // ------------------------------------------------------------------

    @Test
    void quoteArgWrapsPlainStringWithDoubleQuotes() {
        assertEquals("\"alpha\"", CreateRoomModeCatalog.quoteArg("alpha"));
    }

    @Test
    void quoteArgEscapesEmbeddedDoubleQuotes() {
        // 输入： say "hi"
        // 期望输出： "say \"hi\""
        assertEquals("\"say \\\"hi\\\"\"", CreateRoomModeCatalog.quoteArg("say \"hi\""));
    }

    @Test
    void quoteArgEscapesEmbeddedBackslashes() {
        // 输入： a\b
        // 期望输出： "a\\b"
        assertEquals("\"a\\\\b\"", CreateRoomModeCatalog.quoteArg("a\\b"));
    }

    @Test
    void quoteArgEscapesBackslashBeforeDoubleQuoteInOriginalOrder() {
        // 保持原 CreateRoomScreen 的实现语义：先转反斜杠，再转双引号。
        // 输入： a\"b   （4 个字符：a, \, "）
        // 步骤 1：replace("\\", "\\\\")   → a\\"b   （5 个字符：a, \, \, "）
        // 步骤 2：replace("\"", "\\\"")   → a\\\"b  （6 个字符：a, \, \, \, ", b）
        // 加外层引号： "a\\\"b"           （8 个字符）
        assertEquals("\"a\\\\\\\"b\"", CreateRoomModeCatalog.quoteArg("a\\\"b"));
    }

    // ------------------------------------------------------------------
    // nearestIndex(int[], int)
    // ------------------------------------------------------------------

    @Test
    void nearestIndexIntReturnsExactMatchPosition() {
        assertEquals(1, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 20));
    }

    @Test
    void nearestIndexIntPicksCloserOfTwoNeighbors() {
        // 24 距离 20 是 4，距离 30 是 6 → 选 1
        assertEquals(1, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 24));
        // 17 距离 10 是 7，距离 20 是 3 → 选 1
        assertEquals(1, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 17));
    }

    @Test
    void nearestIndexIntHandlesBoundaries() {
        // 小于所有值 → 选 0
        assertEquals(0, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, -100));
        // 大于所有值 → 选 末位
        assertEquals(3, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 9_999));
        // 等于首/末元素 → 精确命中
        assertEquals(0, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 10));
        assertEquals(3, CreateRoomModeCatalog.nearestIndex(new int[]{10, 20, 30, 40}, 40));
    }

    // ------------------------------------------------------------------
    // nearestIndex(double[], double)
    // ------------------------------------------------------------------

    @Test
    void nearestIndexDoubleReturnsExactMatchPosition() {
        double[] values = {4, 6, 8, 10, 12};
        assertEquals(2, CreateRoomModeCatalog.nearestIndex(values, 8.0D));
    }

    @Test
    void nearestIndexDoublePicksCloserOfTwoNeighbors() {
        double[] values = {4, 6, 8, 10, 12};
        // 11.5 距 10 是 1.5，距 12 是 0.5 → 选 4
        assertEquals(4, CreateRoomModeCatalog.nearestIndex(values, 11.5D));
        // 5.0 距 4 与 6 都是 1.0 → 平局时取首次出现 → 0
        assertEquals(0, CreateRoomModeCatalog.nearestIndex(values, 5.0D));
        // 7.0 距 6 与 8 都是 1.0 → 平局时取首次出现 → 1
        assertEquals(1, CreateRoomModeCatalog.nearestIndex(values, 7.0D));
    }

    @Test
    void nearestIndexDoubleHandlesBoundaries() {
        double[] values = {4, 6, 8, 10, 12};
        assertEquals(0, CreateRoomModeCatalog.nearestIndex(values, -1.0D));
        assertEquals(4, CreateRoomModeCatalog.nearestIndex(values, 100.0D));
        assertEquals(0, CreateRoomModeCatalog.nearestIndex(values, 4.0D));
        assertEquals(4, CreateRoomModeCatalog.nearestIndex(values, 12.0D));
    }
}
