package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import org.shee33.act0.arcade.client.screen.GunModScreen;
import org.shee33.act0.arcade.network.ModificationDto;

/**
 * 客户端"改装上下文"缓存：保存服务端针对当前正在改装的武器下发的
 * {@link ModificationDto}（兼容/已安装信息）。改装界面据此刷新。
 */
public final class ClientModification {

    private static volatile ModificationDto current;

    private ClientModification() {
    }

    /** 接收服务端下发的改装数据；若改装界面已打开，则刷新其内容。 */
    public static void accept(ModificationDto dto) {
        current = dto;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GunModScreen screen) {
            screen.onData(dto);
        }
    }

    /** 当前改装数据（可能为 {@code null}）。 */
    public static ModificationDto current() {
        return current;
    }
}
