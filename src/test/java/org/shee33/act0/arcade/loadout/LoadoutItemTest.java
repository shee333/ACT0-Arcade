package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoadoutItem} 单元测试：构建器默认值、职业过滤、解锁判定与数值钳制。
 */
class LoadoutItemTest {

    @Test
    void noAllowedClassesMeansAvailableToEveryClass() {
        LoadoutItem item = LoadoutItem.builder("ak74", WeaponCategory.RIFLE).build();

        for (PlayerClassType classType : PlayerClassType.values()) {
            assertTrue(item.isAvailableFor(classType));
        }
        assertFalse(item.isAvailableFor(null));
    }

    @Test
    void explicitAllowedClassesRestrictAvailability() {
        LoadoutItem item = LoadoutItem.builder("support_pistol", WeaponCategory.PISTOL)
                .allowedClasses(PlayerClassType.SUPPORT, PlayerClassType.ENGINEER)
                .build();

        assertTrue(item.isAvailableFor(PlayerClassType.SUPPORT));
        assertTrue(item.isAvailableFor(PlayerClassType.ENGINEER));
        assertFalse(item.isAvailableFor(PlayerClassType.ASSAULT));
        assertFalse(item.isAvailableFor(PlayerClassType.RECON));
    }

    @Test
    void defaultItemIsAlwaysUnlockedRegardlessOfUnlockedKeys() {
        LoadoutItem defaultItem = LoadoutItem.builder("ak74", WeaponCategory.RIFLE).isDefault(true).build();

        assertTrue(defaultItem.isUnlockedBy(null));
        assertTrue(defaultItem.isUnlockedBy(Set.of()));
        assertTrue(defaultItem.isUnlockedBy(Set.of("something_else")));
    }

    @Test
    void nonDefaultItemRequiresKeyInUnlockedSet() {
        LoadoutItem paid = LoadoutItem.builder("sniper", WeaponCategory.SNIPER).price(1500).build();

        assertFalse(paid.isUnlockedBy(null));
        assertFalse(paid.isUnlockedBy(Set.of()));
        assertFalse(paid.isUnlockedBy(Set.of("ak74")));
        assertTrue(paid.isUnlockedBy(Set.of("sniper")));
    }

    @Test
    void priceAndInitialAmmoClampToNonNegative() {
        LoadoutItem item = LoadoutItem.builder("x", WeaponCategory.RIFLE)
                .price(-500)
                .initialAmmo(-10)
                .build();

        assertEquals(0, item.price());
        assertEquals(0, item.initialAmmo());
    }

    @Test
    void slotIsDerivedFromCategory() {
        assertEquals(LoadoutSlot.PRIMARY_WEAPON, LoadoutItem.builder("x", WeaponCategory.RIFLE).build().slot());
        assertEquals(LoadoutSlot.SECONDARY_WEAPON, LoadoutItem.builder("x", WeaponCategory.PISTOL).build().slot());
        assertEquals(LoadoutSlot.GADGET_1, LoadoutItem.builder("x", WeaponCategory.GADGET_1).build().slot());
    }

    @Test
    void displayNameFallsBackToKeyWhenNotSet() {
        LoadoutItem item = LoadoutItem.builder("raw_key", WeaponCategory.RIFLE).build();
        assertEquals("raw_key", item.displayName());

        LoadoutItem named = LoadoutItem.builder("raw_key", WeaponCategory.RIFLE).displayName("AK-74").build();
        assertEquals("AK-74", named.displayName());
    }

    @Test
    void createItemWithoutFactoryReturnsNull() {
        LoadoutItem item = LoadoutItem.builder("x", WeaponCategory.RIFLE).build();
        assertNull(item.createItem());
    }

    @Test
    void itemFactoryIsInvokedWhenCreateItemCalled() {
        Object marker = new Object();
        LoadoutItem item = LoadoutItem.builder("x", WeaponCategory.RIFLE)
                .itemFactory(() -> marker)
                .build();

        assertEquals(marker, item.createItem());
    }
}
