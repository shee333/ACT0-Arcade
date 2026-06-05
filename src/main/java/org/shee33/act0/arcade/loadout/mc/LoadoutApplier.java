package org.shee33.act0.arcade.loadout.mc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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

    /** 元子弹（弹药盒）物品 id：发放枪械时按 {@link LoadoutItem#initialAmmo()} 配套给予。 */
    private static final ResourceLocation META_AMMO_ID = new ResourceLocation("tacz", "ammo_box");

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
        int totalAmmo = 0;
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
            int hotbar = slot.hotbarIndex();
            if (hotbar >= 0 && hotbar < player.getInventory().items.size()) {
                player.getInventory().setItem(hotbar, stack);
            }
            totalAmmo += item.initialAmmo();
        }
        grantMetaAmmo(player, totalAmmo);
        player.getInventory().setChanged();
        return resolution;
    }

    /** 把元子弹（弹药盒）按总量发放到玩家背包；数量为 0 时跳过。 */
    private void grantMetaAmmo(ServerPlayer player, int count) {
        if (count <= 0) {
            return;
        }
        Item ammo = ForgeRegistries.ITEMS.getValue(META_AMMO_ID);
        if (ammo == null) {
            return;
        }
        int remaining = Math.min(count, 64 * 8); // 上限保护
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack box = new ItemStack(ammo, give);
            if (!player.getInventory().add(box) && box.getCount() > 0) {
                player.drop(box, false);
            }
            remaining -= give;
        }
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
