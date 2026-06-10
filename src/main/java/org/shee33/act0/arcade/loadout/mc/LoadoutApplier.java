package org.shee33.act0.arcade.loadout.mc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutResolution;
import org.shee33.act0.arcade.loadout.LoadoutRuleEngine;
import org.shee33.act0.arcade.loadout.LoadoutRuleset;
import org.shee33.act0.arcade.loadout.LoadoutSlot;

import java.util.Map;
import java.util.Set;

/**
 * 配装应用层（Minecraft 侧）：把一份 {@link Loadout} 在某 {@link LoadoutRuleset} 下解析后，
 * 把每个 {@code GRANTED} 槽位的装备发到玩家快捷栏对应位置。
 *
 * <p>这是核心规则逻辑（MC-free 的 {@link LoadoutRuleEngine}）与 Minecraft 运行时之间唯一的桥。
 * 装备物品优先以 SNBT（物品 NBT 字符串）反序列化为 {@link ItemStack}，从而天然支持 TaCZ 枪械
 * （整把枪连配件都在 NBT 内），且<b>无需直接依赖 TaCZ API</b>。
 *
 * <p>"街机无缝禁用兵种道具"完全由 {@link LoadoutRuleset} 在解析阶段完成：被 {@code FILTERED_BY_RULESET}
 * 的槽位不会进入发放循环，玩家配装数据本身不被改动。
 */
public final class LoadoutApplier {

    /** TaCZ 虚拟弹药 NBT 键：换弹时消耗此值而非弹药物品。见 tacwiki dummy_ammo。 */
    public static final String DUMMY_AMMO_KEY = "DummyAmmo";
    /** ACT0 自定义键：记录该枪初始虚拟备弹容量，供补给箱补满时还原。 */
    public static final String AMMO_CAP_KEY = "Act0AmmoCap";
    /** TaCZ 枪械 NBT 标识键（用于判断一个 ItemStack 是否为 TaCZ 枪）。 */
    private static final String TACZ_GUN_ID_KEY = "GunId";

    private final LoadoutRegistry registry;
    private final GunModService gunMod;

    public LoadoutApplier(LoadoutRegistry registry) {
        this(registry, null);
    }

    public LoadoutApplier(LoadoutRegistry registry, GunModService gunMod) {
        this.registry = registry;
        this.gunMod = gunMod;
    }

    /**
     * 解析并发放配装到玩家。
     *
     * @param player      目标玩家
     * @param loadout     玩家配装选择（只读）
     * @param ruleset     当前模式规则集（{@code FULL}/{@code ARCADE}）
     * @param unlocked    玩家已解锁的武器 key 集合（默认武器无需在内）
     * @param clearFirst  是否先清空背包（对局开始/复活通常为 {@code true}）
     * @return 本次解析结果（供上层做提示/统计）
     */
    public LoadoutResolution apply(ServerPlayer player,
                                   Loadout loadout,
                                   LoadoutRuleset ruleset,
                                   Set<String> unlocked,
                                   boolean clearFirst) {
        LoadoutResolution resolution = LoadoutRuleEngine.resolve(loadout, ruleset, registry, unlocked);
        if (clearFirst) {
            player.getInventory().clearContent();
        }
        for (Map.Entry<LoadoutSlot, LoadoutResolution.SlotResolution> entry : resolution.bySlot().entrySet()) {
            LoadoutSlot slot = entry.getKey();
            LoadoutResolution.SlotResolution slotResolution = entry.getValue();
            if (!slotResolution.isGranted()) {
                continue;
            }
            LoadoutItem item = slotResolution.item().orElse(null);
            if (item == null) {
                continue;
            }
            ItemStack stack = buildStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            if (gunMod != null) {
                gunMod.installPlayerSelections(player, item.key(), stack);
            }
            // TaCZ 枪械：用虚拟弹药（DummyAmmo），换弹直接消耗该值，无需携带弹药盒物品。
            if (item.initialAmmo() > 0) {
                setDummyAmmo(stack, item.initialAmmo());
            }
            int hotbar = slot.hotbarIndex();
            if (hotbar >= 0 && hotbar < player.getInventory().items.size()) {
                player.getInventory().setItem(hotbar, stack);
            }
        }
        player.getInventory().setChanged();
        return resolution;
    }

