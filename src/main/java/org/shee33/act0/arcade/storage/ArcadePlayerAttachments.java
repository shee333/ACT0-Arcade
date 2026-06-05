package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家"改装"选择持久化（基于 Forge {@link SavedData}）。
 *
 * <p>记录每位玩家对每把<b>武器 key</b> 安装的配件：{@code UUID → (weaponKey → (槽位 → 配件 key))}。
 * 由于配装槽只存武器 key（全服共享目录），玩家个性化的改装结果不能塞回共享目录，
 * 而是作为<b>玩家私有的配件选择</b>单独保存；开局 / 重生发枪时由应用层据此把已解锁且兼容的配件装上，
 * 等价于"发放改装后的整枪"。
 *
 * <p>线程模型：服务器主线程访问。
 */
public final class ArcadePlayerAttachments extends SavedData {

    private static final String DATA_NAME = "act0_arcade_attachments";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_GUNS = "guns";
    private static final String KEY_WEAPON = "weapon";
    private static final String KEY_SLOTS = "slots";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_ATTACH = "attach";

    /** UUID → weaponKey → (slotName → attachmentKey) */
    private final Map<UUID, Map<String, Map<String, String>>> data = new HashMap<>();

    public ArcadePlayerAttachments() {
    }

    /** 从主世界存档获取（不存在则创建）单例存储。 */
    public static ArcadePlayerAttachments get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadePlayerAttachments::load, ArcadePlayerAttachments::new, DATA_NAME);
    }

    /** 某玩家某武器当前的改装选择（槽位名→配件 key，不可变快照）。 */
    public Map<String, String> selections(UUID playerId, String weaponKey) {
        Map<String, Map<String, String>> guns = data.get(playerId);
        if (guns == null) {
            return Collections.emptyMap();
        }
        Map<String, String> slots = guns.get(weaponKey);
        return slots == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    /** 设置某玩家某武器某槽位的配件；{@code attachmentKey} 为 {@code null} 表示卸下该槽位。 */
    public void setSelection(UUID playerId, String weaponKey, AttachmentSlotType slot, String attachmentKey) {
        if (weaponKey == null || slot == null) {
            return;
        }
        Map<String, Map<String, String>> guns = data.computeIfAbsent(playerId, k -> new HashMap<>());
        Map<String, String> slots = guns.computeIfAbsent(weaponKey, k -> new LinkedHashMap<>());
        if (attachmentKey == null) {
            slots.remove(slot.name());
            if (slots.isEmpty()) {
                guns.remove(weaponKey);
            }
        } else {
            slots.put(slot.name(), attachmentKey);
        }
        setDirty();
    }

    /** 清空某玩家某武器的全部改装。 */
    public void clear(UUID playerId, String weaponKey) {
        Map<String, Map<String, String>> guns = data.get(playerId);
        if (guns != null && guns.remove(weaponKey) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Map<String, Map<String, String>>> e : data.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            ListTag guns = new ListTag();
            for (Map.Entry<String, Map<String, String>> g : e.getValue().entrySet()) {
                CompoundTag gun = new CompoundTag();
                gun.putString(KEY_WEAPON, g.getKey());
                ListTag slots = new ListTag();
                for (Map.Entry<String, String> s : g.getValue().entrySet()) {
                    CompoundTag slot = new CompoundTag();
                    slot.putString(KEY_SLOT, s.getKey());
                    slot.putString(KEY_ATTACH, s.getValue());
                    slots.add(slot);
                }
                gun.put(KEY_SLOTS, slots);
                guns.add(gun);
            }
            entry.put(KEY_GUNS, guns);
            entries.add(entry);
        }
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static ArcadePlayerAttachments load(CompoundTag tag) {
        ArcadePlayerAttachments store = new ArcadePlayerAttachments();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID(KEY_UUID);
            Map<String, Map<String, String>> guns = new HashMap<>();
            ListTag gunList = entry.getList(KEY_GUNS, Tag.TAG_COMPOUND);
            for (int j = 0; j < gunList.size(); j++) {
                CompoundTag gun = gunList.getCompound(j);
                String weaponKey = gun.getString(KEY_WEAPON);
                Map<String, String> slots = new LinkedHashMap<>();
                ListTag slotList = gun.getList(KEY_SLOTS, Tag.TAG_COMPOUND);
                for (int k = 0; k < slotList.size(); k++) {
                    CompoundTag slot = slotList.getCompound(k);
                    slots.put(slot.getString(KEY_SLOT), slot.getString(KEY_ATTACH));
                }
                if (!slots.isEmpty()) {
                    guns.put(weaponKey, slots);
                }
            }
            store.data.put(id, guns);
        }
        return store;
    }
}
