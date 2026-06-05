package org.shee33.act0.arcade.economy;

import java.util.UUID;

/**
 * 空经济实现：当没有可用经济后端（如未安装 Vault）时使用。
 *
 * <p>余额恒为 0、永远买不起、扣款总失败——从而在缺少经济插件时<b>拒绝一切付费购买</b>，
 * 但默认初始武器仍免费可用（解锁判定不经过经济）。
 */
public final class NoopEconomy implements ArcadeEconomy {

    public static final NoopEconomy INSTANCE = new NoopEconomy();

    private NoopEconomy() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public double balance(UUID player) {
        return 0.0;
    }

    @Override
    public boolean has(UUID player, double amount) {
        return false;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return false;
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        return false;
    }
}
