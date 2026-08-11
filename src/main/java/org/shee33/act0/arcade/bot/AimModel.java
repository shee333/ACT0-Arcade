package org.shee33.act0.arcade.bot;

/**
 * AI 士兵的瞄准模型：难度的<b>唯一</b>来源。MC-free 纯数据 + 纯函数，可单测。
 *
 * <p><b>为什么难度必须落在瞄准而非决策上。</b>玩家对"这个 bot 强不强"的感知约九成来自枪法手感，
 * 一成来自战术走位。若靠"让弱 bot 决策更差"来降难度，得到的是<b>笨</b>（走错路、卡墙）而不是
 * <b>菜</b>（枪法差）——玩家能立刻分辨这两者，前者直接出戏。COD 系全靠这套参数造难度梯度，
 * 决策逻辑四档共用一份。
 *
 * <p><b>误差圆为何拆成五个参数。</b>合并成"一个准度值"就无法分别控制两件本质不同的事：
 * 刚发现你时准不准（{@link #errorInitialDegrees} → {@link #errorSettledDegrees} 的收敛），
 * 以及连射之后散不散（{@link #errorPerShotDegrees} 累积 / {@link #errorRecoveryPerTick} 回落）。
 * 前者决定"探头对枪"是否可行，后者决定"拉开距离等他打空"是否可行——都是玩家的可用战术。
 *
 * <p><b>后坐力的建模方式。</b>对 bot 而言后坐力不是镜头抬升（它没有镜头），而是
 * <b>每发之后误差圆增大、停火后回落</b>。这样"压枪能力"成为一个可调数值，而非一段需要模拟的物理。
 *
 * <p><b>角度到格数的标定锚点。</b>距离 {@code d} 处的横向偏移约 {@code d·tan(误差角)}；
 * 30 格处 1° ≈ 0.52 格，而玩家碰撞箱宽 0.6 格。因此<b>1° 误差在 30 格上恰好是"擦边命中"</b>
 * ——下方各档的数值都以此为基准校准。
 */
