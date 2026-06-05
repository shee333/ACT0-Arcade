package org.shee33.act0.arcade.loadout;

import java.util.Objects;
import java.util.Set;

/**
 * 一件枪械<b>配件</b>的定义（与玩家无关）。
 *
 * <p>与 {@link LoadoutItem}（武器）平行：每件配件由唯一 {@code key} 标识，声明其 TaCZ 配件 ID、
 * 槽位类型、解锁价格与显示名。具体的 Minecraft 物品由其 {@link #itemSnbt()} 在应用层还原，
 * 从而保持本类<b>不依赖 Minecraft 运行时、可单测</b>。
 *
 * <p>配件与武器共用同一套货币解锁机制（{@code ArcadePlayerUnlocks}），key 命名空间彼此不冲突
 * （配件 key 统一以 {@code attach.} 前缀，武器以 {@code gun.}/物品注册名）。
 */
public final class ArcadeAttachment {

    private final String key;
    private final String attachmentId;
    private final String displayName;
    private final AttachmentSlotType slot;
    private final int price;
    private final boolean isDefault;
    private final String itemSnbt;

    private ArcadeAttachment(Builder b) {
        this.key = Objects.requireNonNull(b.key, "key");
        this.attachmentId = Objects.requireNonNull(b.attachmentId, "attachmentId");
        this.displayName = b.displayName != null ? b.displayName : b.key;
        this.slot = Objects.requireNonNull(b.slot, "slot");
        this.price = Math.max(0, b.price);
        this.isDefault = b.isDefault;
        this.itemSnbt = b.itemSnbt;
    }

    /** 唯一标识键（如 {@code attach.tacz.scope_acog_ta31}）。 */
    public String key() {
        return key;
    }

    /** TaCZ 配件 ID（如 {@code tacz:scope_acog_ta31}）。 */
    public String attachmentId() {
        return attachmentId;
    }

    /** 显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 槽位类型。 */
    public AttachmentSlotType slot() {
        return slot;
    }

    /** 解锁所需货币（默认配件为 0）。 */
    public int price() {
        return price;
    }

    /** 是否为默认配件（对所有玩家免费可用）。 */
    public boolean isDefault() {
        return isDefault;
    }

    /** 配件物品的 NBT 序列化字符串（SNBT），用于还原 ItemStack（图标渲染与安装）。 */
    public String itemSnbt() {
        return itemSnbt;
    }

    /** 该配件对持有给定已解锁集合的玩家是否可用：默认配件恒可用，否则需在集合内。 */
    public boolean isUnlockedBy(Set<String> unlockedKeys) {
        return isDefault || (unlockedKeys != null && unlockedKeys.contains(key));
    }

    public static Builder builder(String key, String attachmentId, AttachmentSlotType slot) {
        return new Builder(key, attachmentId, slot);
    }

    /** {@link ArcadeAttachment} 构建器。 */
    public static final class Builder {
        private final String key;
        private final String attachmentId;
        private final AttachmentSlotType slot;
        private String displayName;
        private int price;
        private boolean isDefault;
        private String itemSnbt;

        private Builder(String key, String attachmentId, AttachmentSlotType slot) {
            this.key = key;
            this.attachmentId = attachmentId;
            this.slot = slot;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder price(int price) {
            this.price = price;
            return this;
        }

        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public Builder itemSnbt(String itemSnbt) {
            this.itemSnbt = itemSnbt;
            return this;
        }

        public ArcadeAttachment build() {
            return new ArcadeAttachment(this);
        }
    }
}
