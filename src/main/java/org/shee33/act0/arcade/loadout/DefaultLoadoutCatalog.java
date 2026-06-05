package org.shee33.act0.arcade.loadout;

/**
 * 默认装备目录辅助。
 *
 * <p>本模组的武器全部由管理员经武器库（{@code /arcade armory …}）上架，数据驱动地存于
 * {@code config/act0_arcade/loadout/*.json}，<b>不再内置任何占位武器</b>。
 * 本类仅保留与"默认配装"相关的少量辅助方法。
 */
public final class DefaultLoadoutCatalog {

    private DefaultLoadoutCatalog() {
    }

    /**
     * 向注册表注册内置装备——当前为空（所有武器改由武器库数据驱动）。保留方法以兼容调用点。
     */
    public static void register(LoadoutRegistry registry) {
        // 不再注册任何占位武器；武器由 config/act0_arcade/loadout/*.json 数据驱动。
    }

    /**
     * 为新玩家生成一份默认配装：默认职业 + 每个槽位选注册表中第一件<b>默认初始武器</b>。
     *
     * <p>玩家未购买任何武器时也能凭默认武器开局；未配置默认武器的槽位留空。
     */
    public static Loadout defaultLoadout(LoadoutRegistry registry) {
        PlayerClassType classType = PlayerClassType.defaultClass();
        Loadout loadout = new Loadout("默认配装", classType);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            registry.availableItems(slot, classType).stream()
                    .filter(LoadoutItem::isDefault)
                    .findFirst()
                    .ifPresent(item -> loadout.setSlot(slot, item.key()));
        }
        return loadout;
    }
}
