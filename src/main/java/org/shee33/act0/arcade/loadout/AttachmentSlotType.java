package org.shee33.act0.arcade.loadout;

/**
 * 枪械配件的槽位类型，与 TaCZ 的 {@code com.tacz.guns.api.item.attachment.AttachmentType} 一一对应。
 *
 * <p>本枚举为 MC-free 的纯数据：以 {@link #taczName()} 与 TaCZ 的序列化名（如 {@code scope}）对齐，
 * 应用层经反射桥 {@code TaczBridge} 把它映射到 TaCZ 的枚举常量。
 */
public enum AttachmentSlotType {

    SCOPE("瞄具", "scope"),
    MUZZLE("枪口", "muzzle"),
    STOCK("枪托", "stock"),
    GRIP("握把", "grip"),
    LASER("激光", "laser"),
    EXTENDED_MAG("扩容弹匣", "extended_mag");

    private final String displayName;
    private final String taczName;

    AttachmentSlotType(String displayName, String taczName) {
        this.displayName = displayName;
        this.taczName = taczName;
    }

    /** 中文显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 与 TaCZ {@code AttachmentType} 对应的序列化名（小写）。 */
    public String taczName() {
        return taczName;
    }

    /** TaCZ 枚举常量名（大写，等同本枚举的 {@link #name()}）。 */
    public String taczEnumName() {
        return name();
    }

    /** 按枚举名（大小写不敏感）解析；未知返回 {@code null}。 */
    public static AttachmentSlotType byName(String raw) {
        if (raw == null) {
            return null;
        }
        for (AttachmentSlotType t : values()) {
            if (t.name().equalsIgnoreCase(raw) || t.taczName.equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return null;
    }
}
