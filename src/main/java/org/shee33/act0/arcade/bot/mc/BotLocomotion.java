package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.MarchTactics;
import org.shee33.act0.arcade.bot.SquadTactics;

import javax.annotation.Nullable;

/**
 * 单个 AI 士兵的位移层：把"该往哪走"翻译成每 tick 的移动输入。
 *
 * <p>从 {@link BotTask} 拆出来，是因为位移已包含三套彼此耦合的状态——路径游标、
 * 手动航点、交火姿态——继续堆在 BotTask 里会让"一个 bot 的状态"和"如何移动"混为一谈。
 * "未交火时去哪"这一条策略链进一步拆给了 {@link BotSeekPolicy}，本类只负责执行。
 *
 * <p><b>朝向归属是本类的核心约定。</b>交火时朝向由 {@link BotWeaponController} 独占
 * （枪要压在敌人身上），此时位移只能通过 {@link BotMovementDriver#driveRelative} 走侧移分解；
 * 未交火时朝向才交还位移层，用 {@link BotMovementDriver#driveTo} 让身体朝向移动方向。
 */
final class BotLocomotion {

    private final BotPlayer bot;
    private final BotSeekPolicy seek;
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
        this.seek = new BotSeekPolicy(bot);
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
     * 自主战斗移动。
     *
     * <p>三条互斥的分支，按优先级排列：<b>低血脱离</b> → <b>交火机动</b> → <b>行军</b>。
     * 脱离排在最前，是因为它是一个覆盖性决策——残血时无论当前距离处在哪一档，正解都是拉开，
     * 若让它与姿态判定并列，bot 会在"该撤"和"该推"之间被距离带来回改判。
     *
     * @param aimTarget 交火目标（必须可见）
     * @param seekGoal  最近敌人（不要求可见），仅在未交火时用于推进
     * @param context   对局快照，可为 {@code null}（对局外的裸 bot）
     * @param tactics   本 bot 的模式化战术状态
     */
    void tickCombat(MinecraftServer server, @Nullable Entity aimTarget, @Nullable Entity seekGoal,
                    @Nullable BotMatchContext context, BotTactics tactics) {
        Entity engaging = alive(aimTarget);
        if (engaging != null && tactics.breakingOff()) {
            disengage(engaging, tactics.separation(context));
            return;
        }
        if (engaging != null && !tactics.shouldFallBackToObjective(context)) {
            manoeuvre(server, engaging, context, tactics);
            return;
        }
        march(server, alive(seekGoal), context, tactics);
        applyScan(server);
    }

    /** bot 撤走时释放导航资源，避免影子 Mob 随之泄漏。 */
    void release() {
        path.release();
    }

    /**
     * 行军：去向交由 {@link BotSeekPolicy} 决定，本方法只负责走过去。
     *
     * <p>去向叠加了队友排斥位移，因此整队 bot 即使目标点相同也会散成一个面而不是叠成一条线。
     */
    private void march(MinecraftServer server, @Nullable Entity seekGoal,
                       @Nullable BotMatchContext context, BotTactics tactics) {
        Vec3 destination = seek.destination(server, context, seekGoal);
        if (destination == null) {
            BotMovementDriver.halt(bot);
            return;
        }
        Vec3 adjusted = destination.add(tactics.separation(context));
        if (marchTo(server, adjusted) != BotPathFollower.Outcome.MOVING) {
            // 无路可走或已抵达：换一个巡逻点，而不是抵着障碍反复重算同一条路。
            seek.abandonPatrol();
            BotMovementDriver.halt(bot);
        }
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
    private void manoeuvre(MinecraftServer server, Entity target,
                           @Nullable BotMatchContext context, BotTactics tactics) {
        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        switch (tactics.stance().modeFor(Math.sqrt(dx * dx + dz * dz))) {
            case ADVANCE -> advance(server, target, context, tactics, dx, dz);
            case HOLD -> strafe(dx, dz, tactics.stance());
            case RETREAT -> retreat(dx, dz, tactics.stance());
        }
    }

    /**
     * 推进：压制角色直冲目标，绕侧角色改走一个偏离轴线的落点。
     *
     * <p>绕侧落点取"目标位置 + 旋转后的接近向量"，而不是直接把移动输入旋转——后者会让 bot
     * 沿切线一直平移、永远接不上火。落点仍以目标为锚，因此绕侧是"从侧面靠过去"而非"绕着跑圈"。
     */
    private void advance(MinecraftServer server, Entity target, @Nullable BotMatchContext context,
                         BotTactics tactics, double dx, double dz) {
        double[] dir = tactics.approachDirection(context, dx, dz);
        Vec3 destination = target.position();
        if (dir[0] != dx || dir[1] != dz) {
            // 从目标处沿旋转后方向的反向退回一段，得到侧翼上的进攻位置。
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
            if (len > 1.0e-4D) {
                double back = Math.min(len, SquadTactics.SPACING_MIN_BLOCKS * 2.0D);
                destination = destination.subtract(dir[0] / len * back, 0.0D, dir[1] / len * back);
            }
        }
        Vec3 adjusted = destination.add(tactics.separation(context));
        if (path.follow(server, adjusted, true, false) != BotPathFollower.Outcome.MOVING) {
            BotMovementDriver.halt(bot);
        }
    }

    /**
     * 低血脱离：背离目标拉开距离并叠加队友排斥，撞墙时改为贴墙横移。
     *
     * <p>脱离期间<b>不停火</b>——朝向仍归武器控制器，边退边打既符合真人行为，也让玩家看得出
     * 这是"被打退"而不是"卡住了"。
     */
    private void disengage(Entity target, Vec3 separation) {
        double dx = bot.getX() - target.getX() + separation.x;
        double dz = bot.getZ() - target.getZ() + separation.z;
        BotMovementDriver.driveRelative(bot, dx, dz, false);
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
    private void retreat(double dx, double dz, org.shee33.act0.arcade.bot.CombatStance stance) {
        if (bot.horizontalCollision) {
            strafe(dx, dz, stance);
            return;
        }
        BotMovementDriver.driveRelative(bot, -dx, -dz, false);
    }

    /**
     * 横向机动：沿垂直于"自己→敌人"的方向平移，方向按节奏与碰撞翻转。
     *
     * <p>{@code (-dz, dx)} 是把敌人方向逆时针旋转 90°，乘以姿态给出的符号即得左右两侧。
     */
    private void strafe(double dx, double dz, org.shee33.act0.arcade.bot.CombatStance stance) {
        stance.tick(bot.horizontalCollision);
        int sign = stance.strafeSign();
        BotMovementDriver.driveRelative(bot, -dz * sign, dx * sign, false);
    }
}
