package org.shee33.act0.arcade.client;

import java.util.List;

/**
 * 客户端侧边栏状态持有者：缓存服务端下发的对局侧边栏标题与行文本，供 {@link ArcadeHudOverlay} 自绘。
 *
 * <p>仅在客户端调用。自绘侧边栏<b>不显示原版右侧红色分值</b>，所有信息都已包含在行文本中。
 */
public final class ClientSidebar {

    private static volatile boolean show = false;
    private static volatile String title = "";
    private static volatile List<String> lines = List.of();

    private ClientSidebar() {
    }

    /** 处理服务端下发：更新缓存。 */
    public static void accept(boolean show, String title, List<String> lines) {
        ClientSidebar.show = show;
        ClientSidebar.title = title != null ? title : "";
        ClientSidebar.lines = lines != null ? lines : List.of();
    }

    public static boolean isShown() {
        return show;
    }

    public static String title() {
        return title;
    }

    public static List<String> lines() {
        return lines;
    }
}
