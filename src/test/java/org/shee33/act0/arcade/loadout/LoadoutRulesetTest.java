package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoadoutRuleset} 与 {@link LoadoutSlot} 单元测试：街机规则集只放行核心战斗槽，
 * 与 {@link LoadoutSlot#isCoreCombatSlot()} 的标记必须完全一致。
 */
class LoadoutRulesetTest {

    @Test
    void fullRulesetAllowsEveryDeclaredSlot() {
        LoadoutRuleset full = LoadoutRuleset.FULL;
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            assertTrue(full.allows(slot), slot + " should be allowed under FULL");
        }
        assertEquals(LoadoutSlot.values().length, full.allowedSlots().size());
    }

    @Test
    void arcadeRulesetAllowsOnlyCoreCombatSlots() {
        LoadoutRuleset arcade = LoadoutRuleset.ARCADE;
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            assertEquals(slot.isCoreCombatSlot(), arcade.allows(slot),
                    slot + " allowance should match isCoreCombatSlot()");
        }
    }

    @Test
    void arcadeRulesetExplicitlyBlocksBothGadgetSlots() {
        assertFalse(LoadoutRuleset.ARCADE.allows(LoadoutSlot.GADGET_1));
        assertFalse(LoadoutRuleset.ARCADE.allows(LoadoutSlot.GADGET_2));
    }

    @Test
    void arcadeRulesetExplicitlyAllowsAllCoreCombatSlots() {
        assertTrue(LoadoutRuleset.ARCADE.allows(LoadoutSlot.PRIMARY_WEAPON));
        assertTrue(LoadoutRuleset.ARCADE.allows(LoadoutSlot.SECONDARY_WEAPON));
        assertTrue(LoadoutRuleset.ARCADE.allows(LoadoutSlot.MELEE));
        assertTrue(LoadoutRuleset.ARCADE.allows(LoadoutSlot.THROWABLE));
    }

    @Test
    void allowsIsFalseForNullSlot() {
        assertFalse(LoadoutRuleset.FULL.allows(null));
        assertFalse(LoadoutRuleset.ARCADE.allows(null));
    }

    @Test
    void allowedSlotsSnapshotIsIndependentPerCall() {
        var first = LoadoutRuleset.ARCADE.allowedSlots();
        first.add(LoadoutSlot.GADGET_1);

        var second = LoadoutRuleset.ARCADE.allowedSlots();
        assertFalse(second.contains(LoadoutSlot.GADGET_1));
    }

    @Test
    void hotbarIndexAndCoreCombatFlagsMatchSlotDeclarationOrder() {
        assertEquals(0, LoadoutSlot.PRIMARY_WEAPON.hotbarIndex());
        assertEquals(1, LoadoutSlot.SECONDARY_WEAPON.hotbarIndex());
        assertEquals(2, LoadoutSlot.MELEE.hotbarIndex());
        assertEquals(3, LoadoutSlot.GADGET_1.hotbarIndex());
        assertEquals(4, LoadoutSlot.GADGET_2.hotbarIndex());
        assertEquals(5, LoadoutSlot.THROWABLE.hotbarIndex());

        assertFalse(LoadoutSlot.GADGET_1.isCoreCombatSlot());
        assertFalse(LoadoutSlot.GADGET_2.isCoreCombatSlot());
        assertTrue(LoadoutSlot.PRIMARY_WEAPON.isCoreCombatSlot());
        assertTrue(LoadoutSlot.THROWABLE.isCoreCombatSlot());
    }
}
