package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import org.shee33.act0.arcade.client.screen.LoadoutQuickSelectScreen;
import org.shee33.act0.arcade.client.screen.LoadoutScreen;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;

/**
 * 客户端专用：打开配装编辑界面。仅在客户端通过 {@code DistExecutor} 调用，避免在服务端加载客户端类。
 */
public final class ClientLoadoutScreenOpener {

    private ClientLoadoutScreenOpener() {
    }

    public static void open(Loadout loadout) {
        open(LoadoutSet.single(loadout));
    }

    public static void open(LoadoutSet set) {
        Minecraft.getInstance().setScreen(new LoadoutScreen(set));
    }

    public static void openQuickSelect(LoadoutSet set) {
        Minecraft.getInstance().setScreen(new LoadoutQuickSelectScreen(set));
    }
}
