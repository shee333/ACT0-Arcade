package org.shee33.act0.arcade.loadout;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 解析一份配装在某规则集下的结果：逐槽位说明最终是否发放、以及未发放的原因。
 *
 * <p>由 {@link LoadoutRuleEngine} 产出，供应用层（{@code LoadoutApplier}）按 {@link Outcome#GRANTED}
 * 发放装备，并供 GUI 展示禁用/锁定原因。本类不依赖 Minecraft 运行时。
 */
public final class LoadoutResolution {

    /** 单个槽位的解析结果。 */
    public enum Outcome {
        /** 已发放：装备解析成功且通过所有过滤。 */
        GRANTED,
        /** 该槽位未选择任何装备。 */
        EMPTY,
        /** 被规则集禁用（如街机模式下的兵种装置槽）。 */
        FILTERED_BY_RULESET,
        /** 被职业限制过滤（该装备不对当前职业开放）。 */
        FILTERED_BY_CLASS,
        /** 玩家等级未达解锁要求。 */
        LOCKED,
        /** 选择的装备 key 在注册表中不存在。 */
        UNKNOWN_ITEM
    }

    /** 单槽位解析详情。 */
    public static final class SlotResolution {
        private final Outcome outcome;
        private final LoadoutItem item; // 仅 GRANTED 时非空

        SlotResolution(Outcome outcome, LoadoutItem item) {
            this.outcome = outcome;
            this.item = item;
        }

        public Outcome outcome() {
            return outcome;
        }

        /** 发放的装备（仅 {@link Outcome#GRANTED} 时存在）。 */
        public Optional<LoadoutItem> item() {
            return Optional.ofNullable(item);
        }

        public boolean isGranted() {
            return outcome == Outcome.GRANTED;
        }
    }

    private final Map<LoadoutSlot, SlotResolution> bySlot;

    LoadoutResolution(Map<LoadoutSlot, SlotResolution> bySlot) {
        this.bySlot = new EnumMap<>(Objects.requireNonNull(bySlot, "bySlot"));
    }

    /** 取某槽位的解析详情。 */
    public SlotResolution slot(LoadoutSlot slot) {
        return bySlot.get(slot);
    }

    /** 全部槽位解析（不可变视图）。 */
    public Map<LoadoutSlot, SlotResolution> bySlot() {
        return Collections.unmodifiableMap(bySlot);
    }
}
