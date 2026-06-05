package org.shee33.act0.arcade.loadout;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 一名玩家保存的<b>配装选择</b>（与具体玩法模式无关）。
 *
 * <p>由职业 + 各槽位选中的装备 key + 名称组成。这是会被持久化的玩家数据，
 * 街机与战地共用同一份选择；模式差异通过 {@link LoadoutRuleset} 在应用层体现，
 * 而<b>不</b>修改本对象。
 */
public final class Loadout {

    private String name;
    private PlayerClassType classType;
    private final Map<LoadoutSlot, String> slotItemKeys = new EnumMap<>(LoadoutSlot.class);

    public Loadout(String name) {
        this(name, PlayerClassType.defaultClass());
    }

    public Loadout(String name, PlayerClassType classType) {
        this.name = name != null ? name : "Loadout";
        this.classType = classType != null ? classType : PlayerClassType.defaultClass();
    }

    /** 配装名称。 */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : this.name;
    }

    /** 职业。 */
    public PlayerClassType classType() {
        return classType;
    }

    public void setClassType(PlayerClassType classType) {
        if (classType != null) {
            this.classType = classType;
        }
    }

    /**
     * 设置某槽位选中的装备 key；传入 {@code null} 表示清空该槽位。
     */
    public void setSlot(LoadoutSlot slot, String itemKey) {
        Objects.requireNonNull(slot, "slot");
        if (itemKey == null) {
            slotItemKeys.remove(slot);
        } else {
            slotItemKeys.put(slot, itemKey);
        }
    }

    /** 取某槽位选中的装备 key。 */
    public Optional<String> slotItemKey(LoadoutSlot slot) {
        return Optional.ofNullable(slotItemKeys.get(slot));
    }

    /** 所有槽位的选择快照（不可变拷贝）。 */
    public Map<LoadoutSlot, String> slotItemKeys() {
        return new EnumMap<>(slotItemKeys);
    }
}
