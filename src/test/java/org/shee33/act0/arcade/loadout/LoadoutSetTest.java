package org.shee33.act0.arcade.loadout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Loadout} 与 {@link LoadoutSet} 单元测试：单套配装的槽位读写、五套配装切换/默认方案，
 * 以及"保存后重新加载"数据模型往返一致性（本模块无 NBT/JSON 编解码器，往返对象为
 * {@code storage.LoadoutSetCodec}，其依赖 Minecraft {@code CompoundTag}，不可在此单测；
 * 这里验证的是编解码器所依赖的 MC-free 数据结构本身在"提取字段 -> 重新构造"后保持一致）。
 */
class LoadoutSetTest {

    // ---------------- Loadout ----------------

    @Test
    void loadoutDefaultsToAssaultClassWhenNoneGiven() {
        Loadout loadout = new Loadout("我的配装");
        assertEquals(PlayerClassType.ASSAULT, loadout.classType());
        assertEquals("我的配装", loadout.name());
    }

    @Test
    void setSlotNullRemovesSelection() {
        Loadout loadout = new Loadout("T");
        loadout.setSlot(LoadoutSlot.PRIMARY_WEAPON, "ak74");
        assertTrue(loadout.slotItemKey(LoadoutSlot.PRIMARY_WEAPON).isPresent());

        loadout.setSlot(LoadoutSlot.PRIMARY_WEAPON, null);
        assertTrue(loadout.slotItemKey(LoadoutSlot.PRIMARY_WEAPON).isEmpty());
    }

    @Test
    void slotItemKeysSnapshotIsDefensiveCopy() {
        Loadout loadout = new Loadout("T");
        loadout.setSlot(LoadoutSlot.MELEE, "knife");

        var snapshot = loadout.slotItemKeys();
        snapshot.put(LoadoutSlot.PRIMARY_WEAPON, "injected");

        assertTrue(loadout.slotItemKey(LoadoutSlot.PRIMARY_WEAPON).isEmpty());
        assertEquals(1, loadout.slotItemKeys().size());
    }

    @Test
    void setNameAndClassTypeIgnoreNullArguments() {
        Loadout loadout = new Loadout("原名", PlayerClassType.RECON);

        loadout.setName(null);
        loadout.setClassType(null);

        assertEquals("原名", loadout.name());
        assertEquals(PlayerClassType.RECON, loadout.classType());
    }

    @Test
    void loadoutSaveThenLoadRoundTripPreservesNameClassAndSlots() {
        Loadout original = new Loadout("突击手配装", PlayerClassType.ASSAULT);
        original.setSlot(LoadoutSlot.PRIMARY_WEAPON, "ak74");
        original.setSlot(LoadoutSlot.SECONDARY_WEAPON, "glock");
        original.setSlot(LoadoutSlot.MELEE, "knife");

        // 模拟"保存"：抽取三项可持久化字段
        String savedName = original.name();
        PlayerClassType savedClass = original.classType();
        var savedSlots = original.slotItemKeys();

        // 模拟"加载"：用捕获的数据重建一份新配装
        Loadout restored = new Loadout(savedName, savedClass);
        savedSlots.forEach(restored::setSlot);

        assertEquals(original.name(), restored.name());
        assertEquals(original.classType(), restored.classType());
        assertEquals(original.slotItemKeys(), restored.slotItemKeys());
    }

    // ---------------- LoadoutSet：默认构造与空集边界 ----------------