    /** 穿戴玩家全配装共享服饰。 */
    public void applyApparel(ServerPlayer player,
                             ApparelSelection selection,
                             ApparelRegistry apparel,
                             Set<String> unlocked) {
        if (player == null || selection == null || apparel == null) {
            return;
        }
        for (ApparelSlot slot : ApparelSlot.values()) {
            String key = selection.selectedKey(slot).orElse(null);
            ApparelItem item = key == null ? null : apparel.find(key).orElse(null);
            if (item == null || item.slot() != slot || !item.isUnlockedBy(unlocked)) {
                player.setItemSlot(equipmentSlot(slot), ItemStack.EMPTY);
                continue;
            }
            ItemStack stack = fromSnbt(item.itemSnbt());
            player.setItemSlot(equipmentSlot(slot), stack);
        }
        player.getInventory().setChanged();
    }

    private static EquipmentSlot equipmentSlot(ApparelSlot slot) {
        return switch (slot) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
        };
    }

    /**
     * 为一把 TaCZ 枪设置虚拟弹药与容量记录。非枪械物品也只是写两个标签，无副作用。
     */
    public static void setDummyAmmo(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(DUMMY_AMMO_KEY, amount);
        tag.putInt(AMMO_CAP_KEY, amount);
    }

    /** 该 ItemStack 是否为 TaCZ 枪（含 {@code GunId} 标签）。 */
    public static boolean isTaczGun(ItemStack stack) {
        return stack != null && stack.hasTag() && stack.getTag().contains(TACZ_GUN_ID_KEY);
    }

    /**
     * 把玩家背包内所有 TaCZ 枪的虚拟弹药补满（补给箱拾取时调用）。
     *
     * @return 实际补给的枪械数量
     */
    public static int refillAllGuns(ServerPlayer player) {
        int refilled = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!isTaczGun(stack)) {
                continue;
            }
            CompoundTag tag = stack.getOrCreateTag();
            int cap = tag.contains(AMMO_CAP_KEY) ? tag.getInt(AMMO_CAP_KEY) : 0;
            if (cap <= 0 && tag.contains(DUMMY_AMMO_KEY)) {
                cap = Math.max(tag.getInt(DUMMY_AMMO_KEY), 60);
            }
            if (cap <= 0) {
                cap = 60;
            }
            tag.putInt(DUMMY_AMMO_KEY, cap);
            tag.putInt(AMMO_CAP_KEY, cap);
            refilled++;
        }
        if (refilled > 0) {
            player.getInventory().setChanged();
        }
        return refilled;
    }

    /**
     * 把一件装备定义构建成 {@link ItemStack}：优先用 SNBT 反序列化，其次用编程式工厂。
     */
    private ItemStack buildStack(LoadoutItem item) {
        String snbt = item.itemSnbt();
        if (snbt != null && !snbt.isBlank()) {
            ItemStack parsed = deserialize(snbt);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        Object created = item.createItem();
        if (created instanceof ItemStack itemStack) {
            return itemStack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 把物品 NBT 字符串（SNBT）解析为 {@link ItemStack}；解析失败返回 {@link ItemStack#EMPTY}。
     */
    private ItemStack deserialize(String snbt) {
        return fromSnbt(snbt);
    }

    /**
     * 把物品 NBT 字符串（SNBT）解析为 {@link ItemStack}；解析失败或为空返回 {@link ItemStack#EMPTY}。
     *
     * <p>客户端 GUI 渲染装备图标时复用本方法，保证图标与服务端实际发放的物品一致。
     */
    public static ItemStack fromSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.of(tag);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
