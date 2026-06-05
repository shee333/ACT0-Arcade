package org.shee33.act0.arcade.match;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SyncSidebarPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 每对局独立的侧边栏：把标题与各行文本经 {@link SyncSidebarPacket} 直接发给参战玩家，由客户端自绘 HUD。
 *
 * <p><b>不使用原版计分板侧边栏</b>——原版侧边栏会在每行右侧强制渲染红色分值（序号），无法隐藏；
 * 自绘 HUD 则只显示行文本（信息已全部包含在文本里），不渲染任何数字。
 *
 * <p>行内容由 {@link #update(List, Iterable)} 设置：每行是一条显示文本，按传入顺序自上而下排列。
 * 所有方法在服务器主线程调用，非线程安全。
 */
final class MatchScoreboard {

    private final String title;

    /** 当前展示的行（自上而下）。 */
    private List<String> current = new ArrayList<>();

    MatchScoreboard(String objectiveName, Component title) {
        // title 为 Component.literal(...)，其 getString() 原样保留 §-颜色码，供客户端渲染。
        this.title = title.getString();
    }

    /** 让一名玩家开始显示本侧边栏（下发当前所有行）。 */
    void showTo(ServerPlayer player) {
        ArcadeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncSidebarPacket(true, title, current));
    }

    /** 让一名玩家移除本侧边栏显示。 */
    void hideFrom(ServerPlayer player) {
        ArcadeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncSidebarPacket(false, "", List.of()));
    }

    /**
     * 更新行内容并推送给当前观看者。
     *
     * @param lines   自上而下的显示文本
     * @param viewers 当前应看到侧边栏的玩家
     */
    void update(List<String> lines, Iterable<ServerPlayer> viewers) {
        current = new ArrayList<>(lines);
        for (ServerPlayer p : viewers) {
            ArcadeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new SyncSidebarPacket(true, title, current));
        }
    }
}
