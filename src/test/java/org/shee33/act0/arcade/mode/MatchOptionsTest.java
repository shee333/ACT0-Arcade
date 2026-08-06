package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MatchOptions} 单元测试：房主可配置项的归一化/钳制（紧凑构造器）与三个便捷构造重载。
 */
class MatchOptionsTest {

    @Test
    void defaultsFactoryUsesSafeBaseline() {
        MatchOptions o = MatchOptions.defaults();

        assertNull(o.winTarget());
        assertEquals(0, o.timeLimitSeconds());
        assertEquals(RandomWeaponMode.OFF, o.randomMode());
        assertFalse(o.randomWeapons());
        assertEquals(0.0D, o.healthOverride());
        assertFalse(o.friendlyFire());
        assertEquals(25, o.ammoCrateChancePercent());
        assertEquals(1, o.hotZoneScorePerSecond());
        assertNull(o.armsRaceLevels());
    }

    @Test
    void timeLimitSecondsClampedToZeroToOneHour() {
        assertEquals(0, new MatchOptions(null, -100, false).timeLimitSeconds());
        assertEquals(3600, new MatchOptions(null, 999_999, false).timeLimitSeconds());
        assertEquals(120, new MatchOptions(null, 120, false).timeLimitSeconds());
    }

    @Test
    void healthOverrideNonPositiveMeansUseGlobalOtherwiseClampedToOneToTwoHundred() {
        MatchOptions zero = fullOptions(0.0D, 3, false, 25, 1, null);
        assertEquals(0.0D, zero.healthOverride());

        MatchOptions negative = fullOptions(-50.0D, 3, false, 25, 1, null);
        assertEquals(0.0D, negative.healthOverride());

        MatchOptions tooLow = fullOptions(0.3D, 3, false, 25, 1, null);
        assertEquals(1.0D, tooLow.healthOverride());

        MatchOptions tooHigh = fullOptions(500.0D, 3, false, 25, 1, null);
        assertEquals(200.0D, tooHigh.healthOverride());
    }

    @Test
    void respawnDelaySecondsClampedToZeroToTwenty() {
        assertEquals(0, fullOptions(0, -5, false, 25, 1, null).respawnDelaySeconds());
        assertEquals(20, fullOptions(0, 999, false, 25, 1, null).respawnDelaySeconds());
        assertEquals(7, fullOptions(0, 7, false, 25, 1, null).respawnDelaySeconds());
    }

    @Test
    void ammoCrateChancePercentClampedToZeroToHundred() {
        assertEquals(0, fullOptions(0, 3, false, -20, 1, null).ammoCrateChancePercent());
        assertEquals(100, fullOptions(0, 3, false, 250, 1, null).ammoCrateChancePercent());
    }

    @Test
    void hotZoneScorePerSecondClampedToOneToFive() {
        assertEquals(1, fullOptions(0, 3, false, 25, -3, null).hotZoneScorePerSecond());
        assertEquals(5, fullOptions(0, 3, false, 25, 42, null).hotZoneScorePerSecond());
    }

    @Test
    void nullRandomModeNormalizedToOff() {
        MatchOptions o = new MatchOptions(null, 30, null,
                0.0D, 3, false, 25, 1, null);
        assertEquals(RandomWeaponMode.OFF, o.randomMode());
        assertFalse(o.randomWeapons());
    }

    @Test
    void emptyArmsRaceLevelsNormalizedToNullNotEmptyList() {
        MatchOptions withEmptyList = fullOptions(0, 3, false, 25, 1, List.of());
        assertNull(withEmptyList.armsRaceLevels());

        List<ArmsRaceLevel> provided = List.of(new ArmsRaceLevel(WeaponCategory.RIFLE, 3));
        MatchOptions withLevels = fullOptions(0, 3, false, 25, 1, provided);
        assertEquals(provided, withLevels.armsRaceLevels());
    }

    @Test
    void threeArgWinTargetConstructorDelegatesToRandomModeConstructor() {
        MatchOptions o = new MatchOptions(5, 120, true);
        assertEquals(5, o.winTarget());
        assertEquals(120, o.timeLimitSeconds());
        assertTrue(o.randomWeapons());
        assertEquals(RandomWeaponMode.ALL, o.randomMode());

        MatchOptions off = new MatchOptions(null, 0, false);
        assertFalse(off.randomWeapons());
        assertEquals(RandomWeaponMode.OFF, off.randomMode());
    }

    @Test
    void twoArgRandomModeConstructorFillsRemainingFieldsWithDefaults() {
        MatchOptions o = new MatchOptions(null, 30, RandomWeaponMode.SNIPER);

        assertEquals(RandomWeaponMode.SNIPER, o.randomMode());
        assertEquals(3, o.respawnDelaySeconds());
        assertFalse(o.friendlyFire());
        assertEquals(25, o.ammoCrateChancePercent());
        assertEquals(1, o.hotZoneScorePerSecond());
        assertEquals(0.0D, o.healthOverride());
        assertNull(o.armsRaceLevels());
    }

    private static MatchOptions fullOptions(double healthOverride,
                                            int respawnDelaySeconds,
                                            boolean friendlyFire,
                                            int ammoCrateChancePercent,
                                            int hotZoneScorePerSecond,
                                            List<ArmsRaceLevel> armsRaceLevels) {
        return new MatchOptions(null, 0, RandomWeaponMode.OFF,
                healthOverride, respawnDelaySeconds, friendlyFire, ammoCrateChancePercent,
                hotZoneScorePerSecond, armsRaceLevels);
    }
}
