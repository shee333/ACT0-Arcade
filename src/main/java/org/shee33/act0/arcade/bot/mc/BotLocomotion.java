package org.shee33.act0.arcade.bot.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.CombatStance;
import org.shee33.act0.arcade.bot.PathCursor;

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

    /**
     * 行进时每 tick 最大转向角（度）。
     *
     * <p>与瞄准的转向速率刻意分开：那是交火时枪口追踪目标的速率、属难度参数；
     * 这是行军时身体的转向速率，与强弱无关，四档共用一个值即可。
     */
    private static final float MOVE_TURN_RATE = 12.0F;

    /** 抵达判定半径（格）。小于玩家碰撞箱宽度会导致到点后反复微调抖动。 */
    private static final double ARRIVE_RADIUS = 0.6D;

    /**
     * 路径重规划间隔（tick）。
     *
     * <p>2 秒重算一次：原版 {@code shouldRecomputePath} 极少触发，而追击活动目标时
     * 目标每时每刻都在移动。间隔再短会让寻路开销显著上升，而 {@link PathCursor} 的前瞻
     * 与卡死恢复已能吸收这个粒度内的偏差。
     */
    private static final int PATH_REPLAN_INTERVAL_TICKS = 40;

    /**
     * 判定卡死的无进展 tick 数。
     *
     * <p>1.5 秒：足够长以容纳绕过障碍时的正常贴墙滑行与起跳，又足够短以免玩家看着 bot 顶墙发呆。
     */
    private static final int STUCK_TICKS = 30;

    private final BotPlayer bot;
    private final CombatStance stance = new CombatStance();

    @Nullable
    private BotNavigator navigator;

    @Nullable
    private PathCursor cursor;

    /**
     * 手动航点。
     *
     * <p>刻意不叫 {@code target}：那会与交火目标混淆，而两者在交火时是竞争朝向控制权的。
     */
    @Nullable
    private Vec3 waypoint;

    BotLocomotion(BotPlayer bot) {
        this.bot = bot;
    }

    /** 设定手动航点；清空既有路径以便按新目标重新规划。 */
    void setWaypoint(@Nullable Vec3 target) {
        this.waypoint = target;
        this.cursor = null;
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
        Outcome outcome = followPath(server, waypoint, aimOwnsFacing);
        if (outcome != Outcome.MOVING) {
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
    void tickCombat(MinecraftServer server, @Nullable Entity goal, boolean engaged) {
        if (goal == null || !goal.isAlive()) {
            BotMovementDriver.halt(bot);
            return;
        }
        if (!engaged) {
            if (followPath(server, goal.position(), false) != Outcome.MOVING) {
                BotMovementDriver.halt(bot);
            }
            return;
        }
        double dx = goal.getX() - bot.getX();
        double dz = goal.getZ() - bot.getZ();
        switch (stance.modeFor(Math.sqrt(dx * dx + dz * dz))) {
            case ADVANCE -> {
                if (followPath(server, goal.position(), true) != Outcome.MOVING) {
                    BotMovementDriver.halt(bot);
                }
            }
            case HOLD -> strafe(dx, dz);
            case RETREAT -> retreat(dx, dz);
        }
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

    private enum Outcome {
        /** 正在沿路径前进。 */
        MOVING,
        /** 已抵达目标点。 */
        ARRIVED,
        /** 无路可走或卡死后正在重规划。 */
        BLOCKED
    }

    /**
     * 沿路径走向目标点一 tick。
     *
     * <p>三件事必须一起做，缺一个都会让 bot 看起来是坏的：周期重规划（原版的
     * {@code shouldRecomputePath} 极少触发，追活动目标时路径立刻过期）、卡死恢复
     * （撞在几何缝隙里只会继续顶墙）、以及无路可走时如实上报而非死抱目标。
     */
    private Outcome followPath(MinecraftServer server, Vec3 destination, boolean aimOwnsFacing) {
        boolean due = cursor == null
                || (server.getTickCount() + bot.getId()) % PATH_REPLAN_INTERVAL_TICKS == 0;
        if (due) {
            PathCursor fresh = navigator().computePath(BlockPos.containing(destination));
            if (fresh == null) {
                return Outcome.BLOCKED;
            }
            cursor = fresh;
        }
        if (cursor.advance(bot.getX(), bot.getY(), bot.getZ())) {
            return Outcome.ARRIVED;
        }
        if (cursor.ticksWithoutProgress() > STUCK_TICKS) {
            cursor.stepBack();
            cursor = null;
            return Outcome.BLOCKED;
        }
        if (aimOwnsFacing) {
            BotMovementDriver.driveRelative(bot,
                    cursor.targetX() - bot.getX(), cursor.targetZ() - bot.getZ(), false);
        } else {
            BotMovementDriver.driveTo(bot, cursor.targetX(), cursor.targetY(), cursor.targetZ(),
                    MOVE_TURN_RATE, ARRIVE_RADIUS, false);
        }
        return Outcome.MOVING;
    }

    private BotNavigator navigator() {
        if (navigator == null) {
            navigator = new BotNavigator(bot);
        }
        return navigator;
    }

    /** bot 撤走时释放导航资源，避免影子 Mob 随之泄漏。 */
    void release() {
        cursor = null;
        if (navigator != null) {
            navigator.release();
            navigator = null;
        }
    }
}
