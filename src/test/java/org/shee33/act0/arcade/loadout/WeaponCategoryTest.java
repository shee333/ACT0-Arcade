package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WeaponCategory} 单元测试：分类到槽位的映射、{@link WeaponCategory#forSlot(LoadoutSlot)}
 * 分组，以及 {@link WeaponCategory#byName(String)} 解析。
 */
class WeaponCategoryTest {

    @Test
    void forSlotGroupsAllPrimaryWeaponCategoriesInDeclarationOrder() {
        assertEquals(
                List.of(WeaponCategory.RIFLE, WeaponCategory.SMG, WeaponCategory.SNIPER,
                        WeaponCategory.SHOTGUN, WeaponCategory.LMG, WeaponCategory.MARKSMAN),
                WeaponCategory.forSlot(LoadoutSlot.PRIMARY_WEAPON));
    }

    @Test
    void forSlotSecondaryWeaponContainsOnlyPistol() {
        assertEquals(List.of(WeaponCategory.PISTOL), WeaponCategory.forSlot(LoadoutSlot.SECONDARY_WEAPON));
    }

    @Test
    void forSlotNullReturnsEmptyList() {
        assertTrue(WeaponCategory.forSlot(null).isEmpty());
    }

    @Test
    void eachGadgetSlotMapsToExactlyOneCategory() {
        assertEquals(List.of(WeaponCategory.GADGET_1), WeaponCategory.forSlot(LoadoutSlot.GADGET_1));
        assertEquals(List.of(WeaponCategory.GADGET_2), WeaponCategory.forSlot(LoadoutSlot.GADGET_2));
    }

    @Test
    void byNameResolvesCaseInsensitively() {
        assertEquals(WeaponCategory.RIFLE, WeaponCategory.byName("rifle"));
        assertEquals(WeaponCategory.RIFLE, WeaponCategory.byName("RIFLE"));
        assertEquals(WeaponCategory.SNIPER, WeaponCategory.byName("Sniper"));
    }

    @Test
    void byNameUnknownOrNullReturnsNull() {
        assertNull(WeaponCategory.byName("not_a_category"));
        assertNull(WeaponCategory.byName(null));
    }

    @Test
    void everyCategorySlotAssignmentMatchesDocumentedDesign() {
        assertEquals(LoadoutSlot.MELEE, WeaponCategory.MELEE.slot());
        assertEquals(LoadoutSlot.THROWABLE, WeaponCategory.THROWABLE.slot());
        assertEquals(LoadoutSlot.GADGET_1, WeaponCategory.GADGET_1.slot());
        assertEquals(LoadoutSlot.GADGET_2, WeaponCategory.GADGET_2.slot());
    }
}
