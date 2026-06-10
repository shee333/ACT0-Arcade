package org.shee33.act0.arcade.client;

import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.storage.ApparelCatalogIO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 客户端服饰目录与当前选择缓存。 */
public final class ClientApparel {
    private static final ApparelRegistry REGISTRY = new ApparelRegistry();
    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static ApparelSelection selection = new ApparelSelection();

    private ClientApparel() {
    }

    public static void accept(List<ApparelCatalogIO.ApparelEntryDto> entries, ApparelSelection incoming) {
        REGISTRY.clear();
        ICONS.clear();
        if (entries != null) {
            for (ApparelCatalogIO.ApparelEntryDto entry : entries) {
                if (ApparelCatalogIO.registerEntry(REGISTRY, entry)) {
                    ICONS.put(entry.key, LoadoutApplier.fromSnbt(entry.snbt));
                }
            }
        }
        selection = incoming != null ? incoming : new ApparelSelection();
    }

    public static ApparelRegistry registry() {
        return REGISTRY;
    }

    public static ApparelSelection selection() {
        return selection;
    }

    public static void setSelection(ApparelSelection next) {
        selection = next != null ? next : new ApparelSelection();
    }

    public static ItemStack iconFor(String key) {
        return key == null ? ItemStack.EMPTY : ICONS.getOrDefault(key, ItemStack.EMPTY);
    }

    public static ItemStack iconFor(ApparelItem item) {
        return item == null ? ItemStack.EMPTY : iconFor(item.key());
    }
}
