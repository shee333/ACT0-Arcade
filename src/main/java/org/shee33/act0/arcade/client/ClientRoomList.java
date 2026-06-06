package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import org.shee33.act0.arcade.client.screen.RoomBrowserScreen;
import org.shee33.act0.arcade.network.RoomDto;

import java.util.List;

/**
 * 客户端房间列表持有者：缓存服务端下发的最新房间数据，供 {@link RoomBrowserScreen} 读取。
 *
 * <p>收到 {@code SyncRoomListPacket} 时：界面已打开则就地刷新；仅当 {@code open=true}（玩家主动
 * 请求开屏）且界面未打开时才打开它。广播/定时刷新（{@code open=false}）不会弹出界面，避免打扰其他玩家。
 */
public final class ClientRoomList {

    private static volatile boolean canManage;
    private static volatile List<String> arenaIds = List.of();
    private static volatile List<RoomDto> rooms = List.of();

    private ClientRoomList() {
    }

    public static boolean canManage() {
        return canManage;
    }

    public static List<String> arenaIds() {
        return arenaIds;
    }

    public static List<RoomDto> rooms() {
        return rooms;
    }

    /** 处理服务端下发：更新缓存并刷新/打开浏览界面。 */
    public static void accept(boolean canManage, boolean open, List<String> arenaIds, List<RoomDto> rooms) {
        ClientRoomList.canManage = canManage;
        ClientRoomList.arenaIds = arenaIds != null ? arenaIds : List.of();
        ClientRoomList.rooms = rooms != null ? rooms : List.of();

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RoomBrowserScreen browser) {
            browser.onRoomsUpdated();
        } else if (open) {
            mc.setScreen(new RoomBrowserScreen());
        }
    }

    public static void closeBrowser() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RoomBrowserScreen) {
            mc.setScreen(null);
        }
    }
}
