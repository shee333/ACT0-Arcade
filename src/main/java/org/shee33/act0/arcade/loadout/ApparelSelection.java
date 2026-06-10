package org.shee33.act0.arcade.loadout;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** 玩家全配装共享的服饰选择。 */
public final class ApparelSelection {
    private final Map<ApparelSlot, String> selected = new EnumMap<>(ApparelSlot.class);

    public Optional<String> selectedKey(ApparelSlot slot) {
        return Optional.ofNullable(selected.get(slot));
    }

    public void set(ApparelSlot slot, String key) {
        if (slot == null) {
            return;
        }
        if (key == null || key.isBlank()) {
            selected.remove(slot);
        } else {
            selected.put(slot, key);
        }
    }

    public Map<ApparelSlot, String> all() {
        return new EnumMap<>(selected);
    }

    public ApparelSelection copy() {
        ApparelSelection copy = new ApparelSelection();
        for (Map.Entry<ApparelSlot, String> e : selected.entrySet()) {
            copy.set(e.getKey(), e.getValue());
        }
        return copy;
    }
}
