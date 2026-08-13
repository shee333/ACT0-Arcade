package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotModeTest {

    @Test
    void mapsAllFiveTargetModes() {
        assertEquals(BotMode.DUEL_1V1, BotMode.fromModeId("duel_1v1"));
        assertEquals(BotMode.DUEL_2V2, BotMode.fromModeId("duel_2v2"));
        assertEquals(BotMode.TEAM_DEATHMATCH, BotMode.fromModeId("team_deathmatch"));
        assertEquals(BotMode.FREE_FOR_ALL, BotMode.fromModeId("free_for_all"));
        assertEquals(BotMode.HOT_ZONE, BotMode.fromModeId("hot_zone"));
    }

    /**
     * 未做差异化的模式必须落到 OTHER 而不是被误认成某个已调过的模式——被误认会让军备竞赛的 bot
     * 按热区的目标吸引力行动，表现为满场往一个不存在的点跑。
     */
    @Test
    void unhandledModesFallBackToOther() {
        assertEquals(BotMode.OTHER, BotMode.fromModeId("arms_race"));
        assertEquals(BotMode.OTHER, BotMode.fromModeId("team_arms_race"));
        assertEquals(BotMode.OTHER, BotMode.fromModeId("jump_sniper"));
        assertEquals(BotMode.OTHER, BotMode.fromModeId("something_new"));
        assertEquals(BotMode.OTHER, BotMode.fromModeId(""));
        assertEquals(BotMode.OTHER, BotMode.fromModeId(null));
    }

    @Test
    void soloModesHaveNoTeammates() {
        assertFalse(BotMode.DUEL_1V1.hasTeammates());
        assertFalse(BotMode.FREE_FOR_ALL.hasTeammates());
        assertTrue(BotMode.DUEL_2V2.hasTeammates());
        assertTrue(BotMode.TEAM_DEATHMATCH.hasTeammates());
        assertTrue(BotMode.HOT_ZONE.hasTeammates());
    }

    @Test
    void onlyHotZoneHasObjective() {
        for (BotMode mode : BotMode.values()) {
            assertEquals(mode == BotMode.HOT_ZONE, mode.hasObjective(), mode + " 的目标点判定");
        }
    }
}
