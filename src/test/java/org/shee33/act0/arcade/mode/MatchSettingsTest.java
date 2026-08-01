package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.LoadoutRuleset;
import org.shee33.act0.arcade.round.RespawnPolicy;
import org.shee33.act0.arcade.round.RoundFormat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八模式预设描述符校验：方数/队伍大小/计分方式/复活策略/赛制，且全部使用 ARCADE 规则集。
 *
 * <p>前 4 个预设（单挑/2v2/团队死斗/个人乱斗）直接来自 {@link MatchSettings} 的静态工厂方法。
 * 后 4 个预设（热区/跳狙/个人军备竞赛/团队军备竞赛）目前仅以内联 Builder 调用的形式定义在
 * {@code match.MatchLauncher#buildSettings}（该类依赖 Minecraft API，不可单测），因此这里按照
 * 其文档化的固定参数用 {@link MatchSettings.Builder} 重建等价预设并校验 {@link MatchSettings}
 * 自身的归一化/钳制逻辑，而不修改任何生产代码。
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

    // ---------------- 新增 4 种模式：热区 / 跳狙 / 个人军备竞赛 / 团队军备竞赛 ----------------

    /**
     * 热区预设：等价重建 {@code match.MatchLauncher} 中 {@code "hot_zone"} 分支
     * （sizingCount=8 时 teamSize = max(1, 8/2) = 4，目标分沿用默认 150）。
     */
    @Test
    void hotZonePresetMirrorsMatchLauncherDefaults() {
        MatchSettings s = MatchSettings.builder("hot_zone", "热区")
                .sideCount(2).teamSize(4)
                .scoringMode(ScoringMode.HOT_ZONE)
                .roundFormat(RoundFormat.firstTo(150))
                .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE)
                .build();

        assertEquals(2, s.sideCount());
        assertEquals(4, s.teamSize());
        assertEquals(8, s.totalPlayers());
        assertEquals(ScoringMode.HOT_ZONE, s.scoringMode());
        assertEquals(150, s.roundFormat().pointsToWin());
        assertEquals(RespawnPolicy.NEAR_TEAMMATE, s.respawnPolicy());
        assertEquals(LoadoutRuleset.ARCADE, s.ruleset());
        assertTrue(s.flexible());
        // 未显式设置热区参数时应落到 Builder 默认值
        assertEquals(60 * 20, s.hotZoneRotateTicks());
        assertEquals(6.0D, s.hotZoneRadius());
        assertEquals(1, s.hotZoneScorePerSecond());
    }

    /**
     * 跳狙预设：全程强制狙击枪随机武器模式，回合制先到 5 局，固定出生点复活。
     */
    @Test
    void jumpSniperPresetForcesSniperRandomMode() {
        MatchSettings s = MatchSettings.builder("jump_sniper", "跳狙飞人")
                .sideCount(2).teamSize(2)
                .scoringMode(ScoringMode.ROUND_WIN)
                .roundFormat(RoundFormat.firstTo(5))
                .respawnPolicy(RespawnPolicy.FIXED_SPAWN)
                .randomMode(RandomWeaponMode.SNIPER)
                .build();

        assertEquals(RespawnPolicy.FIXED_SPAWN, s.respawnPolicy());
        assertEquals(5, s.roundFormat().pointsToWin());
        assertEquals(RandomWeaponMode.SNIPER, s.randomWeaponMode());
        assertTrue(s.randomWeapons());
        assertFalse(s.flexible());
    }

    /**
     * 个人军备竞赛预设：目标分等于默认武器晋级路线的等级数（8），随机点复活，弹性人数。
     */
    @Test
    void armsRacePresetUsesDefaultProgressionLength() {
        List<ArmsRaceLevel> levels = ArmsRaceLevel.defaultProgression();

        MatchSettings s = MatchSettings.builder("arms_race", "军备竞赛")
                .sideCount(8).teamSize(1)
                .scoringMode(ScoringMode.ARMS_RACE)
                .roundFormat(RoundFormat.firstTo(levels.size()))
                .respawnPolicy(RespawnPolicy.RANDOM)
                .armsRaceLevels(levels)
                .build();

        assertEquals(8, s.sideCount());
        assertEquals(1, s.teamSize());
        assertEquals(ScoringMode.ARMS_RACE, s.scoringMode());
        assertEquals(RespawnPolicy.RANDOM, s.respawnPolicy());
        assertEquals(8, s.roundFormat().pointsToWin());
        assertEquals(8, s.armsRaceLevels().size());
        assertTrue(s.flexible());
    }

    /**
     * 团队军备竞赛预设：与个人版共用晋级路线，但两队 + 队友附近复活。
     */
    @Test
    void teamArmsRacePresetUsesTeammateRespawn() {
        MatchSettings s = MatchSettings.builder("team_arms_race", "军备竞赛 团队")
                .sideCount(2).teamSize(4)
                .scoringMode(ScoringMode.ARMS_RACE)
                .roundFormat(RoundFormat.firstTo(ArmsRaceLevel.defaultProgression().size()))
                .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE)
                .armsRaceLevels(ArmsRaceLevel.defaultProgression())
                .build();

        assertEquals(2, s.sideCount());
        assertEquals(4, s.teamSize());
        assertEquals(8, s.totalPlayers());
        assertEquals(ScoringMode.ARMS_RACE, s.scoringMode());
        assertEquals(RespawnPolicy.NEAR_TEAMMATE, s.respawnPolicy());
        assertTrue(s.flexible());
    }

    // ---------------- flexible() 计分方式判定 ----------------

    @Test
    void flexibleTrueOnlyForKillCountHotZoneAndArmsRace() {
        assertFalse(MatchSettings.duel1v1().flexible());
        assertTrue(MatchSettings.teamDeathmatch(4, 30).flexible());
        assertTrue(MatchSettings.freeForAll(8, 20).flexible());

        MatchSettings hotZone = MatchSettings.builder("hot_zone", "热区")
                .scoringMode(ScoringMode.HOT_ZONE).roundFormat(RoundFormat.firstTo(150)).build();
        assertTrue(hotZone.flexible());

        MatchSettings armsRace = MatchSettings.builder("arms_race", "军备竞赛")
                .scoringMode(ScoringMode.ARMS_RACE).roundFormat(RoundFormat.firstTo(8)).build();
        assertTrue(armsRace.flexible());
    }

    // ---------------- Builder 校验与钳制边界 ----------------

    @Test
    void builderRejectsInvalidSideCountAndTeamSize() {
        assertThrows(IllegalArgumentException.class,
                () -> MatchSettings.builder("x", "X").sideCount(1).build());
        assertThrows(IllegalArgumentException.class,
                () -> MatchSettings.builder("x", "X").teamSize(0).build());
    }

    @Test
    void displayNameFallsBackToModeIdWhenNull() {
        MatchSettings s = MatchSettings.builder("custom_mode", null).build();
        assertEquals("custom_mode", s.displayName());
    }

    @Test
    void healthOverrideZeroMeansUseGlobalButPositiveValuesAreClamped() {
        MatchSettings zero = MatchSettings.builder("x", "X").healthOverride(0).build();
        assertEquals(0.0D, zero.healthOverride());

        MatchSettings tooLow = MatchSettings.builder("x", "X").healthOverride(0.2D).build();
        assertEquals(1.0D, tooLow.healthOverride());

        MatchSettings tooHigh = MatchSettings.builder("x", "X").healthOverride(999D).build();
        assertEquals(200.0D, tooHigh.healthOverride());
    }

    @Test
    void hotZoneParametersAreClampedToDocumentedRange() {
        MatchSettings belowRange = MatchSettings.builder("hot_zone", "热区")
                .hotZoneRotateSeconds(1)
                .hotZoneRadius(999)
                .hotZoneScorePerSecond(99)
                .build();

        assertEquals(15 * 20, belowRange.hotZoneRotateTicks());
        assertEquals(16.0D, belowRange.hotZoneRadius());
        assertEquals(5, belowRange.hotZoneScorePerSecond());

        MatchSettings aboveRange = MatchSettings.builder("hot_zone", "热区")
                .hotZoneRotateSeconds(999)
                .hotZoneRadius(0)
                .hotZoneScorePerSecond(0)
                .build();

        assertEquals(180 * 20, aboveRange.hotZoneRotateTicks());
        assertEquals(2.0D, aboveRange.hotZoneRadius());
        assertEquals(1, aboveRange.hotZoneScorePerSecond());
    }

    @Test
    void negativeTimersClampToZeroAndAmmoCratePercentClampsToPercentRange() {
        MatchSettings s = MatchSettings.builder("x", "X")
                .countdownSeconds(-5)
                .reEquipProtectionSeconds(-5)
                .timeLimitSeconds(-5)
                .ammoCrateChancePercent(-5)
                .build();
        assertEquals(0, s.countdownSeconds());
        assertEquals(0, s.reEquipProtectionSeconds());
        assertEquals(0, s.timeLimitSeconds());
        assertEquals(0, s.ammoCrateChance());

        MatchSettings percentAboveMax = MatchSettings.builder("x", "X").ammoCrateChancePercent(150).build();
        assertEquals(1.0D, percentAboveMax.ammoCrateChance());
    }

    @Test
    void emptyArmsRaceLevelsListFallsBackToDefaultProgression() {
        MatchSettings s = MatchSettings.builder("arms_race", "军备竞赛")
                .armsRaceLevels(List.of())
                .build();

        assertEquals(ArmsRaceLevel.defaultProgression().size(), s.armsRaceLevels().size());
        assertEquals(ArmsRaceLevel.defaultProgression(), s.armsRaceLevels());
    }

    @Test
    void rulesetDefaultsToArcadeButCanBeOverridden() {
        MatchSettings arcadeDefault = MatchSettings.builder("x", "X").build();
        assertEquals(LoadoutRuleset.ARCADE, arcadeDefault.ruleset());

        MatchSettings full = MatchSettings.builder("x", "X").ruleset(LoadoutRuleset.FULL).build();
        assertEquals(LoadoutRuleset.FULL, full.ruleset());
    }
}
