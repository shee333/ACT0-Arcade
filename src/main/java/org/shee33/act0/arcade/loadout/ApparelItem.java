package org.shee33.act0.arcade.loadout;

import java.util.Objects;
import java.util.Set;

/** 一件可装备服饰定义。 */
public final class ApparelItem {
    private final String key;
    private final String displayName;
    private final ApparelSlot slot;
    private final int price;
    private final boolean isDefault;
    private final String itemSnbt;

    public ApparelItem(String key, String displayName, ApparelSlot slot, int price, boolean isDefault, String itemSnbt) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : key;
        this.slot = Objects.requireNonNull(slot, "slot");
        this.price = Math.max(0, price);
        this.isDefault = isDefault;
        this.itemSnbt = itemSnbt;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public ApparelSlot slot() {
        return slot;
    }

    public int price() {
        return price;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String itemSnbt() {
        return itemSnbt;
    }

    public boolean isUnlockedBy(Set<String> unlockedKeys) {
        return isDefault || (unlockedKeys != null && unlockedKeys.contains(key));
    }
}
