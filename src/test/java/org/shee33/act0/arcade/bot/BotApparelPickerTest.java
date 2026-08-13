package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotApparelPickerTest {

    private static ApparelRegistry registryWith(ApparelSlot slot, int count) {
        ApparelRegistry registry = new ApparelRegistry();
        for (int i = 0; i < count; i++) {
            registry.register(new ApparelItem(slot.name() + "_" + i, "件" + i, slot, 0, false, "{}"));
        }
        return registry;
    }

    private static ApparelRegistry fullRegistry(int perSlot) {
        ApparelRegistry registry = new ApparelRegistry();
        for (ApparelSlot slot : ApparelSlot.values()) {
            for (int i = 0; i < perSlot; i++) {
                registry.register(new ApparelItem(slot.name() + "_" + i, "件" + i, slot, 0, false, "{}"));
            }
        }
        return registry;
    }

    @Test
    void picksOnePiecePerSlot() {
        ApparelSelection selection = BotApparelPicker.pickRandom(fullRegistry(3), new Random(1L));
        for (ApparelSlot slot : ApparelSlot.values()) {
            assertTrue(selection.selectedKey(slot).isPresent(), slot + " 应被选中一件");
        }
        assertEquals(ApparelSlot.values().length, selection.all().size());
    }

    @Test
    void pickedKeyBelongsToItsOwnSlot() {
        ApparelSelection selection = BotApparelPicker.pickRandom(fullRegistry(4), new Random(7L));
        for (ApparelSlot slot : ApparelSlot.values()) {
            String key = selection.selectedKey(slot).orElseThrow();
            assertTrue(key.startsWith(slot.name()), slot + " 抽到了别的槽位的件：" + key);
        }
    }

    /**
     * 目录为空、或只录了部分槽位，都是正常运营状态（护甲全靠管理员逐件录入，没有种子模板）。
     * 此时必须静默跳过缺货槽位，而不是抛异常或整体不穿。
     */
    @Test
    void emptyCatalogYieldsEmptySelection() {
        ApparelSelection selection = BotApparelPicker.pickRandom(new ApparelRegistry(), new Random(1L));
        assertTrue(selection.all().isEmpty());
        assertFalse(BotApparelPicker.hasAnyApparel(new ApparelRegistry()));
    }

    @Test
    void partiallyStockedCatalogFillsOnlyStockedSlots() {
        ApparelRegistry registry = registryWith(ApparelSlot.HELMET, 2);
        ApparelSelection selection = BotApparelPicker.pickRandom(registry, new Random(1L));
        assertTrue(selection.selectedKey(ApparelSlot.HELMET).isPresent());
        assertTrue(selection.selectedKey(ApparelSlot.CHESTPLATE).isEmpty());
        assertEquals(1, selection.all().size());
        assertTrue(BotApparelPicker.hasAnyApparel(registry));
    }

    @Test
    void nullArgumentsYieldEmptySelection() {
        assertTrue(BotApparelPicker.pickRandom(null, new Random(1L)).all().isEmpty());
        assertTrue(BotApparelPicker.pickRandom(fullRegistry(2), null).all().isEmpty());
        assertFalse(BotApparelPicker.hasAnyApparel(null));
    }

    @Test
    void sameSeedYieldsSameOutfit() {
        ApparelRegistry registry = fullRegistry(5);
        ApparelSelection a = BotApparelPicker.pickRandom(registry, new Random(42L));
        ApparelSelection b = BotApparelPicker.pickRandom(registry, new Random(42L));
        assertEquals(a.all(), b.all(), "同一 bot 的造型必须跨调用稳定，否则每次复活换一身");
    }

    /**
     * 逐槽独立抽取的观感目标是"杂牌军"：不同种子应当能产出不同搭配，否则所有 bot 会长得一样。
     */
    @Test
    void differentSeedsProduceVariedOutfits() {
        ApparelRegistry registry = fullRegistry(4);
        Set<String> distinct = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            distinct.add(BotApparelPicker.pickRandom(registry, new Random(seed)).all().toString());
        }
        assertTrue(distinct.size() > 5, "40 个种子应产出多种搭配，实际 " + distinct.size());
    }
}
