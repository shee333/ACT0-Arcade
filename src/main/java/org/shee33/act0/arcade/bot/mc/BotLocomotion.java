package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.CombatStance;
import org.shee33.act0.arcade.bot.MarchTactics;

import javax.annotation.Nullable;

/**
 * 单个 AI 士兵的位移层：持有寻路状态与交火姿态，把"该往哪走"翻译成每 tick 的移动输入。
 *
 * <p>从 {@link BotTask} 拆出来，是因为位移已包含三套彼此耦合的状态——路径游标、
 * 手动航点、交火姿态——继续堆在 BotTask 里会让"一个 bot 的状态"和"如何移动"混为一谈。
 *
 * <p><b>朝向归属是本类的核心约定。</b>交火时朝向由 {@link BotWeaponController} 独占
 * （枪要压在敌人身上），此时位移只能通过 {@link BotMovementDriver#driveRelative} 走侧移分解；
 * 未交火时朝向才交还位移层，用 {@link BotMovementDriver#driveTo} 让身体朝向移动方向。
 */
final class BotLocomotion {

    private final BotPlayer bot;
    private final CombatStance stance = new CombatStance();
    private final BotPatrol patrol;
    private final BotPathFollower path;

    /**
     * 手动航点。
     *
     * <p>刻意不叫 {@code target}：那会与交火目标混淆，而两者在交火时是竞争朝向控制权的。
     */
    @Nullable
    private Vec3 waypoint;

    BotLocomotion(BotPlayer bot) {
        this.bot = bot;
        this.patrol = new BotPatrol(bot);
        this.path = new BotPathFollower(bot);
    }

    /** 设定手动航点；清空既有路径以便按新目标重新规划。 */
    void setWaypoint(@Nullable Vec3 target) {
        this.waypoint = target;
        path.reset();
    }

    @Nullable
    Vec3 waypoint() {
        return waypoint;
    }

    /**
     * 走向手动航点；抵达或无路可走时清空航点并停下。
     *
     * @param aimOwnsFacing 交火中，朝向不得由位移层接管
     */
    void tickWaypoint(MinecraftServer server, boolean aimOwnsFacing) {
        if (waypoint == null) {
            return;
        }
        BotPathFollower.Outcome outcome = path.follow(server, waypoint, aimOwnsFacing, false);
        if (outcome != BotPathFollower.Outcome.MOVING) {
            // 抵达与无路可走都应放弃航点，而不是原地顶着障碍反复尝试。
            setWaypoint(null);
            BotMovementDriver.halt(bot);
        }
    }

    /**
     * 自主战斗移动：无交火目标时朝敌人推进，交火中按姿态推进／横向机动／后撤。
     *
     * <p>未交火就一律推进，是为了让 bot 主动寻敌——没有这一条，看不见敌人的 bot 会满场站桩。
     * 只有真正交火时才启用横向机动与后撤，否则"没在打枪却横着走"看起来只是行为异常。
     *
     * @param goal    移动目标（最近敌人，不要求可见）；{@code null} 表示无敵可寻
     * @param engaged 是否正在交火（有交火目标）
     */
    void tickCombat(MinecraftServer server, @Nullable Entity aimTarget, @Nullable Entity seekGoal) {
        Entity engaging = alive(aimTarget);
        if (engaging != null) {
            manoeuvre(server, engaging);
            return;
        }
        Entity seeking = alive(seekGoal);
        if (seeking != null) {
            if (marchTo(server, seeking.position()) != BotPathFollower.Outcome.MOVING) {
                BotMovementDriver.halt(bot);
            }
        } else {
            patrol(server);
        }
        applyScan(server);
    }

    /**
     * 朝一个静止坐标行军：远距离疾跑，朝向仍归位移层（未交火，枪不需要压在谁身上）。
     *
     * <p>疾跑门槛见 {@link MarchTactics#SPRINT_MIN_DISTANCE}——此处恒以"未交火"询问，
     * 因为本方法只在没有交火目标的分支被调用。
     */
    private BotPathFollower.Outcome marchTo(MinecraftServer server, Vec3 destination) {
        double dx = destination.x - bot.getX();
        double dz = destination.z - bot.getZ();
        boolean sprint = MarchTactics.shouldSprint(false, Math.sqrt(dx * dx + dz * dz));
        return path.follow(server, destination, false, sprint);
    }

