package org.shee33.act0.arcade.loadout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 配装装备注册表：所有可用 {@link LoadoutItem} 定义的集合，按 key 索引。
 *
 * <p>作为配装系统的数据源，供配装 GUI 列出可选装备、供规则引擎按 key 解析。
 * 不依赖 Minecraft 运行时，可单测。
 */
public final class LoadoutRegistry {

    private final Map<String, LoadoutItem> itemsByKey = new LinkedHashMap<>();

    /**
     * 注册一件装备定义。
     *
     * @throws IllegalStateException key 重复时
     */
    public void register(LoadoutItem item) {
        Objects.requireNonNull(item, "item");
        LoadoutItem previous = itemsByKey.putIfAbsent(item.key(), item);
        if (previous != null) {
            throw new IllegalStateException("重复注册配装装备 key: " + item.key());
        }
    }

    /** 按 key 查找装备定义。 */
    public Optional<LoadoutItem> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(itemsByKey.get(key));
    }

    /** 列出某槽位下的全部装备定义（注册顺序）。 */
    public List<LoadoutItem> itemsForSlot(LoadoutSlot slot) {
        List<LoadoutItem> result = new ArrayList<>();
        if (slot == null) {
            return result;
        }
        for (LoadoutItem item : itemsByKey.values()) {
            if (item.slot() == slot) {
                result.add(item);
            }
        }
        return result;
    }

    /** 列出某武器分类下的全部装备定义（注册顺序），供二级武器选择界面分类展示。 */
    public List<LoadoutItem> itemsForCategory(WeaponCategory category) {
        List<LoadoutItem> result = new ArrayList<>();
        if (category == null) {
            return result;
        }
        for (LoadoutItem item : itemsByKey.values()) {
            if (item.category() == category) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 列出某槽位下、对指定职业可用的装备（供 GUI 过滤展示）。解锁状态由调用方按已解锁集合另行判断。
     *
     * @param slot      槽位
     * @param classType 职业
     */
    public List<LoadoutItem> availableItems(LoadoutSlot slot, PlayerClassType classType) {
        List<LoadoutItem> result = new ArrayList<>();
        for (LoadoutItem item : itemsForSlot(slot)) {
            if (item.isAvailableFor(classType)) {
                result.add(item);
            }
        }
        return result;
    }

    /** 列出某武器分类下、对指定职业可用的装备（供二级武器选择界面分类展示）。 */
    public List<LoadoutItem> availableItemsInCategory(WeaponCategory category, PlayerClassType classType) {
        List<LoadoutItem> result = new ArrayList<>();
        for (LoadoutItem item : itemsForCategory(category)) {
            if (item.isAvailableFor(classType)) {
                result.add(item);
            }
        }
        return result;
    }

    /** 全部已注册装备（不可变视图）。 */
    public Map<String, LoadoutItem> all() {
        return Collections.unmodifiableMap(itemsByKey);
    }

    /** 清空全部装备定义（用于从配置重新加载目录）。 */
    public void clear() {
        itemsByKey.clear();
    }
}
