package org.shee33.act0.arcade.economy;

import java.util.UUID;

/**
 * 街机经济抽象：把"查余额 / 是否买得起 / 扣款"收敛为一个与具体经济插件无关的接口。
 *
 * <p>底层可由 Vault（{@link VaultEconomy}，经反射调用 Bukkit 服务，适用于 Mohist 等混合端）实现，
 * 也可在缺失时退回 {@link NoopEconomy}。上层（武器库购买逻辑）只依赖本接口。
 */
public interface ArcadeEconomy {

    /** 经济后端是否可用（不可用时购买应被拒绝）。 */
    boolean isAvailable();

    /** 查询玩家余额。 */
    double balance(UUID player);

    /** 玩家是否买得起给定金额。 */
    boolean has(UUID player, double amount);

    /** 从玩家扣款；成功返回 {@code true}。 */
    boolean withdraw(UUID player, double amount);

    /** 给玩家存入金额；成功返回 {@code true}。 */
    boolean deposit(UUID player, double amount);
}
