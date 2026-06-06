package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ArcadeArena} / {@link SpawnPoint} 的 NBT 编解码器。
 */
public final class ArenaCodec {

    private static final String KEY_ID = "id";
    private static final String KEY_SIDE_SPAWNS = "sideSpawns";
    private static final String KEY_RANDOM_SPAWNS = "randomSpawns";
    private static final String KEY_RETURN = "returnSpawn";

    private static final String KEY_DIM = "dim";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YAW = "yaw";
    private static final String KEY_PITCH = "pitch";

    private ArenaCodec() {
    }

    public static CompoundTag writeSpawn(SpawnPoint spawn) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_DIM, spawn.dimension());
        tag.putDouble(KEY_X, spawn.x());
        tag.putDouble(KEY_Y, spawn.y());
        tag.putDouble(KEY_Z, spawn.z());
        tag.putFloat(KEY_YAW, spawn.yaw());
        tag.putFloat(KEY_PITCH, spawn.pitch());
        return tag;
    }

    public static SpawnPoint readSpawn(CompoundTag tag) {
        return new SpawnPoint(
                tag.getString(KEY_DIM),
                tag.getDouble(KEY_X),
                tag.getDouble(KEY_Y),
                tag.getDouble(KEY_Z),
                tag.getFloat(KEY_YAW),
                tag.getFloat(KEY_PITCH));
    }

    public static CompoundTag writeArena(ArcadeArena arena) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ID, arena.arenaId());
        tag.put(KEY_SIDE_SPAWNS, writeSpawnList(arena.sideSpawns()));
        tag.put(KEY_RANDOM_SPAWNS, writeSpawnList(arena.randomSpawns()));
        if (arena.hasReturnSpawn()) {
            tag.put(KEY_RETURN, writeSpawn(arena.returnSpawn()));
        }
        return tag;
    }

    public static ArcadeArena readArena(CompoundTag tag) {
        String id = tag.getString(KEY_ID);
        List<SpawnPoint> sideSpawns = readSpawnList(tag.getList(KEY_SIDE_SPAWNS, Tag.TAG_COMPOUND));
        List<SpawnPoint> randomSpawns = readSpawnList(tag.getList(KEY_RANDOM_SPAWNS, Tag.TAG_COMPOUND));
        SpawnPoint returnSpawn = tag.contains(KEY_RETURN, Tag.TAG_COMPOUND) ? readSpawn(tag.getCompound(KEY_RETURN)) : null;
        return new ArcadeArena(id, sideSpawns, randomSpawns, returnSpawn);
    }

    private static ListTag writeSpawnList(List<SpawnPoint> spawns) {
        ListTag list = new ListTag();
        for (SpawnPoint spawn : spawns) {
            list.add(writeSpawn(spawn));
        }
        return list;
    }

    private static List<SpawnPoint> readSpawnList(ListTag list) {
        List<SpawnPoint> spawns = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            spawns.add(readSpawn(list.getCompound(i)));
        }
        return spawns;
    }
}
