package org.shee33.act0.arcade.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模组内置钱包（基于 Forge {@link SavedData}）：在没有 Vault 经济后端（如开发环境 / 纯 Forge 端）时，
 * 作为兜底货币系统，按玩家 UUID 记录余额并随存档落盘。
 *
 * <p>线上 Mohist + Vault 环境下不使用此钱包（经济走 Vault）；此钱包仅供 {@code WalletEconomy} 兜底。
 *
 * <p>线程模型：服务器主线程访问。
 */
public final class ArcadePlayerWallet extends SavedData {

    private static final String DATA_NAME = "act0_arcade_wallet";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_BALANCE = "balance";

    private final Map<UUID, Double> balances = new HashMap<>();

    public ArcadePlayerWallet() {
    }

    /** 从主世界存档获取（不存在则创建）单例存储。 */
    public static ArcadePlayerWallet get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ArcadePlayerWallet::load, ArcadePlayerWallet::new, DATA_NAME);
    }

    /** 查询玩家余额。 */
    public double balance(UUID playerId) {
        return balances.getOrDefault(playerId, 0.0);
    }

    /** 设置玩家余额（不为负）。 */
    public void set(UUID playerId, double amount) {
        balances.put(playerId, Math.max(0.0, amount));
        setDirty();
    }

    /** 增加余额（可为负，结果不为负）。 */
    public void add(UUID playerId, double amount) {
        set(playerId, balance(playerId) + amount);
    }

    /** 尝试扣款；余额不足返回 {@code false}。 */
    public boolean withdraw(UUID playerId, double amount) {
        double current = balance(playerId);
        if (amount < 0 || current < amount) {
            return false;
        }
        set(playerId, current - amount);
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            entry.putDouble(KEY_BALANCE, e.getValue());
            entries.add(entry);
        }
        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    public static ArcadePlayerWallet load(CompoundTag tag) {
        ArcadePlayerWallet store = new ArcadePlayerWallet();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            store.balances.put(entry.getUUID(KEY_UUID), entry.getDouble(KEY_BALANCE));
        }
        return store;
    }
}
