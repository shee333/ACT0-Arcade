package org.shee33.act0.arcade.network;

import java.util.ArrayList;
import java.util.List;

/**
 * 某玩家对某把枪的"改装"上下文数据传输对象（S→C）。
 *
 * <p>仅承载与该枪相关的、客户端缓存里没有的动态信息：每个<b>该枪允许</b>的配件槽位、
 * 以及该槽位下与该枪<b>兼容</b>的配件 key 列表与当前已安装的配件 key。
 * 配件的名称 / 价格 / 图标由客户端 {@code ClientAttachmentCatalog} 提供，
 * 解锁状态由 {@code ClientUnlocks} 提供。
 */
public final class ModificationDto {

    public String weaponKey;
    public List<SlotDto> slots = new ArrayList<>();

    public ModificationDto() {
    }

    public ModificationDto(String weaponKey) {
        this.weaponKey = weaponKey;
    }

    /** 单个槽位的兼容/安装信息。 */
    public static final class SlotDto {
        /** 槽位枚举名（{@code AttachmentSlotType.name()}）。 */
        public String slot;
        /** 当前已安装的配件 key；空串表示未安装。 */
        public String installedKey = "";
        /** 该槽位下与本枪兼容的配件 key 列表。 */
        public List<String> compatibleKeys = new ArrayList<>();

        public SlotDto() {
        }

        public SlotDto(String slot) {
            this.slot = slot;
        }
    }
}
