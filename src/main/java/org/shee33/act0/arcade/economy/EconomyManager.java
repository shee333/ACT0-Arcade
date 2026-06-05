package org.shee33.act0.arcade.economy;

import net.minecraft.server.MinecraftServer;

/**
 * 经济后端解析入口：优先使用 Vault（反射，适用于 Mohist 等混合端）；缺失时退回模组内置钱包
 * {@link WalletEconomy}（Forge SavedData 兜底，保证开发环境 / 纯 Forge 端也能购买）。
 *
 * <p>结果按服务器实例缓存。服务器停止时应调用 {@link #reset()}（见生命周期事件），
 * 以便下次以新的服务器与最新 Vault 状态重新解析。
 */
public final class EconomyManager {

    private static ArcadeEconomy cached;
    private static MinecraftServer cachedServer;

    private EconomyManager() {
    }

    /**
     * 取当前经济后端（首次或换服务器时解析）。
     *
     * @param server 当前服务器（用于内置钱包兜底；可为 {@code null}，此时无 Vault 则退回 {@link NoopEconomy}）
     */
    public static synchronized ArcadeEconomy economy(MinecraftServer server) {
        if (cached == null || cachedServer != server) {
            VaultEconomy vault = VaultEconomy.tryCreate();
            if (vault != null) {
                cached = vault;
            } else if (server != null) {
                cached = new WalletEconomy(server);
            } else {
                cached = NoopEconomy.INSTANCE;
            }
            cachedServer = server;
        }
        return cached;
    }

    /** 清除缓存，下次 {@link #economy(MinecraftServer)} 时重新解析。 */
    public static synchronized void reset() {
        cached = null;
        cachedServer = null;
    }
}
