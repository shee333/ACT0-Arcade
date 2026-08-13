package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.SquadIntel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 未交火时"该往哪走"的优先级链：目标点 → 看得见的敌人 → 队友共享的情报 → 巡逻。
 *
 * <p>从 {@link BotLocomotion} 拆出来，是因为这条链是<b>策略</b>（去哪），而 BotLocomotion 是
 * <b>执行</b>（怎么走过去）。两者混在一处时，每加一个去向来源都要在移动代码里再插一个分支，
 * 很快就分不清"这段是在决定目标还是在驱动腿"。
 *
 * <p><b>优先级的依据。</b>目标点排第一，是因为在热区模式里站在点上就是唯一的得分方式——追人
 * 是手段，占点才是目的；反过来在无目标模式里这一档恒不触发，链条自然退化成原来的"找敌人／巡逻"。
 * 共享情报排在目视敌人<b>之后</b>：自己看得见的人一定比队友两秒前的粗略报点更可靠。
 */
final class BotSeekPolicy {

    /**
     * 判定"已经到位"的到达半径（格）。
     *
     * <p>不用精确抵达中心：热区是一片区域，站在边缘也在得分范围内，非要走到几何中心会让 bot
     * 在点内反复微调位置。3 格是寻路层本身的到达容差量级，取同一量级避免两层判定互相拉扯。
     */
    private static final double ARRIVAL_RADIUS = 3.0D;

    private final BotPlayer bot;
    private final BotPatrol patrol;

    BotSeekPolicy(BotPlayer bot) {
        this.bot = bot;
        this.patrol = new BotPatrol(bot);
    }

    /**
     * 选出本 tick 的行军去向；返回 {@code null} 表示无处可去（应当停下）。
     *
     * @param context      当前对局快照，可为 {@code null}（对局外的裸 bot）
     * @param nearestEnemy 最近的敌人（不要求可见），可为 {@code null}
     */
    @Nullable
    Vec3 destination(MinecraftServer server,
                     @Nullable BotMatchContext context,
                     @Nullable Entity nearestEnemy) {
        Vec3 objective = objectiveDestination(context);
        if (objective != null) {
            return objective;
        }
        if (nearestEnemy != null) {
            return nearestEnemy.position();
        }
        Vec3 intel = intelDestination(context, server.getTickCount());
        if (intel != null) {
            return intel;
        }
        return patrol.destination(server);
    }

    /**
     * 目标点去向：还没站进去就一直往里走；已经在里面则交给后续环节（打人／巡逻）。
     *
     * <p>已在点内还继续以中心为目标会让 bot 无视敌人往中心挤，交火反而被荒废。
     */
    @Nullable
    private Vec3 objectiveDestination(@Nullable BotMatchContext context) {
        if (context == null || context.objectiveArea() == null) {
            return null;
        }
        if (context.insideObjective()) {
            return null;
        }
        Vec3 target = context.objective();
        if (target == null) {
            return null;
        }
        double dx = bot.getX() - target.x;
        double dz = bot.getZ() - target.z;
        return Math.sqrt(dx * dx + dz * dz) <= ARRIVAL_RADIUS ? null : target;
    }

    /**
     * 队友共享情报给出的去向。
     *
     * <p>坐标已在 {@link SquadIntel} 里被量化到 8 格网格，所以这只是"往那一带去看看"，
     * 而不是把 bot 直接送到敌人脚下——这道限制是共享情报不至于变成变相透视的关键。
     */
    @Nullable
    private Vec3 intelDestination(@Nullable BotMatchContext context, long tick) {
        if (context == null) {
            return null;
        }
        Optional<SquadIntel.Contact> contact = BotSquadBoard.INSTANCE.nearestContact(
                context.match().matchId(), context.sideIndex(), bot.getX(), bot.getZ(), tick);
        return contact.map(c -> new Vec3(c.x(), c.y(), c.z())).orElse(null);
    }

    /** 当前巡逻点走不通时换一个。 */
    void abandonPatrol() {
        patrol.abandon();
    }
}
