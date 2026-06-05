package org.shee33.act0.arcade.loadout;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一件可装备物品的<b>定义</b>（与玩家无关）。
 *
 * <p>这是配装系统的数据驱动单元：每件装备由唯一 {@code key} 标识，声明所属槽位、可用职业、
 * 解锁等级与显示名。具体的 Minecraft 物品由 {@link ItemFactory} 在应用层按需生成，
 * 从而让本类及规则逻辑保持<b>不依赖 Minecraft 运行时、可单测</b>。
 *
 * <p>新增装备只需新增一个 {@code LoadoutItem} 定义（未来可由 JSON 加载），无需改动渲染或规则代码。
 */
public final class LoadoutItem {

    /**
     * 生成该装备对应 Minecraft 物品的工厂。
     *
     * <p>返回类型为 {@link Object} 以避免核心模块直接依赖 {@code net.minecraft} 类型，
     * 应用层（{@code LoadoutApplier}）负责转型为 {@code ItemStack}。可为 {@code null}（占位/未实现）。
     */
    @FunctionalInterface
    public interface ItemFactory {
        Object create();
    }

    private final String key;
    private final String displayName;
    private final WeaponCategory category;
    private final Set<PlayerClassType> allowedClasses;
    private final int price;
    private final int initialAmmo;
    private final boolean isDefault;
    private final ItemFactory itemFactory;
    private final String itemSnbt;

    private LoadoutItem(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "key");
        this.displayName = builder.displayName != null ? builder.displayName : builder.key;
        this.category = Objects.requireNonNull(builder.category, "category");
        this.allowedClasses = builder.allowedClasses.isEmpty()
                ? EnumSet.allOf(PlayerClassType.class)
                : EnumSet.copyOf(builder.allowedClasses);
        this.price = Math.max(0, builder.price);
        this.initialAmmo = Math.max(0, builder.initialAmmo);
        this.isDefault = builder.isDefault;
        this.itemFactory = builder.itemFactory;
        this.itemSnbt = builder.itemSnbt;
    }

    /** 唯一标识键。 */
    public String key() {
        return key;
    }

    /** 显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 武器分类。 */
    public WeaponCategory category() {
        return category;
    }

    /** 所属槽位（由分类推导）。 */
    public LoadoutSlot slot() {
        return category.slot();
    }

    /** 允许使用该装备的职业集合（不可变快照；空表示全职业可用）。 */
    public Set<PlayerClassType> allowedClasses() {
        return EnumSet.copyOf(allowedClasses);
    }

    /** 解锁所需货币（默认武器为 0）。 */
    public int price() {
        return price;
    }

    /** 解锁/发放时附带的初始备弹（元子弹）数量。 */
    public int initialAmmo() {
        return initialAmmo;
    }

    /** 是否为默认初始武器（对所有玩家免费可用）。 */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * 判断该装备是否对指定职业可用。
     *
     * @param classType 职业，可为 {@code null}
     * @return {@code null} 视为不可用
     */
    public boolean isAvailableFor(PlayerClassType classType) {
        return classType != null && allowedClasses.contains(classType);
    }

    /**
     * 判断该武器对持有给定已解锁集合的玩家是否可用：默认武器恒可用，否则需在已解锁集合内。
     *
     * @param unlockedKeys 玩家已解锁的武器 key 集合（可为 {@code null}）
     */
    public boolean isUnlockedBy(Set<String> unlockedKeys) {
        return isDefault || (unlockedKeys != null && unlockedKeys.contains(key));
    }

    /**
     * 生成对应的 Minecraft 物品（{@code ItemStack}），无工厂时返回 {@code null}。
     */
    public Object createItem() {
        return itemFactory != null ? itemFactory.create() : null;
    }

    /**
     * 该装备的物品 NBT 序列化字符串（SNBT），用于数据驱动地还原物品（含 TaCZ 枪械的完整 NBT）。
     *
     * <p>为 MC-free 的纯字符串：核心不解析它，由 MC 应用层（{@code LoadoutApplier}）反序列化为 {@code ItemStack}。
     * 可为 {@code null}（此时回退到 {@link #createItem()}）。
     */
    public String itemSnbt() {
        return itemSnbt;
    }

    /** 创建一个绑定到指定分类的构建器。 */
    public static Builder builder(String key, WeaponCategory category) {
        return new Builder(key, category);
    }

    /** {@link LoadoutItem} 构建器。 */
    public static final class Builder {
        private final String key;
        private final WeaponCategory category;
        private String displayName;
        private final Set<PlayerClassType> allowedClasses = EnumSet.noneOf(PlayerClassType.class);
        private int price;
        private int initialAmmo;
        private boolean isDefault;
        private ItemFactory itemFactory;
        private String itemSnbt;

        private Builder(String key, WeaponCategory category) {
            this.key = key;
            this.category = category;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** 限定可用职业；不调用则默认全职业可用。 */
        public Builder allowedClasses(PlayerClassType... classes) {
            this.allowedClasses.clear();
            Collections.addAll(this.allowedClasses, classes);
            return this;
        }

        /** 解锁价格。 */
        public Builder price(int price) {
            this.price = price;
            return this;
        }

        /** 初始备弹（元子弹）数量。 */
        public Builder initialAmmo(int initialAmmo) {
            this.initialAmmo = initialAmmo;
            return this;
        }

        /** 标记为默认初始武器（免费、对所有玩家可用）。 */
        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public Builder itemFactory(ItemFactory itemFactory) {
            this.itemFactory = itemFactory;
            return this;
        }

        /** 物品 NBT 序列化字符串（SNBT），用于数据驱动还原（含 TaCZ 枪械）。 */
        public Builder itemSnbt(String itemSnbt) {
            this.itemSnbt = itemSnbt;
            return this;
        }

        public LoadoutItem build() {
            return new LoadoutItem(this);
        }
    }
}
