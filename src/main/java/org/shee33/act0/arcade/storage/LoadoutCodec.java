package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;

/**
 * {@link Loadout} 的 NBT 编解码器：把玩家配装选择序列化为 {@link CompoundTag}（用于持久化/网络）。
 *
 * <p>仅依赖 Minecraft 的 NBT 类型，不触碰世界状态；与 MC-free 核心保持清晰边界。
 *
 * <p>NBT 结构：
 * <pre>
 * {
 *   name: String,
 *   class: String,            // PlayerClassType.name()
 *   slots: { PRIMARY_WEAPON: "item_key", ... }   // 仅含已选槽位
 * }
 * </pre>
 */
public final class LoadoutCodec {

    private static final String KEY_NAME = "name";
    private static final String KEY_CLASS = "class";
    private static final String KEY_SLOTS = "slots";

    private LoadoutCodec() {
    }

    public static CompoundTag write(Loadout loadout) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_NAME, loadout.name());
        tag.putString(KEY_CLASS, loadout.classType().name());
        CompoundTag slots = new CompoundTag();
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            loadout.slotItemKey(slot).ifPresent(key -> slots.putString(slot.name(), key));
        }
        tag.put(KEY_SLOTS, slots);
        return tag;
    }

    public static Loadout read(CompoundTag tag) {
        String name = tag.contains(KEY_NAME) ? tag.getString(KEY_NAME) : "Loadout";
        PlayerClassType classType = parseClass(tag.getString(KEY_CLASS));
        Loadout loadout = new Loadout(name, classType);
        CompoundTag slots = tag.getCompound(KEY_SLOTS);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slots.contains(slot.name())) {
                loadout.setSlot(slot, slots.getString(slot.name()));
            }
        }
        return loadout;
    }

    private static PlayerClassType parseClass(String raw) {
        if (raw == null || raw.isEmpty()) {
            return PlayerClassType.defaultClass();
        }
        try {
            return PlayerClassType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return PlayerClassType.defaultClass();
        }
    }
}
