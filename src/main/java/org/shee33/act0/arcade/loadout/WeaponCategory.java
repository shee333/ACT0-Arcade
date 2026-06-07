package org.shee33.act0.arcade.loadout;

/**
 * 武器分类：独立于配装槽位的武器类别（步枪 / 冲锋枪 / 狙击 …），是武器库（商城）与二级武器选择界面的组织单元。
 *
 * <p>每个分类绑定一个 {@link LoadoutSlot}（决定该类武器装备到哪个槽位 / 快捷栏位置），
 * 因此管理员上架时只需指定分类，槽位由分类推导，无需手填。MC-free，可单测。
 */
public enum WeaponCategory {

    /** 步枪。 */
    RIFLE("步枪", LoadoutSlot.PRIMARY_WEAPON),

    /** 冲锋枪。 */
    SMG("冲锋枪", LoadoutSlot.PRIMARY_WEAPON),

    /** 狙击枪。 */
    SNIPER("狙击枪", LoadoutSlot.PRIMARY_WEAPON),

    /** 霰弹枪。 */
    SHOTGUN("霰弹枪", LoadoutSlot.PRIMARY_WEAPON),

    /** 轻机枪。 */
    LMG("机枪", LoadoutSlot.PRIMARY_WEAPON),

    /** 精确射手步枪。 */
    MARKSMAN("精确射手", LoadoutSlot.PRIMARY_WEAPON),

    /** 手枪。 */
    PISTOL("手枪", LoadoutSlot.SECONDARY_WEAPON),

    /** 近战武器。 */
    MELEE("近战", LoadoutSlot.MELEE),

    /** 投掷物。 */
    THROWABLE("投掷物", LoadoutSlot.THROWABLE),

    /** 装置 1。 */
    GADGET_1("装置 1", LoadoutSlot.GADGET_1),

    /** 装置 2。 */
    GADGET_2("装置 2", LoadoutSlot.GADGET_2);

    private final String displayName;
    private final LoadoutSlot slot;

    WeaponCategory(String displayName, LoadoutSlot slot) {
        this.displayName = displayName;
        this.slot = slot;
    }

    /** 中文显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 该分类武器所占的配装槽位。 */
    public LoadoutSlot slot() {
        return slot;
    }

    /** 按名称（忽略大小写）解析分类；未知返回 {@code null}。 */
    public static WeaponCategory byName(String name) {
        if (name == null) {
            return null;
        }
        for (WeaponCategory c : values()) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** 列出映射到指定槽位的全部武器分类（保持枚举声明顺序）。 */
    public static java.util.List<WeaponCategory> forSlot(LoadoutSlot slot) {
        java.util.List<WeaponCategory> result = new java.util.ArrayList<>();
        if (slot == null) {
            return result;
        }
        for (WeaponCategory c : values()) {
            if (c.slot == slot) {
                result.add(c);
            }
        }
        return result;
    }
}
