package org.shee33.act0.arcade.loadout;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 配装规则引擎：纯逻辑，将一份 {@link Loadout} 在给定 {@link LoadoutRuleset} 下解析为
 * {@link LoadoutResolution}。
 *
 * <p>这是"街机无缝禁用兵种道具"的判定核心，<b>不依赖 Minecraft 运行时</b>，可直接单元测试。
 * 过滤顺序（逐槽位）：
 * <ol>
 *   <li>该槽位无选择 → {@code EMPTY}。</li>
 *   <li>规则集不允许该槽位 → {@code FILTERED_BY_RULESET}（街机模式下的 GADGET_1 / GADGET_2）。</li>
 *   <li>装备 key 未注册 → {@code UNKNOWN_ITEM}。</li>
 *   <li>装备不对当前职业开放 → {@code FILTERED_BY_CLASS}。</li>
 *   <li>玩家未购买/未解锁该武器 → {@code LOCKED}。</li>
 *   <li>全部通过 → {@code GRANTED}。</li>
 * </ol>
 *
 * <p>引擎<b>只读</b> {@link Loadout}，绝不写回玩家配装数据，从而保证配装隔离。
 */
public final class LoadoutRuleEngine {

    private LoadoutRuleEngine() {
    }

    /**
     * 解析一份配装。
     *
     * @param loadout      玩家配装选择（只读）
     * @param ruleset      当前模式规则集
     * @param registry     装备注册表
     * @param unlockedKeys 玩家已解锁的武器 key 集合（默认武器无需在内）
     * @return 逐槽位解析结果
     */
    public static LoadoutResolution resolve(Loadout loadout,
                                            LoadoutRuleset ruleset,
                                            LoadoutRegistry registry,
                                            Set<String> unlockedKeys) {
        Objects.requireNonNull(loadout, "loadout");
        Objects.requireNonNull(ruleset, "ruleset");
        Objects.requireNonNull(registry, "registry");

        Map<LoadoutSlot, LoadoutResolution.SlotResolution> bySlot = new EnumMap<>(LoadoutSlot.class);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            bySlot.put(slot, resolveSlot(loadout, ruleset, registry, unlockedKeys, slot));
        }
        return new LoadoutResolution(bySlot);
    }

    private static LoadoutResolution.SlotResolution resolveSlot(Loadout loadout,
                                                                LoadoutRuleset ruleset,
                                                                LoadoutRegistry registry,
                                                                Set<String> unlockedKeys,
                                                                LoadoutSlot slot) {
        String itemKey = loadout.slotItemKey(slot).orElse(null);
        if (itemKey == null) {
            return outcome(LoadoutResolution.Outcome.EMPTY, null);
        }
        if (!ruleset.allows(slot)) {
            return outcome(LoadoutResolution.Outcome.FILTERED_BY_RULESET, null);
        }
        LoadoutItem item = registry.find(itemKey).orElse(null);
        if (item == null) {
            return outcome(LoadoutResolution.Outcome.UNKNOWN_ITEM, null);
        }
        if (!item.isAvailableFor(loadout.classType())) {
            return outcome(LoadoutResolution.Outcome.FILTERED_BY_CLASS, null);
        }
        if (!item.isUnlockedBy(unlockedKeys)) {
            return outcome(LoadoutResolution.Outcome.LOCKED, null);
        }
        return outcome(LoadoutResolution.Outcome.GRANTED, item);
    }

    private static LoadoutResolution.SlotResolution outcome(LoadoutResolution.Outcome outcome, LoadoutItem item) {
        return new LoadoutResolution.SlotResolution(outcome, item);
    }
}