public record AimModel(
        int reactionTicks,
        float fovHalfAngleDegrees,
        float turnRateDegPerTick,
        float errorInitialDegrees,
        float errorSettledDegrees,
        int errorConvergeTicks,
        float errorPerShotDegrees,
        float errorRecoveryPerTick,
        int burstMinShots,
        int burstMaxShots,
        int burstPauseTicks,
        int reacquireTicks) {

    /** 误差圆上限，防止长时间连射后 bot 朝天乱打。 */
    public static final float MAX_ERROR_DEGREES = 20.0F;

    public AimModel {
        if (reactionTicks < 0) {
            throw new IllegalArgumentException("reactionTicks must be >= 0, got " + reactionTicks);
        }
        if (fovHalfAngleDegrees <= 0.0F || fovHalfAngleDegrees > 180.0F) {
            throw new IllegalArgumentException("fovHalfAngleDegrees must be in (0,180], got " + fovHalfAngleDegrees);
        }
        if (turnRateDegPerTick <= 0.0F) {
            throw new IllegalArgumentException("turnRateDegPerTick must be > 0, got " + turnRateDegPerTick);
        }
        if (errorInitialDegrees < errorSettledDegrees) {
            throw new IllegalArgumentException("误差必须随跟踪时间收敛，要求 initial >= settled");
        }
        if (errorSettledDegrees < 0.0F) {
            throw new IllegalArgumentException("errorSettledDegrees must be >= 0");
        }
        if (errorConvergeTicks <= 0) {
            throw new IllegalArgumentException("errorConvergeTicks must be > 0, got " + errorConvergeTicks);
        }
        if (burstMinShots < 1 || burstMaxShots < burstMinShots) {
            throw new IllegalArgumentException("要求 1 <= burstMinShots <= burstMaxShots");
        }
        if (burstPauseTicks < 0 || reacquireTicks < 0) {
            throw new IllegalArgumentException("burstPauseTicks / reacquireTicks must be >= 0");
        }
    }

    /**
     * 四档难度。
     *
     * <p>各档只是参数集差异，决策逻辑完全共用——与 CS 的 {@code BotProfile.db}、
     * COD 的难度设定同构。
     */
    public enum Difficulty {

        /**
         * 简单：反应 600ms，收敛后 30 格上横向偏移约 1.57 格（碰撞箱宽 0.6，约 2.6 倍箱宽，
         * 远距离基本打不中），后坐力几乎压不住。面向新手，交火中玩家有充裕时间反应。
         */
        EASY(12, 50.0F, 6.0F, 6.0F, 3.0F, 30, 1.20F, 0.15F, 2, 4, 18, 10),

        /** 普通：反应 400ms，收敛后 30 格上偏移约 0.79 格（约 1.3 倍箱宽，擦边命中），对枪互有胜负。 */
        NORMAL(8, 60.0F, 10.0F, 4.0F, 1.5F, 24, 0.90F, 0.25F, 3, 5, 14, 20),

        /** 困难：反应 250ms，收敛后 30 格上偏移约 0.37 格（略大于半个箱宽，稳定命中），压枪较好。 */
        HARD(5, 70.0F, 16.0F, 3.0F, 0.7F, 16, 0.60F, 0.40F, 4, 7, 10, 40),

        /**
         * 精英：反应 150ms（接近职业选手），收敛后近乎必中，压枪极稳，记忆你的位置长达 3 秒。
         * 刻意保留非零误差与反应延迟——零误差会让玩家判定为作弊而非强。
         */
        ELITE(3, 80.0F, 24.0F, 2.0F, 0.25F, 10, 0.35F, 0.60F, 5, 9, 7, 60);

        private final AimModel model;

        Difficulty(int reactionTicks, float fov, float turnRate,
                   float errInit, float errSettled, int convergeTicks,
                   float errPerShot, float errRecovery,
                   int burstMin, int burstMax, int burstPause, int reacquire) {
            this.model = new AimModel(reactionTicks, fov, turnRate, errInit, errSettled,
                    convergeTicks, errPerShot, errRecovery,
                    burstMin, burstMax, burstPause, reacquire);
        }

        public AimModel model() {
            return model;
        }
    }

    /**
     * 持续跟踪 {@code ticksOnTarget} tick 后的基础误差圆半径（度）。
     *
     * <p>刻意用线性收敛而非指数：策划看"多少 tick 后收敛到多少度"比看时间常数直观，
     * 满足 {@code AGENTS.md} 对"数值必须有设计依据"的要求。
     */
    public float baseErrorDegrees(int ticksOnTarget) {
        if (ticksOnTarget <= 0) {
            return errorInitialDegrees;
        }
        if (ticksOnTarget >= errorConvergeTicks) {
            return errorSettledDegrees;
        }
        float t = (float) ticksOnTarget / (float) errorConvergeTicks;
        return errorInitialDegrees + (errorSettledDegrees - errorInitialDegrees) * t;
    }

    /** 叠加后坐力累积后的总误差圆半径（度），已钳制到 {@link #MAX_ERROR_DEGREES}。 */
    public float totalErrorDegrees(int ticksOnTarget, float recoilDegrees) {
        float total = baseErrorDegrees(ticksOnTarget) + Math.max(0.0F, recoilDegrees);
        return Math.min(MAX_ERROR_DEGREES, total);
    }

    /** 目标是否落在视野锥内（{@code angleToTarget} 为与当前朝向的夹角绝对值）。 */
    public boolean withinFov(float angleToTargetDegrees) {
        return Math.abs(angleToTargetDegrees) <= fovHalfAngleDegrees;
    }

    /**
     * 给定距离下，某误差角对应的横向偏移（格）。
     *
     * <p>供调参时把角度换算成"能不能打中"的直观量：玩家碰撞箱宽 0.6 格，
     * 偏移超过 0.3 格即为脱靶。
     */
    public static double lateralOffsetBlocks(float errorDegrees, double distanceBlocks) {
        return distanceBlocks * Math.tan(Math.toRadians(errorDegrees));
    }
}
