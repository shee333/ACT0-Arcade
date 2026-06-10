package org.shee33.act0.arcade.loadout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 服饰目录注册表。 */
public final class ApparelRegistry {
    private final Map<String, ApparelItem> itemsByKey = new LinkedHashMap<>();

    public void register(ApparelItem item) {
        Objects.requireNonNull(item, "item");
        ApparelItem previous = itemsByKey.putIfAbsent(item.key(), item);
        if (previous != null) {
            throw new IllegalStateException("重复注册服饰 key: " + item.key());
        }
    }

    public Optional<ApparelItem> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(itemsByKey.get(key));
    }

    public List<ApparelItem> itemsForSlot(ApparelSlot slot) {
        List<ApparelItem> result = new ArrayList<>();
        if (slot == null) {
            return result;
        }
        for (ApparelItem item : itemsByKey.values()) {
            if (item.slot() == slot) {
                result.add(item);
            }
        }
        return result;
    }

    public Map<String, ApparelItem> all() {
        return Collections.unmodifiableMap(itemsByKey);
    }

    public void clear() {
        itemsByKey.clear();
    }
}
