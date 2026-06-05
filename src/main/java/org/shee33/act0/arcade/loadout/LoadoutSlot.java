package org.shee33.act0.arcade.loadout;

/**
 * 配装槽位，绑定到热键栏位置。
 *
 * <p>采用战地配装的六槽位设计。{@link #hotbarIndex()} 为该槽位装备在玩家
 * 快捷栏（0–8）中的固定位置。
 *
 * <p>槽位被划分为两类：
 * <ul>
 *   <li><b>核心战斗槽</b>（{@link #isCoreCombatSlot()} 为 {@code true}）：主武器 / 副武器 / 近战 / 投掷，
 *       所有模式通用。</li>
 *   <li><b>兵种装置槽</b>：GADGET_1 / GADGET_2，承载战地兵种特殊道具（补给箱、医疗箱、信标等），
 *       在街机模式下会被 {@link LoadoutRuleset#ARCADE} 无缝禁用。</li>
 * </ul>
 */
public enum LoadoutSlot {

    /** 主武器。 */
    PRIMARY_WEAPON(0, true),

    /** 副武器。 */
    SECONDARY_WEAPON(1, true),

    /** 近战武器。 */
    MELEE(2, true),

    /** 装置 1（兵种专属，街机禁用）。 */
    GADGET_1(3, false),

    /** 装置 2（兵种专属，街机禁用）。 */
    GADGET_2(4, false),

    /** 投掷物。 */
    THROWABLE(5, true);

    private final int hotbarIndex;
    private final boolean coreCombatSlot;

    LoadoutSlot(int hotbarIndex, boolean coreCombatSlot) {
        this.hotbarIndex = hotbarIndex;
        this.coreCombatSlot = coreCombatSlot;
    }

    /** 该槽位装备在快捷栏中的固定位置（0–8）。 */
    public int hotbarIndex() {
        return hotbarIndex;
    }

    /**
     * 是否为所有模式通用的核心战斗槽。
     *
     * @return {@code true} 表示主武器 / 副武器 / 近战 / 投掷；{@code false} 表示兵种装置槽。
     */
    public boolean isCoreCombatSlot() {
        return coreCombatSlot;
    }
}
