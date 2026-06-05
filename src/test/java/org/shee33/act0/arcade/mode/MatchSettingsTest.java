package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.LoadoutRuleset;
import org.shee33.act0.arcade.round.RespawnPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 四模式预设描述符校验：方数/队伍大小/计分方式/复活策略/赛制，且全部使用 ARCADE 规则集。
 */
class MatchSettingsTest {

    @Test
    void duel1v1Preset() {
        MatchSettings s = MatchSettings.duel1v1();
        assertEquals(2, s.sideCount());
        assertEquals(1, s.teamSize());
        assertEquals(2, s.totalPlayers());
        assertEquals(ScoringMode.ROUND_WIN, s.scoringMode());
        assertEquals(3, s.roundFormat().pointsToWin());
        assertEquals(RespawnPolicy.FIXED_SPAWN, s.respawnPolicy());
        assertEquals(LoadoutRuleset.ARCADE, s.ruleset());
    }

    @Test
    void duel2v2Preset() {
        MatchSettings s = MatchSettings.duel2v2();
        assertEquals(2, s.sideCount());
        assertEquals(2, s.teamSize());
        assertEquals(4, s.totalPlayers());
        assertEquals(ScoringMode.ROUND_WIN, s.scoringMode());
        assertEquals(RespawnPolicy.NEAR_TEAMMATE, s.respawnPolicy());
    }

    @Test
    void teamDeathmatchPreset() {
        MatchSettings s = MatchSettings.teamDeathmatch(6, 50);
        assertEquals(2, s.sideCount());
        assertEquals(6, s.teamSize());
        assertEquals(ScoringMode.KILL_COUNT, s.scoringMode());
        assertEquals(50, s.roundFormat().pointsToWin());
        assertEquals(RespawnPolicy.NEAR_TEAMMATE, s.respawnPolicy());
    }

    @Test
    void freeForAllPreset() {
        MatchSettings s = MatchSettings.freeForAll(8, 20);
        assertEquals(8, s.sideCount());
        assertEquals(1, s.teamSize());
        assertEquals(8, s.totalPlayers());
        assertEquals(ScoringMode.KILL_COUNT, s.scoringMode());
        assertEquals(RespawnPolicy.RANDOM, s.respawnPolicy());
    }
}
