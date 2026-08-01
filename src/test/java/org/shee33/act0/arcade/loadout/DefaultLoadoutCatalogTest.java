package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultLoadoutCatalog} 单元测试：默认配装装配逻辑（挑选每个槽位的第一件默认武器）。
 */
class DefaultLoadoutCatalogTest {

    @Test
    void registerIsANoOpPlaceholder() {
        LoadoutRegistry reg = new LoadoutRegistry();
        DefaultLoadoutCatalog.register(reg);
        assertTrue(reg.all().isEmpty());
    }

    @Test
    void defaultLoadoutPicksFirstDefaultItemPerSlotForDefaultClass() {
        LoadoutRegistry reg = new LoadoutRegistry();
        reg.register(LoadoutItem.builder("m4", WeaponCategory.RIFLE).build()); // 非默认，先注册
        reg.register(LoadoutItem.builder("ak74", WeaponCategory.RIFLE).isDefault(true).build());
        reg.register(LoadoutItem.builder("glock", WeaponCategory.PISTOL).isDefault(true).build());

        Loadout loadout = DefaultLoadoutCatalog.defaultLoadout(reg);

        assertEquals(PlayerClassType.defaultClass(), loadout.classType());
        assertEquals("ak74", loadout.slotItemKey(LoadoutSlot.PRIMARY_WEAPON).orElse(null));
        assertEquals("glock", loadout.slotItemKey(LoadoutSlot.SECONDARY_WEAPON).orElse(null));
        assertTrue(loadout.slotItemKey(LoadoutSlot.MELEE).isEmpty());
    }

    @Test
    void defaultLoadoutSkipsDefaultItemsNotAvailableToDefaultClass() {
        LoadoutRegistry reg = new LoadoutRegistry();
        // 该默认近战武器仅支援兵可用，突击兵（默认职业）在筛选可用列表阶段就看不到它
        reg.register(LoadoutItem.builder("support_knife", WeaponCategory.MELEE)
                .isDefault(true).allowedClasses(PlayerClassType.SUPPORT).build());

        Loadout loadout = DefaultLoadoutCatalog.defaultLoadout(reg);

        assertTrue(loadout.slotItemKey(LoadoutSlot.MELEE).isEmpty());
    }
}
