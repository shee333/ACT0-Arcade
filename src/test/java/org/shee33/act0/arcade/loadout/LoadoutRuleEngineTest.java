package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