    /** 搜索半径内一个敌人都没有时的巡逻；无处可去（不在对局中）则停下。 */
    private void patrol(MinecraftServer server) {
        Vec3 target = patrol.destination(server);
        if (target == null) {
            BotMovementDriver.halt(bot);
            return;
        }
        if (marchTo(server, target) != BotPathFollower.Outcome.MOVING) {
            // 无路可走或已抵达：换一个巡逻点，而不是抵着障碍反复重算同一条路。
            patrol.abandon();
            BotMovementDriver.halt(bot);
        }
    }

    /**
     * 未交火时让头部相对身体扫视，身体朝向仍归行进方向。
     *
     * <p>只动头不动身，是因为身体朝向决定移动方向（见
     * {@link BotMovementDriver#driveRelative}）——摆动身体会让 bot 走蛇形。
     * 交火时本方法不会被调用：那时朝向归瞄准独占。
     *
     * <p><b>只登记偏移量，不直接写头部朝向。</b>本方法运行在 tick 的开始阶段，而随后的原版
     * 实体 tick 会把玩家头部重新对齐到身体——实测直接写 {@code setYHeadRot} 后头身夹角
     * 恒为 0.0°，扫视完全无效。真正的施加点在 {@link BotPlayer#tick()} 末尾。
     */
    private void applyScan(MinecraftServer server) {
        bot.setHeadYawOffset(MarchTactics.scanOffsetDegrees(server.getTickCount(), bot.getId()));
    }

    /**
     * 交火机动：姿态与方向<b>一律相对正在交火的那个目标</b>。
     *
     * <p>此处曾误用"最近的敌人"来算距离与侧移方向。两者未必是同一个人——最近的敌人可能
     * 躲在墙后（不可交火），而正在对枪的是更远处那个可见的敌人。一旦错位，bot 会朝 A 开枪
     * 却按到 B 的距离决定推进还是后撤、并绕着 B 侧移，动作与枪口完全对不上。
     * 双 bot 场景下两者恒为同一人，因此这类错位在小规模测试里永远暴露不出来。
     */
    private void manoeuvre(MinecraftServer server, Entity target) {
        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        switch (stance.modeFor(Math.sqrt(dx * dx + dz * dz))) {
            case ADVANCE -> {
                if (path.follow(server, target.position(), true, false) != BotPathFollower.Outcome.MOVING) {
                    BotMovementDriver.halt(bot);
                }
            }
            case HOLD -> strafe(dx, dz);
            case RETREAT -> retreat(dx, dz);
        }
    }

    @Nullable
    private static Entity alive(@Nullable Entity entity) {
        return entity != null && entity.isAlive() ? entity : null;
    }

    /**
     * 后撤：背离敌人拉开距离；退无可退时改为贴墙横移。
     *
     * <p>撞墙分支的价值经变异实测确认，但<b>不是</b>"防止 bot 被钉死"：去掉它之后 bot 并不会
     * 静止，而是在姿态之间反复弹跳（实测背离方向被完全挡住时，z 轴每次采样来回 ±1 格、
     * 并向侧面无规律漂移）。加上它之后，贴墙脱离变成沿墙面单向平滑侧滑。
     * 换来的是动作可读性，而非"能不能动"。
     *
     * <p>真人在退不动时会侧身挪开；而 {@link #strafe} 的翻向本就由碰撞抢占，
     * 因此第一侧也堵住时会自动换另一侧。
     */
    private void retreat(double dx, double dz) {
        if (bot.horizontalCollision) {
            strafe(dx, dz);
            return;
        }
        BotMovementDriver.driveRelative(bot, -dx, -dz, false);
    }

    /**
     * 横向机动：沿垂直于"自己→敌人"的方向平移，方向按节奏与碰撞翻转。
     *
     * <p>{@code (-dz, dx)} 是把敌人方向逆时针旋转 90°，乘以姿态给出的符号即得左右两侧。
     */
    private void strafe(double dx, double dz) {
        stance.tick(bot.horizontalCollision);
        int sign = stance.strafeSign();
        BotMovementDriver.driveRelative(bot, -dz * sign, dx * sign, false);
    }

    /** bot 撤走时释放导航资源，避免影子 Mob 随之泄漏。 */
    void release() {
        path.release();
    }
}
