package org.shee33.act0.arcade.loadout.mc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.integration.TaczBridge;
import org.shee33.act0.arcade.loadout.ArcadeAttachment;
import org.shee33.act0.arcade.loadout.AttachmentRegistry;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.network.ModificationDto;
import org.shee33.act0.arcade.storage.ArcadePlayerAttachments;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 枪械"改装"服务端核心逻辑（Minecraft 侧）。
 *
 * <p>统一处理：构建改装上下文（兼容性 / 已安装）、提交单槽改装（校验解锁与兼容、写入玩家私有选择）、
 * 以及在发枪时把玩家已选且仍合法的配件装到枪上。所有 TaCZ 交互经 {@link TaczBridge} 反射软依赖完成；
 * TaCZ 缺失时本服务整体降级为"无可改装槽位"。
 */
public final class GunModService {

    private final LoadoutRegistry registry;
    private final AttachmentRegistry attachments;

    public GunModService(LoadoutRegistry registry, AttachmentRegistry attachments) {
        this.registry = registry;
        this.attachments = attachments;
    }

    /** 改装提交的结果。 */
    public enum Result {
        OK("已更新改装"),
        TACZ_ABSENT("当前服务器未启用枪械改装"),
        UNKNOWN_WEAPON("未知武器"),
        NOT_A_GUN("该武器不支持改装"),
        WEAPON_LOCKED("尚未解锁该武器"),
        UNKNOWN_ATTACHMENT("未知配件"),
        ATTACHMENT_LOCKED("尚未解锁该配件"),
        SLOT_NOT_ALLOWED("该武器不支持此配件槽"),
        INCOMPATIBLE("该配件与此武器不兼容");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public boolean ok() {
            return this == OK;
        }
    }

    /** 某武器是否可改装（是 TaCZ 枪且 TaCZ 可用）。 */
    public boolean isModifiable(String weaponKey) {
        if (!TaczBridge.isAvailable()) {
            return false;
        }
        ItemStack base = baseGun(weaponKey);
        return !base.isEmpty() && TaczBridge.isGun(base);
    }

    /**
     * 构建某玩家对某武器的改装上下文（兼容/已安装），供下发客户端。
     *
     * @return 改装数据；若不可改装则 {@code slots} 为空。
     */
    public ModificationDto buildModificationData(ServerPlayer player, String weaponKey) {
        ModificationDto dto = new ModificationDto(weaponKey);
        if (!TaczBridge.isAvailable()) {
            return dto;
        }
        ItemStack base = baseGun(weaponKey);
        if (base.isEmpty() || !TaczBridge.isGun(base)) {
            return dto;
        }
        Map<String, String> selections =
                ArcadePlayerAttachments.get(player.getServer()).selections(player.getUUID(), weaponKey);
        for (AttachmentSlotType slot : AttachmentSlotType.values()) {
            if (!TaczBridge.allowAttachmentType(base, slot)) {
                continue;
            }
            ModificationDto.SlotDto slotDto = new ModificationDto.SlotDto(slot.name());
            for (ArcadeAttachment att : attachments.itemsForSlot(slot)) {
                ItemStack attStack = LoadoutApplier.fromSnbt(att.itemSnbt());
                if (attStack.isEmpty() || !TaczBridge.allowAttachment(base, attStack)) {
                    continue;
                }
                slotDto.compatibleKeys.add(att.key());
            }
            String installed = selections.get(slot.name());
            if (installed != null && slotDto.compatibleKeys.contains(installed)) {
                slotDto.installedKey = installed;
            }
            dto.slots.add(slotDto);
        }
        return dto;
    }

    /**
     * 提交一次改装：在某武器某槽位安装 / 卸下配件，写入玩家私有选择。
     *
     * @param attachmentKey 目标配件 key；{@code null} 表示卸下该槽位
     */
    public Result applyModification(ServerPlayer player, String weaponKey, AttachmentSlotType slot, String attachmentKey) {
        if (!TaczBridge.isAvailable()) {
            return Result.TACZ_ABSENT;
        }
        LoadoutItem item = registry.find(weaponKey).orElse(null);
        if (item == null) {
            return Result.UNKNOWN_WEAPON;
        }
        ItemStack base = baseGun(weaponKey);
        if (base.isEmpty() || !TaczBridge.isGun(base)) {
            return Result.NOT_A_GUN;
        }
        Set<String> unlocked = ArcadePlayerUnlocks.get(player.getServer()).unlocked(player.getUUID());
        if (!item.isDefault() && !unlocked.contains(weaponKey)) {
            return Result.WEAPON_LOCKED;
        }
        if (!TaczBridge.allowAttachmentType(base, slot)) {
            return Result.SLOT_NOT_ALLOWED;
        }
        ArcadePlayerAttachments store = ArcadePlayerAttachments.get(player.getServer());
        if (attachmentKey == null) {
            store.setSelection(player.getUUID(), weaponKey, slot, null);
            return Result.OK;
        }
        ArcadeAttachment att = attachments.find(attachmentKey).orElse(null);
        if (att == null || att.slot() != slot) {
            return Result.UNKNOWN_ATTACHMENT;
        }
        if (!att.isUnlockedBy(unlocked)) {
            return Result.ATTACHMENT_LOCKED;
        }
        ItemStack attStack = LoadoutApplier.fromSnbt(att.itemSnbt());
        if (attStack.isEmpty() || !TaczBridge.allowAttachment(base, attStack)) {
            return Result.INCOMPATIBLE;
        }
        store.setSelection(player.getUUID(), weaponKey, slot, attachmentKey);
        return Result.OK;
    }

    /**
     * 把玩家对某武器已选、且仍解锁且兼容的配件装到 {@code gun}（原地修改其 NBT）。
     * 发枪流程在构建好基础枪后调用。
     */
    public void installPlayerSelections(ServerPlayer player, String weaponKey, ItemStack gun) {
        if (!TaczBridge.isAvailable() || gun == null || gun.isEmpty() || !TaczBridge.isGun(gun)) {
            return;
        }
        UUID id = player.getUUID();
        Map<String, String> selections = ArcadePlayerAttachments.get(player.getServer()).selections(id, weaponKey);
        if (selections.isEmpty()) {
            return;
        }
        Set<String> unlocked = ArcadePlayerUnlocks.get(player.getServer()).unlocked(id);
        for (Map.Entry<String, String> e : selections.entrySet()) {
            AttachmentSlotType slot = AttachmentSlotType.byName(e.getKey());
            if (slot == null || !TaczBridge.allowAttachmentType(gun, slot)) {
                continue;
            }
            ArcadeAttachment att = attachments.find(e.getValue()).orElse(null);
            if (att == null || !att.isUnlockedBy(unlocked)) {
                continue;
            }
            ItemStack attStack = LoadoutApplier.fromSnbt(att.itemSnbt());
            if (attStack.isEmpty() || !TaczBridge.allowAttachment(gun, attStack)) {
                continue;
            }
            TaczBridge.install(gun, attStack);
        }
    }

    /** 由某武器 key 构建一把基础枪（未改装）的 ItemStack；无 SNBT 返回空。 */
    private ItemStack baseGun(String weaponKey) {
        LoadoutItem item = registry.find(weaponKey).orElse(null);
        if (item == null || item.itemSnbt() == null || item.itemSnbt().isBlank()) {
            return ItemStack.EMPTY;
        }
        return LoadoutApplier.fromSnbt(item.itemSnbt());
    }
}
