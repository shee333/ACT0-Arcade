package org.shee33.act0.arcade.bot.mc;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * AI 士兵的假网络连接：不接 netty 通道，静默吞掉一切出站包。
 *
 * <p><b>这是整个 AI 方案能否低改动落地的关键。</b>{@code ArcadeMatch} 会给参战者持续下发
 * 计分板同步、死亡镜头、观战、HUD、开火锁等大量数据包，而 bot 没有真实客户端。两种应对：
 * <ol>
 *   <li>在每个发包点插入 {@code if (isBot) skip}——要改动几十处，且今后每新增一种包就多一个漏判点；</li>
 *   <li>让连接层把包吞掉——上层照常"发包"，包进黑洞。</li>
 * </ol>
 * 本类走第二条，因此 {@code ArcadeMatch} 及其全部下游发包逻辑<b>无需任何改造</b>。
 *
 * <p><b>为什么不需要同时替换 {@code ServerGamePacketListenerImpl}</b>：原版监听器的
 * {@code send} 最终一律委托到 {@link Connection#send}，在此处拦截即可覆盖全部出站路径；
 * 而 {@code PlayerList.placeNewPlayer} 内部会自行 new 一个监听器，想替换它就必须上 mixin。
 * 在连接层拦截使我们绕开了 mixin 依赖（{@code AGENTS.md} 禁止无故引入新依赖）。
 *
 * <p><b>为什么监听器的超时踢人不会触发</b>：{@code ServerGamePacketListenerImpl.tick()} 的
 * keep-alive 超时检查由 {@code ServerConnectionListener} 遍历真实连接驱动，而本连接从未注册
 * 进那份列表，因此永远不会被 tick，也就不存在"bot 因无心跳被踢下线"。
 *
 * <p>原版实现大量直接操作 {@code channel} 字段，本类 channel 恒为 {@code null}，
 * 因此所有会解引用它的方法都必须在此覆盖为安全实现，否则一律 NPE。
 */
public final class BotConnection extends Connection {

    /**
     * 假远端地址。
     *
     * <p>{@code placeNewPlayer} 登录日志会打印远端地址；用
     * {@link InetSocketAddress#createUnresolved} 而非普通构造，避免触发真实 DNS 解析
     * ——那会在主线程上产生一次可能阻塞的网络查询。
     */
    private static final SocketAddress FAKE_ADDRESS = InetSocketAddress.createUnresolved("act0.bot", 0);

    public BotConnection() {
        // 服务端侧接收的是 serverbound 方向，与真实玩家连接一致。
        super(PacketFlow.SERVERBOUND);
    }

    @Override
    public void send(Packet<?> packet) {
        // 静默丢弃：bot 没有客户端。
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        // 静默丢弃；刻意不回调 listener，避免上层误以为包已确认送达。
    }

    // 刻意不覆盖 flushQueue()：原版将其声明为 private，且 send 已被吞掉、队列恒为空，无需处理。

    @Override
    public void tick() {
        // 无通道可维护；同时确保即便被误注册进连接列表也不会驱动监听器的超时检查。
    }

    /** 恒为已连接：返回 {@code false} 会让服务端把 bot 当作掉线玩家清理掉。 */
    @Override
    public boolean isConnected() {
        return true;
    }

    /** 恒为非握手中：原版实现以 {@code channel == null} 判定"连接中"，对本类恒真，必须覆盖。 */
    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return FAKE_ADDRESS;
    }

    @Override
    public void disconnect(Component reason) {
        // 原版会 close() 真实通道；此处无通道。bot 的下线一律走 BotSpawner.despawn。
    }

    @Override
    public void setReadOnly() {
        // 无通道可置为只读。
    }

    @Override
    public void handleDisconnection() {
        // 无通道，无待处理的断开事件。
    }
}
