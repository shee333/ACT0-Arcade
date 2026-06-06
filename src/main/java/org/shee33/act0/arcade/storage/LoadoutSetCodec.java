package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LoadoutSet} 的 NBT 编解码器：用于持久化/网络同步多套配装。
 *
 * <p>结构：
 * <pre>
 * {
 *   active: 0,
 *   default: 0,
 *   loadouts: [ {Loadout}, {Loadout}, ... ]
 * }
 * </pre>
 */
public final class LoadoutSetCodec {

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_DEFAULT = "default";
    private static final String KEY_LOADOUTS = "loadouts";

    private LoadoutSetCodec() {
    }

    public static CompoundTag write(LoadoutSet set) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_ACTIVE, set.activeIndex());
        tag.putInt(KEY_DEFAULT, set.defaultIndex());
        ListTag list = new ListTag();
        for (Loadout loadout : set.all()) {
            list.add(LoadoutCodec.write(loadout));
        }
        tag.put(KEY_LOADOUTS, list);
        return tag;
    }

    public static LoadoutSet read(CompoundTag tag) {
        int active = tag.contains(KEY_ACTIVE) ? tag.getInt(KEY_ACTIVE) : 0;
        int def = tag.contains(KEY_DEFAULT) ? tag.getInt(KEY_DEFAULT) : active;
        List<Loadout> loadouts = new ArrayList<>();
        ListTag list = tag.getList(KEY_LOADOUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            loadouts.add(LoadoutCodec.read(list.getCompound(i)));
        }
        if (loadouts.isEmpty()) {
            loadouts.add(new Loadout("配装 1"));
        }
        return new LoadoutSet(loadouts, active, def);
    }
}
