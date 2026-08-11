package org.shee33.act0.arcade.bot.mc;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * AI 士兵的躯体：一个没有客户端的真 {@link ServerPlayer}。
 *
 * <p><b>为什么是 ServerPlayer 而不是自定义 Mob</b>：{@code ArcadeMatch} 的伤害归属、命中反馈、
 * 击杀计分、热区占领计数、配装发放全部以 {@code ServerPlayer} 为准入条件——做成 Mob 就要把这些
 * 逐条重写；做成玩家则<b>一行都不用改</b>。代价只有一个：MC 的导航栈绑定在 {@code Mob} 上，
 * 寻路要自建。而战地风格的 bot 本来就需要理解据点与掩体的自建导航，那份工作无论如何都躲不掉。
 *
 * <p><b>本类存在的唯一理由：补上缺失的物理驱动。</b>
 * 原版把玩家的每 tick 工作拆成了两半：
 * <ul>
 *   <li>{@link ServerPlayer#tick()} —— 由所在世界的实体循环驱动，做游戏模式、容器同步等；</li>
 *   <li>{@link ServerPlayer#doTick()} —— 由 {@code ServerGamePacketListenerImpl.tick()} 驱动，
 *       内部才调用 {@code Player.tick()}，也就是重力、碰撞、台阶攀爬所在的真正物理链路。</li>
 * </ul>
 * 而 bot 的假连接从不会被 {@code ServerConnectionListener} 遍历到，{@code doTick()} 永远不会被调用
 * ——不补这一刀，bot 会像雕像一样定在原地，既不受重力也不响应任何移动意图。
 *
 * <p>补上之后，位移、碰撞、重力、台阶攀爬全部由原版物理接管，无需自研玩家物理引擎。
 * 移动意图只需每 tick 写入 {@code zza}/{@code xxa}/{@code yRot}，见 {@link BotMovementDriver}。
 */
public final class BotPlayer extends ServerPlayer {

    /**
     * 区块跟随间隔（tick）。
     *
     * <p>真玩家靠上行移动包触发区块票据更新；bot 没有上行包，若不主动更新，走出初始加载范围后
     * 就会踏进未加载区块——表现为凭空卡住或掉出世界。每 10 tick（0.5 秒）更新一次足够跟上
     * 步行速度，且开销可忽略。
     */
    private static final int CHUNK_FOLLOW_INTERVAL_TICKS = 10;

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile);
    }

    @Override
    public void tick() {
        super.tick();

        // placeNewPlayer 尚未把监听器装上时不能推进物理。
        // 刻意用显式判空而非捕获 NPE：后者会连带吞掉真正的逻辑缺陷。
        if (this.connection == null) {
            return;
        }

        this.doTick();

        if (this.tickCount % CHUNK_FOLLOW_INTERVAL_TICKS == 0) {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);
        }
    }
}
