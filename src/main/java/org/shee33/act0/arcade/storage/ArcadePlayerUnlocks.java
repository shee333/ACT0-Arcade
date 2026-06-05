package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家武器解锁持久化（基于 Forge {@link SavedData}）：按玩家 UUID 记录其已购买/解锁的武器 key 集合，随存档落盘。
 *
 * <p>默认初始武器（{@code LoadoutItem.isDefault()}）对所有玩家免费可用，<b>不</b>记录于此；
 * 此处仅保存通过武器库购买解锁的武器。
 *
 * <p>线程模型：服务器主线程访问。
 */
public final class ArcadePlayerUnlocks extends SavedData {

    private static final String DATA_NAME = "act0_arcade_unlocks";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_KEYS = "keys";

    private final Map<UUID, Set<String>> unlocks = new HashMap<>();

    public ArcadePlayerUnlocks() {
    }

    /** 从主世界存档获取（不存在则创建）单例存储。 */
    public static ArcadePlayerUnlocks get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadePlayerUnlocks::load, ArcadePlayerUnlocks::new, DATA_NAME);
    }

    /** 玩家已解锁的武器 key 集合（不可变快照）。 */
    public Set<String> unlocked(UUID playerId) {
        Set<String> set = unlocks.get(playerId);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    /** 玩家是否已解锁某武器。 */
    public boolean isUnlocked(UUID playerId, String key) {
        Set<String> set = unlocks.get(playerId);
        return set != null && set.contains(key);
    }

    /** 解锁一件武器；返回是否为新增（此前未解锁）。 */
    public boolean unlock(UUID playerId, String key) {
        boolean added = unlocks.computeIfAbsent(playerId, k -> new LinkedHashSet<>()).add(key);
        if (added) {
            setDirty();
        }
        return added;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Set<String>> e : unlocks.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            ListTag keys = new ListTag();
            for (String key : e.getValue()) {
                keys.add(StringTag.valueOf(key));
            }
            entry.put(KEY_KEYS, keys);
            entries.add(entry);
        }
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static ArcadePlayerUnlocks load(CompoundTag tag) {
        ArcadePlayerUnlocks store = new ArcadePlayerUnlocks();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID(KEY_UUID);
            Set<String> keys = new LinkedHashSet<>();
            ListTag list = entry.getList(KEY_KEYS, Tag.TAG_STRING);
            for (int j = 0; j < list.size(); j++) {
                keys.add(list.getString(j));
            }
            store.unlocks.put(id, keys);
        }
        return store;
    }
}
