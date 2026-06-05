package org.shee33.act0.arcade.loadout;

import java.util.EnumSet;
import java.util.Set;

/**
 * 配装规则集：声明某个玩法模式允许哪些槽位生效。
 *
 * <p>这是"街机与战地共用一套配装、但街机无缝禁用兵种道具"的核心机制。
 * 规则集<b>不修改</b>玩家保存的配装数据，只在"应用层"决定哪些槽位会被实际发放。
 *
 * <ul>
 *   <li>{@link #FULL}：战地模式，全槽位开放（含 GADGET_1 / GADGET_2 兵种装置）。</li>
 *   <li>{@link #ARCADE}：街机模式，仅开放核心战斗槽（主武器 / 副武器 / 近战 / 投掷），
 *       兵种装置槽被过滤。</li>
 * </ul>
 */
public enum LoadoutRuleset {

    /** 战地：全开。 */
    FULL(EnumSet.allOf(LoadoutSlot.class)),

    /** 街机：仅核心战斗槽。 */
    ARCADE(coreCombatSlots());

    private final Set<LoadoutSlot> allowedSlots;

    LoadoutRuleset(Set<LoadoutSlot> allowedSlots) {
        // 防御性拷贝为不可变视图
        this.allowedSlots = EnumSet.copyOf(allowedSlots);
    }

    private static Set<LoadoutSlot> coreCombatSlots() {
        EnumSet<LoadoutSlot> set = EnumSet.noneOf(LoadoutSlot.class);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.isCoreCombatSlot()) {
                set.add(slot);
            }
        }
        return set;
    }

    /**
     * 判断某槽位在本规则集下是否允许生效。
     *
     * @param slot 槽位，可为 {@code null}
     * @return {@code null} 视为不允许；否则按规则集判定
     */
    public boolean allows(LoadoutSlot slot) {
        return slot != null && allowedSlots.contains(slot);
    }

    /** 本规则集允许的槽位集合（不可变快照）。 */
    public Set<LoadoutSlot> allowedSlots() {
        return EnumSet.copyOf(allowedSlots);
    }
}
