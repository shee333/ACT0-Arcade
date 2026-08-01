package org.shee33.act0.arcade.mode;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RandomWeaponMode} 单元测试：{@link RandomWeaponMode#byName(String)} 别名解析与
 * {@link RandomWeaponMode#enabled()} 语义。
 */
class RandomWeaponModeTest {

    @Test
    void byNameResolvesBooleanLikeAliasesToAllOrOff() {
        assertEquals(RandomWeaponMode.ALL, RandomWeaponMode.byName("true"));
        assertEquals(RandomWeaponMode.ALL, RandomWeaponMode.byName("on"));
        assertEquals(RandomWeaponMode.ALL, RandomWeaponMode.byName("yes"));
        assertEquals(RandomWeaponMode.ALL, RandomWeaponMode.byName("TRUE"));

        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName("false"));
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName("off"));
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName("no"));
    }

    @Test
    void byNameResolvesModeIdCaseInsensitively() {
        assertEquals(RandomWeaponMode.SNIPER, RandomWeaponMode.byName("sniper"));
        assertEquals(RandomWeaponMode.SNIPER, RandomWeaponMode.byName("SNIPER"));
        assertEquals(RandomWeaponMode.RIFLE, RandomWeaponMode.byName("rifle"));
        assertEquals(RandomWeaponMode.PISTOL_ONLY, RandomWeaponMode.byName("pistol"));
    }

    @Test
    void byNameResolvesEnumConstantNameAndDisplayName() {
        assertEquals(RandomWeaponMode.SMG, RandomWeaponMode.byName("SMG"));
        assertEquals(RandomWeaponMode.SNIPER, RandomWeaponMode.byName("随机狙击枪"));
    }

    @Test
    void byNameUnknownOrBlankFallsBackToOff() {
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName(null));
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName(""));
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName("   "));
        assertEquals(RandomWeaponMode.OFF, RandomWeaponMode.byName("not_a_real_mode"));
    }

    @Test
    void enabledIsFalseOnlyForOff() {
        assertFalse(RandomWeaponMode.OFF.enabled());
        for (RandomWeaponMode mode : RandomWeaponMode.values()) {
            if (mode != RandomWeaponMode.OFF) {
                assertTrue(mode.enabled());
            }
        }
    }

    @Test
    void primaryCategoryAndPistolOnlyFlagsMatchDeclaredMapping() {
        assertNull(RandomWeaponMode.ALL.primaryCategory());
        assertNull(RandomWeaponMode.PISTOL_ONLY.primaryCategory());
        assertTrue(RandomWeaponMode.PISTOL_ONLY.pistolOnly());

        assertEquals(WeaponCategory.RIFLE, RandomWeaponMode.RIFLE.primaryCategory());
        assertEquals(WeaponCategory.SNIPER, RandomWeaponMode.SNIPER.primaryCategory());
        assertFalse(RandomWeaponMode.RIFLE.pistolOnly());
    }
}
