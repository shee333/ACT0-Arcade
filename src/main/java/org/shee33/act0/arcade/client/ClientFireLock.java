package org.shee33.act0.arcade.client;

/** 客户端开火键锁：倒计时期间禁用攻击/使用物品输入。 */
public final class ClientFireLock {

    private static volatile boolean locked;

    private ClientFireLock() {
    }

    public static void setLocked(boolean locked) {
        ClientFireLock.locked = locked;
    }

    public static boolean isLocked() {
        return locked;
    }
}
