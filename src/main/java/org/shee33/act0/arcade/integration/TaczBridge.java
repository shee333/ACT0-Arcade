package org.shee33.act0.arcade.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * TaCZ（Timeless and Classics Zero）枪械配件 API 的<b>反射软依赖</b>桥。
 *
 * <p>本模组<b>不在编译期依赖</b> TaCZ；运行时若检测到 TaCZ（{@code com.tacz.guns.api.item.IGun}
 * 等类可加载），则通过反射调用其配件相关方法，实现"改装"功能；否则 {@link #isAvailable()} 返回
 * {@code false}，上层据此隐藏改装入口并禁用相关指令，其余玩法不受影响。
 *
 * <p>所有方法均为静态、空安全；任意反射异常都被吞掉并以保守结果返回，绝不向上抛出，
 * 以避免单个集成问题影响主玩法。
 *
 * <p>线程模型：仅在服务器主线程 / 客户端渲染线程（仅图标构建）调用。
 */
public final class TaczBridge {

    private static final boolean AVAILABLE;

    private static Class<?> iGunClass;
    private static Class<?> iAttachmentClass;
    private static Class<?> attachmentTypeClass;

    private static Method getIGunOrNull;
    private static Method gunGetGunId;
    private static Method gunGetAttachment;
    private static Method gunInstallAttachment;
    private static Method gunUnloadAttachment;
    private static Method gunAllowAttachment;
    private static Method gunAllowAttachmentType;

    private static Method getIAttachmentOrNull;
    private static Method attGetType;
    private static Method attGetAttachmentId;

    /** TaCZ AttachmentType 枚举常量缓存：本枚举名 → TaCZ 常量对象。 */
    private static final Map<AttachmentSlotType, Object> TYPE_CONSTANTS = new HashMap<>();

    static {
        boolean ok = false;
        try {
            iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            iAttachmentClass = Class.forName("com.tacz.guns.api.item.IAttachment");
            attachmentTypeClass = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");

            getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            gunGetGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            gunGetAttachment = iGunClass.getMethod("getAttachment", ItemStack.class, attachmentTypeClass);
            gunInstallAttachment = iGunClass.getMethod("installAttachment", ItemStack.class, ItemStack.class);
            gunUnloadAttachment = iGunClass.getMethod("unloadAttachment", ItemStack.class, attachmentTypeClass);
            gunAllowAttachment = iGunClass.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
            gunAllowAttachmentType = iGunClass.getMethod("allowAttachmentType", ItemStack.class, attachmentTypeClass);

            getIAttachmentOrNull = iAttachmentClass.getMethod("getIAttachmentOrNull", ItemStack.class);
            attGetType = iAttachmentClass.getMethod("getType", ItemStack.class);
            attGetAttachmentId = iAttachmentClass.getMethod("getAttachmentId", ItemStack.class);

            // 预解析 TaCZ AttachmentType 枚举常量（按名映射）
            for (AttachmentSlotType slot : AttachmentSlotType.values()) {
                Object constant = enumConstant(attachmentTypeClass, slot.taczEnumName());
                if (constant != null) {
                    TYPE_CONSTANTS.put(slot, constant);
                }
            }
            ok = !TYPE_CONSTANTS.isEmpty();
        } catch (Throwable ignored) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    private TaczBridge() {
    }

    /** 运行时是否存在 TaCZ 配件 API。 */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** 给定物品是否为 TaCZ 枪械。 */
    public static boolean isGun(ItemStack stack) {
        if (!AVAILABLE || stack == null || stack.isEmpty()) {
            return false;
        }
        return iGun(stack) != null;
    }

    /** 枪械 ID（如 {@code tacz:ak47}）；非枪或不可用返回 {@code null}。 */
    public static String gunId(ItemStack stack) {
        Object iGun = iGun(stack);
        if (iGun == null) {
            return null;
        }
        try {
            Object id = gunGetGunId.invoke(iGun, stack);
            return id != null ? id.toString() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 该枪是否允许某类型配件槽。 */
    public static boolean allowAttachmentType(ItemStack gun, AttachmentSlotType slot) {
        Object iGun = iGun(gun);
        Object type = TYPE_CONSTANTS.get(slot);
        if (iGun == null || type == null) {
            return false;
        }
        try {
            return (boolean) gunAllowAttachmentType.invoke(iGun, gun, type);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 该枪是否允许装配某具体配件物品。 */
    public static boolean allowAttachment(ItemStack gun, ItemStack attachment) {
        Object iGun = iGun(gun);
        if (iGun == null || attachment == null || attachment.isEmpty()) {
            return false;
        }
        try {
            return (boolean) gunAllowAttachment.invoke(iGun, gun, attachment);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 把配件安装到枪上（原地修改 {@code gun} 的 NBT）。返回是否成功。 */
    public static boolean install(ItemStack gun, ItemStack attachment) {
        Object iGun = iGun(gun);
        if (iGun == null || attachment == null || attachment.isEmpty()) {
            return false;
        }
        try {
            gunInstallAttachment.invoke(iGun, gun, attachment);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 卸下枪上某类型配件（原地修改 {@code gun} 的 NBT）。返回是否成功。 */
    public static boolean unload(ItemStack gun, AttachmentSlotType slot) {
        Object iGun = iGun(gun);
        Object type = TYPE_CONSTANTS.get(slot);
        if (iGun == null || type == null) {
            return false;
        }
        try {
            gunUnloadAttachment.invoke(iGun, gun, type);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 枪上某类型当前安装的配件物品（空表示无）。 */
    public static ItemStack installedAttachment(ItemStack gun, AttachmentSlotType slot) {
        Object iGun = iGun(gun);
        Object type = TYPE_CONSTANTS.get(slot);
        if (iGun == null || type == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object result = gunGetAttachment.invoke(iGun, gun, type);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }

    /** 配件物品的槽位类型；非配件或不可用返回 {@code null}。 */
    public static AttachmentSlotType attachmentType(ItemStack attachment) {
        Object iAtt = iAttachment(attachment);
        if (iAtt == null) {
            return null;
        }
        try {
            Object type = attGetType.invoke(iAtt, attachment);
            return type != null ? AttachmentSlotType.byName(((Enum<?>) type).name()) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 配件物品的 TaCZ 配件 ID（如 {@code tacz:scope_acog_ta31}）；非配件返回 {@code null}。 */
    public static String attachmentId(ItemStack attachment) {
        Object iAtt = iAttachment(attachment);
        if (iAtt == null) {
            return null;
        }
        try {
            Object id = attGetAttachmentId.invoke(iAtt, attachment);
            return id instanceof ResourceLocation rl ? rl.toString() : (id != null ? id.toString() : null);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 给定物品是否为 TaCZ 配件。 */
    public static boolean isAttachment(ItemStack stack) {
        return iAttachment(stack) != null;
    }

    // ---- 内部 ----

    private static Object iGun(ItemStack stack) {
        if (!AVAILABLE || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return getIGunOrNull.invoke(null, stack);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Object iAttachment(ItemStack stack) {
        if (!AVAILABLE || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return getIAttachmentOrNull.invoke(null, stack);
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        try {
            return Enum.valueOf((Class<Enum>) enumClass, name);
        } catch (Throwable e) {
            return null;
        }
    }
}
