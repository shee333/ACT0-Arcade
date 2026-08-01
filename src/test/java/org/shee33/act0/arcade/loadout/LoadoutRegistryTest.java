package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoadoutRegistry} 单元测试：按 key/槽位/分类查找、按职业过滤可用装备、重复注册报错、
 * 以及 {@link LoadoutRegistry#all()} 的不可变视图。
 */
class LoadoutRegistryTest {

    private LoadoutItem rifle(String key, boolean isDefault) {
        return LoadoutItem.builder(key, WeaponCategory.RIFLE).isDefault(isDefault).build();
    }

    @Test
    void registerDuplicateKeyThrows() {
        LoadoutRegistry reg = new LoadoutRegistry();
        reg.register(rifle("ak74", true));

        assertThrows(IllegalStateException.class, () -> reg.register(rifle("ak74", false)));
    }

    @Test
    void findReturnsEmptyForUnknownOrNullKey() {
        LoadoutRegistry reg = new LoadoutRegistry();
        reg.register(rifle("ak74", true));

        assertTrue(reg.find("does_not_exist").isEmpty());
        assertTrue(reg.find(null).isEmpty());
        assertTrue(reg.find("ak74").isPresent());
    }

    @Test
    void itemsForSlotAndCategoryFilterAndPreserveRegistrationOrder() {
        LoadoutRegistry reg = new LoadoutRegistry();
        LoadoutItem ak = rifle("ak74", true);
        LoadoutItem m4 = rifle("m4", false);
        LoadoutItem glock = LoadoutItem.builder("glock", WeaponCategory.PISTOL).build();
        reg.register(ak);
        reg.register(m4);
        reg.register(glock);

        assertEquals(List.of(ak, m4), reg.itemsForSlot(LoadoutSlot.PRIMARY_WEAPON));
        assertEquals(List.of(glock), reg.itemsForSlot(LoadoutSlot.SECONDARY_WEAPON));
        assertEquals(List.of(ak, m4), reg.itemsForCategory(WeaponCategory.RIFLE));
        assertTrue(reg.itemsForSlot(null).isEmpty());
        assertTrue(reg.itemsForCategory(null).isEmpty());
    }

    @Test
    void availableItemsFiltersByPlayerClass() {
        LoadoutRegistry reg = new LoadoutRegistry();
        LoadoutItem forEveryone = rifle("ak74", true);
        LoadoutItem supportOnly = LoadoutItem.builder("support_rifle", WeaponCategory.RIFLE)
                .allowedClasses(PlayerClassType.SUPPORT).build();
        reg.register(forEveryone);
        reg.register(supportOnly);

        List<LoadoutItem> forAssault = reg.availableItems(LoadoutSlot.PRIMARY_WEAPON, PlayerClassType.ASSAULT);
        List<LoadoutItem> forSupport = reg.availableItems(LoadoutSlot.PRIMARY_WEAPON, PlayerClassType.SUPPORT);

        assertEquals(List.of(forEveryone), forAssault);
        assertEquals(List.of(forEveryone, supportOnly), forSupport);
    }

    @Test
    void availableItemsInCategoryMirrorsAvailableItemsButScopedToCategory() {
        LoadoutRegistry reg = new LoadoutRegistry();
        LoadoutItem sniperForRecon = LoadoutItem.builder("awp", WeaponCategory.SNIPER)
                .allowedClasses(PlayerClassType.RECON).build();
        reg.register(sniperForRecon);

        assertTrue(reg.availableItemsInCategory(WeaponCategory.SNIPER, PlayerClassType.ASSAULT).isEmpty());
        assertEquals(List.of(sniperForRecon),
                reg.availableItemsInCategory(WeaponCategory.SNIPER, PlayerClassType.RECON));
    }

    @Test
    void allReturnsUnmodifiableViewAndClearEmptiesRegistry() {
        LoadoutRegistry reg = new LoadoutRegistry();
        reg.register(rifle("ak74", true));

        assertThrows(UnsupportedOperationException.class,
                () -> reg.all().put("m4", rifle("m4", false)));

        reg.clear();
        assertTrue(reg.all().isEmpty());
        assertTrue(reg.find("ak74").isEmpty());
    }
}
