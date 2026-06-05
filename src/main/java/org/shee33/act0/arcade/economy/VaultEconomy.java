package org.shee33.act0.arcade.economy;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Vault 经济实现（全反射）：在 Mohist 等 Forge+Bukkit 混合端上，通过反射拿到 Bukkit 的
 * {@code ServicesManager} 注册的 {@code net.milkbowl.vault.economy.Economy} 服务并调用之。
 *
 * <p><b>不硬编译依赖</b> Bukkit / Vault：所有类型与方法均运行时反射解析，缺失任一环节即视为不可用，
 * 从而在纯 Forge 端或未装 Vault 时安全降级（见 {@link EconomyManager}）。
 */
final class VaultEconomy implements ArcadeEconomy {

    private final Object economy;          // net.milkbowl.vault.economy.Economy 实例
    private final Method getOfflinePlayer; // static Bukkit.getOfflinePlayer(UUID)
    private final Method getBalance;       // Economy.getBalance(OfflinePlayer)
    private final Method has;              // Economy.has(OfflinePlayer, double)
    private final Method withdrawPlayer;   // Economy.withdrawPlayer(OfflinePlayer, double)
    private final Method depositPlayer;    // Economy.depositPlayer(OfflinePlayer, double)
    private final Method transactionSuccess; // EconomyResponse.transactionSuccess()

    private VaultEconomy(Object economy, Method getOfflinePlayer, Method getBalance,
                         Method has, Method withdrawPlayer, Method depositPlayer, Method transactionSuccess) {
        this.economy = economy;
        this.getOfflinePlayer = getOfflinePlayer;
        this.getBalance = getBalance;
        this.has = has;
        this.withdrawPlayer = withdrawPlayer;
        this.depositPlayer = depositPlayer;
        this.transactionSuccess = transactionSuccess;
    }

    /** 尝试解析 Vault 经济服务；任何环节失败返回 {@code null}。 */
    static VaultEconomy tryCreate() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");

            Object servicesManager = bukkit.getMethod("getServicesManager").invoke(null);
            if (servicesManager == null) {
                return null;
            }
            Method getRegistration = servicesManager.getClass().getMethod("getRegistration", Class.class);
            Object registration = getRegistration.invoke(servicesManager, economyClass);
            if (registration == null) {
                return null;
            }
            Object economy = registration.getClass().getMethod("getProvider").invoke(registration);
            if (economy == null) {
                return null;
            }

            Method getOfflinePlayer = bukkit.getMethod("getOfflinePlayer", UUID.class);
            Method getBalance = economyClass.getMethod("getBalance", offlinePlayerClass);
            Method has = economyClass.getMethod("has", offlinePlayerClass, double.class);
            Method withdrawPlayer = economyClass.getMethod("withdrawPlayer", offlinePlayerClass, double.class);
            Method depositPlayer = economyClass.getMethod("depositPlayer", offlinePlayerClass, double.class);
            Method transactionSuccess = responseClass.getMethod("transactionSuccess");

            return new VaultEconomy(economy, getOfflinePlayer, getBalance, has, withdrawPlayer, depositPlayer, transactionSuccess);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object offlinePlayer(UUID player) throws Exception {
        return getOfflinePlayer.invoke(null, player);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double balance(UUID player) {
        try {
            Object result = getBalance.invoke(economy, offlinePlayer(player));
            return result instanceof Number ? ((Number) result).doubleValue() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public boolean has(UUID player, double amount) {
        try {
            Object result = has.invoke(economy, offlinePlayer(player), amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        try {
            Object response = withdrawPlayer.invoke(economy, offlinePlayer(player), amount);
            if (response == null) {
                return false;
            }
            Object success = transactionSuccess.invoke(response);
            return success instanceof Boolean && (Boolean) success;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        try {
            Object response = depositPlayer.invoke(economy, offlinePlayer(player), amount);
            if (response == null) {
                return false;
            }
            Object success = transactionSuccess.invoke(response);
            return success instanceof Boolean && (Boolean) success;
        } catch (Exception e) {
            return false;
        }
    }
}
