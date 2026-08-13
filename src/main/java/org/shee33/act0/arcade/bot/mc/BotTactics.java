package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.BotMode;
import org.shee33.act0.arcade.bot.CombatStance;
import org.shee33.act0.arcade.bot.ModeTactics;
import org.shee33.act0.arcade.bot.RetreatPolicy;
import org.shee33.act0.arcade.bot.SquadTactics;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 单个 bot 的模式化战术状态：按当前对局模式配好的交火姿态与撤退状态机，外加小队机动的几何计算。
 *
 * <p><b>为什么按模式重建而不是每 tick 传参。</b>{@link CombatStance} 与 {@link RetreatPolicy} 都<b>带
 * 内部状态</b>（侧移翻向节奏、撤退迟滞计时）。把模式参数每 tick 传进去等于要求这两个类无状态，
 * 而它们的状态恰恰是"行为可读"的来源。因此改为：模式变了就整体重建，模式不变就一直复用同一个实例。
 *
 * <p>模式在一局之内不会变，重建只发生在 bot 进入对局、换局、或从对局外状态转入对局时——
 * 也正是那些本就该重置侧移节奏与撤退迟滞的时刻。
 */
final class BotTactics {

    private final BotPlayer bot;

    private BotMode mode = BotMode.OTHER;
    private ModeTactics tactics = ModeTactics.forMode(BotMode.OTHER);
    private CombatStance stance = ModeTactics.forMode(BotMode.OTHER).newStance();
    private RetreatPolicy retreat = ModeTactics.forMode(BotMode.OTHER).newRetreatPolicy();

    BotTactics(BotPlayer bot) {
        this.bot = bot;
    }

    /**
     * 按最新对局快照刷新战术配置，并推进撤退状态机一 tick。
     *
     * @param context 当前对局快照；{@code null}（不在对局中）时退回通用配置并复位撤退状态
     */
    void tick(@Nullable BotMatchContext context) {
        BotMode next = context != null ? context.mode() : BotMode.OTHER;
        if (next != mode) {
            mode = next;
            tactics = ModeTactics.forMode(next);
            stance = tactics.newStance();
            retreat = tactics.newRetreatPolicy();
            return;
        }
        if (context == null) {
            retreat.reset();
            return;
        }
        retreat.tick(context.healthFraction());
    }

    BotMode mode() {
        return mode;
    }

    ModeTactics tactics() {
        return tactics;
    }

    CombatStance stance() {
        return stance;
    }

    /** 当前是否应当主动脱离交火（低血）。 */
    boolean breakingOff() {
        return retreat.shouldBreakOff();
    }

    /** 复活时复位：撤退迟滞不应跨越生命延续。 */
    void onRespawn() {
        retreat.reset();
    }

    /**
     * 本 bot 在小队中的角色。
     *
     * <p>无队友（1v1、个人乱斗）或绕侧倾向过低时恒为压制——独自绕侧只是把侧身送给对手。
     */
    SquadTactics.Role role(@Nullable BotMatchContext context) {
        if (context == null || context.teammates().isEmpty()) {
            return SquadTactics.Role.SUPPRESS;
        }
        return SquadTactics.roleFor(context.squadRank(), tactics.flankBias());
    }

    /**
     * 把"自己→目标"的推进方向按绕侧角旋转；压制角色原样返回。
     *
     * @return 长度 2 的数组 {@code {dx, dz}}，模长与输入一致
     */
    double[] approachDirection(@Nullable BotMatchContext context, double dx, double dz) {
        if (context == null || context.teammates().isEmpty()) {
            return new double[]{dx, dz};
        }
        float offset = SquadTactics.flankOffsetDegrees(context.squadRank(), tactics.flankBias());
        return offset == 0.0F ? new double[]{dx, dz} : SquadTactics.rotateApproach(dx, dz, offset);
    }

    /**
     * 队友散开的排斥位移：太挤时给出一个远离最近队友的水平向量，否则返回 {@link Vec3#ZERO}。
     *
     * <p>只算<b>在场且存活</b>的队友：待复活的队友仍在名单里，但把死人算进间距会让活人绕着尸体走位。
     *
     * <p>排斥量按 {@link SquadTactics#separationStrength} 线性缩放到最多
     * {@value SquadTactics#SPACING_MIN_BLOCKS} 格的位移分量——它只是叠加在期望方向上的偏置，
     * 不是独立的移动目标，否则 bot 会为了散开而放弃推进。
     */
    Vec3 separation(@Nullable BotMatchContext context) {
        if (context == null) {
            return Vec3.ZERO;
        }
        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (UUID mate : context.teammates()) {
            ServerPlayer other = bot.serverLevel().getServer().getPlayerList().getPlayer(mate);
            if (other == null || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            double dx = bot.getX() - other.getX();
            double dz = bot.getZ() - other.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        if (nearest == null) {
            return Vec3.ZERO;
        }
        double strength = SquadTactics.separationStrength(nearestDist);
        if (strength <= 0.0D) {
            return Vec3.ZERO;
        }
        double dx = bot.getX() - nearest.getX();
        double dz = bot.getZ() - nearest.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4D) {
            // 完全重合时方向未定义，用实体 id 造一个稳定的错开方向，避免两人朝同一侧一起挪。
            double angle = (bot.getId() % 8) * (Math.PI / 4.0D);
            return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle))
                    .scale(SquadTactics.SPACING_MIN_BLOCKS * strength);
        }
        return new Vec3(dx / len, 0.0D, dz / len)
                .scale(SquadTactics.SPACING_MIN_BLOCKS * strength);
    }

    /**
     * 热区等目标模式下，是否该放弃当前交火回防目标点。
     *
     * <p>判据是"本模式认为目标重于追击"且当前离目标点已超过容许半径
     * （{@link ModeTactics#objectiveLeashBlocks}）。非目标模式恒为 {@code false}。
     */
    boolean shouldFallBackToObjective(@Nullable BotMatchContext context) {
        if (context == null || context.objective() == null
                || !tactics.prefersObjectiveOverPursuit()) {
            return false;
        }
        Vec3 objective = context.objective();
        double dx = bot.getX() - objective.x;
        double dz = bot.getZ() - objective.z;
        return Math.sqrt(dx * dx + dz * dz) > tactics.objectiveLeashBlocks();
    }
}
