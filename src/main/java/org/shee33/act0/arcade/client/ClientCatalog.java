package org.shee33.act0.arcade.client;

import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO.CatalogEntryDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端装备目录缓存：保存服务端通过 {@code SyncCatalogPacket} 下发的完整装备目录，
 * 供配装界面 / 游戏浏览器渲染使用。
 *
 * <p>之所以需要客户端副本：装备目录由服务端 {@code config/act0_arcade/loadout/*.json} 数据驱动，
 * 不同服务端可能装载不同枪包，因此客户端不能再用内置占位目录，必须以服务端为准。
 *
 * <p>同时为每个条目预解析一份图标 {@link ItemStack}（来自其 SNBT），使界面能直接渲染真实物品贴图
 * （含 TaCZ 枪械模型图标）。
 */
public final class ClientCatalog {

    private static final LoadoutRegistry REGISTRY = new LoadoutRegistry();
    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static boolean ready;

    private ClientCatalog() {
    }

    /** 接收服务端下发的目录，重建注册表与图标缓存。 */
    public static void accept(List<CatalogEntryDto> entries) {
        REGISTRY.clear();
        ICONS.clear();
        if (entries != null) {
            for (CatalogEntryDto entry : entries) {
                if (LoadoutCatalogIO.registerEntry(REGISTRY, entry)) {
                    ICONS.put(entry.key, LoadoutApplier.fromSnbt(entry.snbt));
                }
            }
        }
        ready = true;
    }

    /** 是否已收到服务端目录。 */
    public static boolean isReady() {
        return ready;
    }

    /** 客户端装备注册表（与服务端一致）。 */
    public static LoadoutRegistry registry() {
        return REGISTRY;
    }

    /** 取某装备键的图标物品；缺失返回 {@link ItemStack#EMPTY}。 */
    public static ItemStack iconFor(String key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return ICONS.getOrDefault(key, ItemStack.EMPTY);
    }

    /** 取某装备的图标物品。 */
    public static ItemStack iconFor(LoadoutItem item) {
        return item == null ? ItemStack.EMPTY : iconFor(item.key());
    }
}
