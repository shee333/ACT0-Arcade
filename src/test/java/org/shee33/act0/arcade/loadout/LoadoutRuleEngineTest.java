package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoadoutRuleEngine} 单元测试。
 *
 * <p>验证配装解析的核心判定：默认武器免费可用、未购买武器被锁、职业过滤、空槽位，以及街机规则集。
 * 不依赖 Minecraft 运行时，可直接 {@code gradlew test} 运行。
 */
class LoadoutRuleEngineTest {

    private LoadoutRegistry buildRegistry() {
        LoadoutRegistry reg = new LoadoutRegistry();
        // 默认步枪：所有玩家免费可用
        reg.register(LoadoutItem.builder("ak74", WeaponCategory.RIFLE)
                .displayName("AK-74").isDefault(true).build());
        // 需购买的狙击枪
        reg.register(LoadoutItem.builder("sniper", WeaponCategory.SNIPER)
                .displayName("Sniper").price(1500).build());
        // 仅支援职业可用的手枪
        reg.register(LoadoutItem.builder("support_pistol", WeaponCategory.PISTOL)
                .displayName("Support Pistol").allowedClasses(PlayerClassType.SUPPORT).build());
        // 支援兵专属装置（占用 GADGET_1 槽位）：全职业可用武器免费，但槽位本身在街机下被禁用
        reg.register(LoadoutItem.builder("ammo_crate", WeaponCategory.GADGET_1)
                .displayName("Ammo Crate").isDefault(true).build());
        return reg;
    }

    @Test
    void defaultWeaponGrantedWithoutPurchase() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.PRIMARY_WEAPON, "ak74");

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertTrue(r.slot(LoadoutSlot.PRIMARY_WEAPON).isGranted());
    }

    @Test
    void arcadeRulesetStillGrantsCoreWeapon() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.PRIMARY_WEAPON, "ak74");

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.ARCADE, reg, Set.of());

        assertTrue(r.slot(LoadoutSlot.PRIMARY_WEAPON).isGranted());
    }

    @Test
    void classFilterRejectsUnavailableItem() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT); // 突击兵不能用支援手枪
        lo.setSlot(LoadoutSlot.SECONDARY_WEAPON, "support_pistol");

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertEquals(LoadoutResolution.Outcome.FILTERED_BY_CLASS,
                r.slot(LoadoutSlot.SECONDARY_WEAPON).outcome());
    }

    @Test
    void lockedUntilPurchasedThenGranted() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.PRIMARY_WEAPON, "sniper"); // 需购买

        LoadoutResolution locked = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());
        LoadoutResolution unlocked = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of("sniper"));

        assertEquals(LoadoutResolution.Outcome.LOCKED, locked.slot(LoadoutSlot.PRIMARY_WEAPON).outcome());
        assertTrue(unlocked.slot(LoadoutSlot.PRIMARY_WEAPON).isGranted());
    }

    @Test
    void emptySlotResolvesToEmpty() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT); // 未选任何东西

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertEquals(LoadoutResolution.Outcome.EMPTY, r.slot(LoadoutSlot.PRIMARY_WEAPON).outcome());
    }

    @Test
    void arcadeRulesetFiltersGadgetSlotEvenWhenItemWouldOtherwiseBeGranted() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.GADGET_1, "ammo_crate");

        LoadoutResolution arcade = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.ARCADE, reg, Set.of());
        LoadoutResolution full = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertEquals(LoadoutResolution.Outcome.FILTERED_BY_RULESET,
                arcade.slot(LoadoutSlot.GADGET_1).outcome());
        assertTrue(arcade.slot(LoadoutSlot.GADGET_1).item().isEmpty());
        assertTrue(full.slot(LoadoutSlot.GADGET_1).isGranted());
    }

    @Test
    void unknownItemKeyResolvesToUnknownItem() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.PRIMARY_WEAPON, "does_not_exist");

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertEquals(LoadoutResolution.Outcome.UNKNOWN_ITEM, r.slot(LoadoutSlot.PRIMARY_WEAPON).outcome());
        assertTrue(r.slot(LoadoutSlot.PRIMARY_WEAPON).item().isEmpty());
    }

    @Test
    void resolveCoversEveryLoadoutSlotEvenWhenLoadoutIsCompletelyEmpty() {
        LoadoutRegistry reg = buildRegistry();
        Loadout empty = new Loadout("空配装", PlayerClassType.ASSAULT);

        LoadoutResolution r = LoadoutRuleEngine.resolve(empty, LoadoutRuleset.ARCADE, reg, Set.of());

        assertEquals(LoadoutSlot.values().length, r.bySlot().size());
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            assertEquals(LoadoutResolution.Outcome.EMPTY, r.slot(slot).outcome());
        }
    }

    @Test
    void nonGrantedSlotResolutionsHaveNoItemPresent() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);
        lo.setSlot(LoadoutSlot.PRIMARY_WEAPON, "sniper"); // 未解锁 -> LOCKED

        LoadoutResolution r = LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, reg, Set.of());

        assertFalse(r.slot(LoadoutSlot.PRIMARY_WEAPON).isGranted());
        assertNull(r.slot(LoadoutSlot.PRIMARY_WEAPON).item().orElse(null));
    }

    @Test
    void resolveRejectsNullArguments() {
        LoadoutRegistry reg = buildRegistry();
        Loadout lo = new Loadout("T", PlayerClassType.ASSAULT);

        assertThrows(NullPointerException.class,
                () -> LoadoutRuleEngine.resolve(null, LoadoutRuleset.FULL, reg, Set.of()));
        assertThrows(NullPointerException.class,
                () -> LoadoutRuleEngine.resolve(lo, null, reg, Set.of()));
        assertThrows(NullPointerException.class,
                () -> LoadoutRuleEngine.resolve(lo, LoadoutRuleset.FULL, null, Set.of()));
    }
}
