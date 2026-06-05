package org.shee33.act0.arcade.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端武器解锁缓存：保存服务端通过 {@code SyncUnlocksPacket} 下发的、当前玩家已购买/解锁的武器 key 集合，
 * 供二级武器选择界面判断哪些武器需要灰色蒙版与"未解锁"标注。
 *
 * <p>默认初始武器（{@code LoadoutItem.isDefault()}）始终可用，不依赖此集合。
 */
public final class ClientUnlocks {

    private static final Set<String> UNLOCKED = new HashSet<>();

    private ClientUnlocks() {
    }

    /** 接收服务端下发的已解锁集合，整体替换。 */
    public static void accept(Collection<String> keys) {
        UNLOCKED.clear();
        if (keys != null) {
            UNLOCKED.addAll(keys);
        }
    }

    /** 当前玩家是否已解锁某武器。 */
    public static boolean isUnlocked(String key) {
        return key != null && UNLOCKED.contains(key);
    }
}