    @Test
    void allFiveSlotsDefaultToEmptyNamedLoadoutsWhenNoneProvided() {
        LoadoutSet set = new LoadoutSet(null, 0);

        assertEquals(LoadoutSet.MAX_SLOTS, set.all().size());
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            Loadout l = set.get(i);
            assertEquals("配装 " + (i + 1), l.name());
            assertEquals(PlayerClassType.ASSAULT, l.classType());
            assertTrue(l.slotItemKeys().isEmpty());
        }
        assertEquals(0, set.activeIndex());
        assertEquals(0, set.defaultIndex());
    }

    @Test
    void constructorTruncatesListsLongerThanFiveSlots() {
        List<Loadout> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            seven.add(new Loadout("方案" + i));
        }

        LoadoutSet set = new LoadoutSet(seven, 0);

        assertEquals(LoadoutSet.MAX_SLOTS, set.all().size());
        assertEquals("方案4", set.get(4).name());
    }

    // ---------------- LoadoutSet：切换与默认方案 ----------------

    @Test
    void activeIndexSwitchesWhichLoadoutIsActive() {
        Loadout alpha = new Loadout("Alpha");
        Loadout bravo = new Loadout("Bravo");
        LoadoutSet set = new LoadoutSet(new ArrayList<>(List.of(alpha, bravo)), 0);

        assertSame(alpha, set.active());

        set.setActiveIndex(1);
        assertSame(bravo, set.active());

        set.setActiveIndex(0);
        assertSame(alpha, set.active());
    }

    @Test
    void activeAndDefaultIndexClampToValidBounds() {
        LoadoutSet set = new LoadoutSet(null, 0);

        set.setActiveIndex(-1);
        assertEquals(0, set.activeIndex());

        set.setActiveIndex(100);
        assertEquals(LoadoutSet.MAX_SLOTS - 1, set.activeIndex());

        set.setDefaultIndex(-99);
        assertEquals(0, set.defaultIndex());

        set.setDefaultIndex(100);
        assertEquals(LoadoutSet.MAX_SLOTS - 1, set.defaultIndex());
    }

    @Test
    void getClampsOutOfRangeIndexInsteadOfThrowing() {
        LoadoutSet set = new LoadoutSet(null, 0);

        assertSame(set.get(0), set.get(-5));
        assertSame(set.get(LoadoutSet.MAX_SLOTS - 1), set.get(999));
    }

    @Test
    void setReplacesSlotAndNullResetsToFreshEmptyLoadout() {
        LoadoutSet set = new LoadoutSet(null, 0);
        Loadout custom = new Loadout("狙击手配装", PlayerClassType.RECON);

        set.set(2, custom);
        assertSame(custom, set.get(2));

        set.set(2, null);
        Loadout resetSlot = set.get(2);
        assertNotSame(custom, resetSlot);
        assertEquals("配装 3", resetSlot.name());
        assertTrue(resetSlot.slotItemKeys().isEmpty());
    }

    @Test
    void defaultIndexIsIndependentFromActiveIndex() {
        LoadoutSet set = new LoadoutSet(null, 2, 4);

        assertEquals(2, set.activeIndex());
        assertEquals(4, set.defaultIndex());

        set.setActiveIndex(0);
        assertEquals(4, set.defaultIndex());
    }

    @Test
    void singleFactoryPopulatesOnlyFirstSlotLeavingRestEmpty() {
        Loadout provided = new Loadout("唯一配装", PlayerClassType.ENGINEER);
        LoadoutSet set = LoadoutSet.single(provided);

        assertSame(provided, set.get(0));
        assertEquals(0, set.activeIndex());
        for (int i = 1; i < LoadoutSet.MAX_SLOTS; i++) {
            assertEquals("配装 " + (i + 1), set.get(i).name());
        }
    }

    @Test
    void singleFactoryWithNullUsesEmptyDefaultInsteadOfNpe() {
        LoadoutSet set = LoadoutSet.single(null);
        assertEquals("配装 1", set.get(0).name());
    }

    @Test
    void allReturnsImmutableSnapshotOfCurrentLoadouts() {
        LoadoutSet set = new LoadoutSet(null, 0);
        List<Loadout> snapshot = set.all();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new Loadout("x")));
    }

    // ---------------- LoadoutSet：保存后重新加载往返一致 ----------------

    @Test
    void loadoutSetSaveThenLoadRoundTripPreservesAllFiveSlotsAndIndices() {
        Loadout primary = new Loadout("主力配装", PlayerClassType.ASSAULT);
        primary.setSlot(LoadoutSlot.PRIMARY_WEAPON, "ak74");
        primary.setSlot(LoadoutSlot.MELEE, "knife");
        Loadout scout = new Loadout("侦察配装", PlayerClassType.RECON);
        scout.setSlot(LoadoutSlot.PRIMARY_WEAPON, "sniper");

        LoadoutSet original = new LoadoutSet(new ArrayList<>(List.of(primary, scout)), 1, 0);

        // 模拟"保存"：捕获全部持久化字段（等价于 LoadoutSetCodec 编码前的输入）
        List<Loadout> savedLoadouts = original.all();
        int savedActive = original.activeIndex();
        int savedDefault = original.defaultIndex();

        // 模拟"加载"：用捕获的数据重建一个新的 LoadoutSet（等价于解码后的输出）
        LoadoutSet restored = new LoadoutSet(savedLoadouts, savedActive, savedDefault);

        assertEquals(savedActive, restored.activeIndex());
        assertEquals(savedDefault, restored.defaultIndex());
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            assertEquals(original.get(i).name(), restored.get(i).name());
            assertEquals(original.get(i).classType(), restored.get(i).classType());
            assertEquals(original.get(i).slotItemKeys(), restored.get(i).slotItemKeys());
        }
    }
}
