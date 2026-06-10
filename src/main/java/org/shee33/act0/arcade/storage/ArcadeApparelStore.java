package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.shee33.act0.arcade.loadout.ApparelSelection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 玩家全配装共享服饰选择持久化。 */
public final class ArcadeApparelStore extends SavedData {
    private static final String DATA_NAME = "act0_arcade_apparel";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_SELECTION = "selection";

    private final Map<UUID, ApparelSelection> selections = new HashMap<>();

    public static ArcadeApparelStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadeApparelStore::load, ArcadeApparelStore::new, DATA_NAME);
    }

    public ApparelSelection getOrCreate(UUID playerId) {
        return selections.computeIfAbsent(playerId, ignored -> new ApparelSelection());
    }

    public void saveSelection(UUID playerId, ApparelSelection selection) {
        selections.put(playerId, selection != null ? selection : new ApparelSelection());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, ApparelSelection> e : selections.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            entry.put(KEY_SELECTION, ApparelSelectionCodec.write(e.getValue()));
            entries.add(entry);
        }
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static ArcadeApparelStore load(CompoundTag tag) {
        ArcadeApparelStore store = new ArcadeApparelStore();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID(KEY_UUID);
            store.selections.put(id, ApparelSelectionCodec.read(entry.getCompound(KEY_SELECTION)));
        }
        return store;
    }
}
