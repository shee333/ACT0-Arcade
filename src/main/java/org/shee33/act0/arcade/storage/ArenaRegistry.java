package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.shee33.act0.arcade.arena.ArcadeArena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 竞技场注册表（基于 Forge {@link SavedData}）：按 arenaId 持久化竞技场出生点配置，随存档落盘。
 *
 * <p>管理员通过命令录入出生点后存于此；开局时按模式选取竞技场。线程模型：服务器主线程访问。
 */
public final class ArenaRegistry extends SavedData {

    private static final String DATA_NAME = "act0_arcade_arenas";
    private static final String KEY_ARENAS = "arenas";

    private final Map<String, ArcadeArena> arenas = new LinkedHashMap<>();

    public ArenaRegistry() {
    }

    /** 从主世界存档获取（不存在则创建）单例注册表。 */
    public static ArenaRegistry get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArenaRegistry::load, ArenaRegistry::new, DATA_NAME);
    }

    public Optional<ArcadeArena> find(String arenaId) {
        return Optional.ofNullable(arenas.get(arenaId));
    }

    /** 注册/覆盖一个竞技场。 */
    public void put(ArcadeArena arena) {
        arenas.put(arena.arenaId(), arena);
        setDirty();
    }

    /** 移除一个竞技场，返回是否存在。 */
    public boolean remove(String arenaId) {
        boolean removed = arenas.remove(arenaId) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(arenas.keySet()));
    }

    public boolean isEmpty() {
        return arenas.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ArcadeArena arena : arenas.values()) {
            list.add(ArenaCodec.writeArena(arena));
        }
        tag.put(KEY_ARENAS, list);
        return tag;
    }

    public static ArenaRegistry load(CompoundTag tag) {
        ArenaRegistry registry = new ArenaRegistry();
        ListTag list = tag.getList(KEY_ARENAS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ArcadeArena arena = ArenaCodec.readArena(list.getCompound(i));
            registry.arenas.put(arena.arenaId(), arena);
        }
        return registry;
    }
}
