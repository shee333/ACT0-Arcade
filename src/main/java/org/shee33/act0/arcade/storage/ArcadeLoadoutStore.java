package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 街机配装持久化（基于 Forge {@link SavedData}）：按玩家 UUID 保存其多套配装选择，随存档落盘。
 *
 * <p>存于主世界（overworld）的存档数据中，全服共享一份；配装与战地模式互通，
 * 街机/战地差异只在应用层经 {@code LoadoutRuleset} 体现。
 *
 * <p>线程模型：服务器主线程访问。
 */
public final class ArcadeLoadoutStore extends SavedData {

    private static final String DATA_NAME = "act0_arcade_loadouts";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_LOADOUT = "loadout";
    private static final String KEY_LOADOUT_SET = "set";

    private final Map<UUID, LoadoutSet> loadouts = new HashMap<>();

    public ArcadeLoadoutStore() {
    }

    /** 从主世界存档获取（不存在则创建）单例存储。 */
    public static ArcadeLoadoutStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadeLoadoutStore::load, ArcadeLoadoutStore::new, DATA_NAME);
    }

    /** 读取玩家配装（不存在返回空）。 */
    public Optional<Loadout> find(UUID playerId) {
        return Optional.ofNullable(loadouts.get(playerId)).map(LoadoutSet::active);
    }

    /** 读取玩家多套配装（不存在返回空）。 */
    public Optional<LoadoutSet> findSet(UUID playerId) {
        return Optional.ofNullable(loadouts.get(playerId));
    }

    /** 读取玩家配装，不存在则用 {@code fallback} 写入并返回。 */
    public Loadout getOrCreate(UUID playerId, Loadout fallback) {
        return getOrCreateSet(playerId, fallback).active();
    }

    /** 读取玩家多套配装，不存在则把 {@code fallback} 写入第 1 套并返回。 */
    public LoadoutSet getOrCreateSet(UUID playerId, Loadout fallback) {
        LoadoutSet existing = loadouts.get(playerId);
        if (existing != null) {
            return existing;
        }
        LoadoutSet set = LoadoutSet.single(fallback);
        loadouts.put(playerId, set);
        setDirty();
        return set;
    }

    /** 保存（覆盖）玩家当前激活配装；兼容旧调用。 */
    public void save(UUID playerId, Loadout loadout) {
        LoadoutSet set = loadouts.get(playerId);
        if (set == null) {
            set = LoadoutSet.single(loadout);
            loadouts.put(playerId, set);
        } else {
            set.set(set.activeIndex(), loadout);
        }
        setDirty();
    }

    /** 保存玩家某个方案槽，并把它设为激活方案。 */
    public void save(UUID playerId, int index, Loadout loadout) {
        save(playerId, index, loadout, true, false);
    }

    /** 保存玩家某个方案槽，并按需设为激活/默认方案。 */
    public void save(UUID playerId, int index, Loadout loadout, boolean activate, boolean makeDefault) {
        LoadoutSet set = loadouts.get(playerId);
        if (set == null) {
            set = LoadoutSet.single(loadout);
            loadouts.put(playerId, set);
        }
        set.set(index, loadout);
        if (activate) {
            set.setActiveIndex(index);
        }
        if (makeDefault) {
            set.setDefaultIndex(index);
        }
        setDirty();
    }

    public void selectActive(UUID playerId, int index, Loadout fallback) {
        LoadoutSet set = getOrCreateSet(playerId, fallback);
        set.setActiveIndex(index);
        setDirty();
    }

    public void setDefault(UUID playerId, int index, Loadout fallback) {
        LoadoutSet set = getOrCreateSet(playerId, fallback);
        set.setDefaultIndex(index);
        set.setActiveIndex(index);
        setDirty();
    }

    /** 保存（覆盖）玩家多套配装。 */
    public void saveSet(UUID playerId, LoadoutSet set) {
        loadouts.put(playerId, set);
        setDirty();
    }

    public boolean contains(UUID playerId) {
        return loadouts.containsKey(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, LoadoutSet> e : loadouts.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            entry.put(KEY_LOADOUT_SET, LoadoutSetCodec.write(e.getValue()));
            entries.add(entry);
        }
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static ArcadeLoadoutStore load(CompoundTag tag) {
        ArcadeLoadoutStore store = new ArcadeLoadoutStore();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID(KEY_UUID);
            if (entry.contains(KEY_LOADOUT_SET, Tag.TAG_COMPOUND)) {
                store.loadouts.put(id, LoadoutSetCodec.read(entry.getCompound(KEY_LOADOUT_SET)));
            } else if (entry.contains(KEY_LOADOUT, Tag.TAG_COMPOUND)) {
                // 旧版单套配装迁移到第 1 套。
                store.loadouts.put(id, LoadoutSet.single(LoadoutCodec.read(entry.getCompound(KEY_LOADOUT))));
            }
        }
        return store;
    }
}
