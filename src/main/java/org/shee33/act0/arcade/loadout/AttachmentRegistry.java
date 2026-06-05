package org.shee33.act0.arcade.loadout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 配件目录注册表：所有可用 {@link ArcadeAttachment} 定义的集合，按 key 索引。
 *
 * <p>与 {@link LoadoutRegistry}（武器）平行，作为配件系统的数据源，供改装 GUI 列出可选配件、
 * 供服务端按 key 解析。不依赖 Minecraft 运行时，可单测。
 */
public final class AttachmentRegistry {

    private final Map<String, ArcadeAttachment> byKey = new LinkedHashMap<>();

    /** 注册一件配件定义。key 重复时抛出。 */
    public void register(ArcadeAttachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        ArcadeAttachment prev = byKey.putIfAbsent(attachment.key(), attachment);
        if (prev != null) {
            throw new IllegalStateException("重复注册配件 key: " + attachment.key());
        }
    }

    /** 按 key 查找配件定义。 */
    public Optional<ArcadeAttachment> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key));
    }

    /** 按 TaCZ 配件 ID 查找定义。 */
    public Optional<ArcadeAttachment> findByAttachmentId(String attachmentId) {
        if (attachmentId == null) {
            return Optional.empty();
        }
        for (ArcadeAttachment a : byKey.values()) {
            if (attachmentId.equals(a.attachmentId())) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    /** 列出某槽位类型下的全部配件（注册顺序）。 */
    public List<ArcadeAttachment> itemsForSlot(AttachmentSlotType slot) {
        List<ArcadeAttachment> result = new ArrayList<>();
        if (slot == null) {
            return result;
        }
        for (ArcadeAttachment a : byKey.values()) {
            if (a.slot() == slot) {
                result.add(a);
            }
        }
        return result;
    }

    /** 全部已注册配件（不可变视图）。 */
    public Map<String, ArcadeAttachment> all() {
        return Collections.unmodifiableMap(byKey);
    }

    /** 清空全部配件定义（用于从配置重新加载目录）。 */
    public void clear() {
        byKey.clear();
    }
}
