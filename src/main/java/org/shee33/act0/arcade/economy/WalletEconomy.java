package org.shee33.act0.arcade.economy;

import net.minecraft.server.MinecraftServer;
import org.shee33.act0.arcade.storage.ArcadePlayerWallet;

import java.util.UUID;

/**
 * 模组内置钱包经济：当 Vault 不可用（开发环境 / 纯 Forge 端 / 未装 Vault）时的兜底实现，
 * 由 {@link ArcadePlayerWallet}（Forge SavedData）支撑，余额随存档落盘。
 *
 * <p>始终 {@link #isAvailable() 可用}，从而保证"无论什么环境都能购买/测试"。
 */
final class WalletEconomy implements ArcadeEconomy {

    private final MinecraftServer server;

    WalletEconomy(MinecraftServer server) {
        this.server = server;
    }

    private ArcadePlayerWallet wallet() {
        return ArcadePlayerWallet.get(server);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double balance(UUID player) {
        return wallet().balance(player);
    }

    @Override
    public boolean has(UUID player, double amount) {
        return wallet().balance(player) >= amount;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return wallet().withdraw(player, amount);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount < 0) {
            return false;
        }
        wallet().add(player, amount);
        return true;
    }
}
