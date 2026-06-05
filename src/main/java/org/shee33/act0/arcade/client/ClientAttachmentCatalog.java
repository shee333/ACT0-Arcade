package org.shee33.act0.arcade.client;

import org.shee33.act0.arcade.loadout.AttachmentRegistry;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO.AttachmentEntryDto;

import java.util.List;

/**
 * 客户端配件目录缓存：保存服务端通过 {@code SyncAttachmentCatalogPacket} 下发的配件定义，
 * 供改装界面渲染配件名称 / 价格 / 图标。
 */
public final class ClientAttachmentCatalog {

    private static final AttachmentRegistry REGISTRY = new AttachmentRegistry();

    private ClientAttachmentCatalog() {
    }

    /** 接收服务端下发的配件目录，整体替换。 */
    public static void accept(List<AttachmentEntryDto> entries) {
        REGISTRY.clear();
        if (entries != null) {
            for (AttachmentEntryDto dto : entries) {
                AttachmentCatalogIO.registerEntry(REGISTRY, dto);
            }
        }
    }

    /** 客户端配件注册表（只读使用）。 */
    public static AttachmentRegistry registry() {
        return REGISTRY;
    }
}
