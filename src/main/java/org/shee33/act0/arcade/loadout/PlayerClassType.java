package org.shee33.act0.arcade.loadout;

/**
 * 玩家职业（兵种）类型。
 *
 * <p>采用战地配装的四职业划分，作为街机/战地共用配装的规范定义。
 * 职业用于过滤"仅限特定职业"的装备（见 {@link LoadoutItem#allowedClasses()}）。
 *
 * <p>街机模式通常不强调职业差异，但仍沿用同一套枚举以保证与战地配装数据互通。
 */
public enum PlayerClassType {

    /** 突击兵：步枪 / 冲锋枪主武器。 */
    ASSAULT,

    /** 支援兵：补给箱部署能力。 */
    SUPPORT,

    /** 工程兵：远程部署能力。 */
    ENGINEER,

    /** 侦察兵：轻武器、高机动。 */
    RECON;

    /** 默认职业。 */
    public static PlayerClassType defaultClass() {
        return ASSAULT;
    }
}
