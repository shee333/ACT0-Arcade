package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ArmsRaceLevel} 单元测试：晋级击杀数钳制、序列化/反序列化往返，以及默认晋级路线。
 */
class ArmsRaceLevelTest {

    @Test
    void killsToAdvanceClampedToOneToTen() {
        assertEquals(1, new ArmsRaceLevel(WeaponCategory.RIFLE, 0).killsToAdvance());
        assertEquals(1, new ArmsRaceLevel(WeaponCategory.RIFLE, -5).killsToAdvance());
        assertEquals(10, new ArmsRaceLevel(WeaponCategory.RIFLE, 50).killsToAdvance());
        assertEquals(5, new ArmsRaceLevel(WeaponCategory.RIFLE, 5).killsToAdvance());
    }

    @Test
    void nullCategoryRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ArmsRaceLevel(null, 2));
    }

    @Test
    void defaultProgressionHasEightLevelsStartingAtPistolEndingAtMelee() {
        List<ArmsRaceLevel> levels = ArmsRaceLevel.defaultProgression();

        assertEquals(8, levels.size());
        assertEquals(WeaponCategory.PISTOL, levels.get(0).category());
        assertEquals(WeaponCategory.MELEE, levels.get(levels.size() - 1).category());
        assertEquals(1, levels.get(levels.size() - 1).killsToAdvance());
    }

    @Test
    void displayNameCombinesCategoryDisplayNameAndKillCount() {
        ArmsRaceLevel level = new ArmsRaceLevel(WeaponCategory.RIFLE, 2);
        assertEquals(WeaponCategory.RIFLE.displayName() + " \u00d72", level.displayName());
    }

    @Test
    void serializeThenDeserializeRoundTrips() {
        ArmsRaceLevel original = new ArmsRaceLevel(WeaponCategory.SNIPER, 3);

        String serialized = original.serialize();
        ArmsRaceLevel restored = ArmsRaceLevel.deserialize(serialized);

        assertEquals("SNIPER:3", serialized);
        assertEquals(original.category(), restored.category());
        assertEquals(original.killsToAdvance(), restored.killsToAdvance());
    }

    @Test
    void deserializeRejectsBlankMalformedOrUnknownCategory() {
        assertNull(ArmsRaceLevel.deserialize(null));
        assertNull(ArmsRaceLevel.deserialize(""));
        assertNull(ArmsRaceLevel.deserialize("no_colon_here"));
        assertNull(ArmsRaceLevel.deserialize("NOT_A_WEAPON:2"));
    }

    @Test
    void serializeListThenDeserializeListRoundTrips() {
        List<ArmsRaceLevel> original = List.of(
                new ArmsRaceLevel(WeaponCategory.PISTOL, 2),
                new ArmsRaceLevel(WeaponCategory.RIFLE, 4));

        String serialized = ArmsRaceLevel.serializeList(original);
        List<ArmsRaceLevel> restored = ArmsRaceLevel.deserializeList(serialized);

        assertEquals("PISTOL:2,RIFLE:4", serialized);
        assertEquals(original, restored);
    }

    @Test
    void serializeListOfNullOrEmptyReturnsEmptyString() {
        assertEquals("", ArmsRaceLevel.serializeList(null));
        assertEquals("", ArmsRaceLevel.serializeList(List.of()));
    }

    @Test
    void deserializeListOfBlankFallsBackToDefaultProgression() {
        assertEquals(ArmsRaceLevel.defaultProgression(), ArmsRaceLevel.deserializeList(null));
        assertEquals(ArmsRaceLevel.defaultProgression(), ArmsRaceLevel.deserializeList(""));
        assertEquals(ArmsRaceLevel.defaultProgression(), ArmsRaceLevel.deserializeList("   "));
    }

    @Test
    void deserializeListSkipsInvalidEntriesButKeepsValidOnes() {
        List<ArmsRaceLevel> restored = ArmsRaceLevel.deserializeList("RIFLE:3,GARBAGE,SNIPER:2");

        assertEquals(2, restored.size());
        assertEquals(WeaponCategory.RIFLE, restored.get(0).category());
        assertEquals(3, restored.get(0).killsToAdvance());
        assertEquals(WeaponCategory.SNIPER, restored.get(1).category());
        assertEquals(2, restored.get(1).killsToAdvance());
    }

    @Test
    void deserializeListFallsBackToDefaultWhenAllEntriesInvalid() {
        List<ArmsRaceLevel> restored = ArmsRaceLevel.deserializeList("GARBAGE1,GARBAGE2");
        assertEquals(ArmsRaceLevel.defaultProgression(), restored);
    }
}
