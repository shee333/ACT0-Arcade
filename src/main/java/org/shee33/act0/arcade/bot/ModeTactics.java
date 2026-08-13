package org.shee33.act0.arcade.bot;

/**
 * 按对局模式差异化的战术参数。MC-free 纯数据 + 查表，可单测。
 *
 * <p><b>与难度的分工。</b>{@link AimModel} 管"枪法有多好"，本类管"该怎么打"。两者刻意不交叉：
 * 同一难度的 bot 在 1v1 与个人乱斗里枪法完全相同，但交火距离与追击意愿不同——这正是玩家能
 * 感知到"它在按这个模式打"的来源。若把模式差异也塞进瞄准参数，就变成了"乱斗里 bot 变菜"，
 * 那是难度而不是战术。
 *
 * <p><b>为什么这些参数覆盖 {@link CombatStance} 的默认值。</b>CombatStance 的
 * {@value CombatStance#DEFAULT_ENGAGE_RANGE}/{@value CombatStance#DEFAULT_TOO_CLOSE_RANGE}
 * 是"没有模式信息时的通用值"；一旦知道模式，距离带就该跟着变——1v1 里贴近是纯赌枪法，
 * 乱斗里久战必被第三方偷。
 *
 * @param engageRange        超过此距离转为推进（格），喂给 {@link CombatStance}
 * @param tooCloseRange      近于此距离转为后撤（格），喂给 {@link CombatStance}
 * @param objectivePull      地图目标（热区）的吸引权重，{@code 0}=无目标模式，{@code 1}=只认目标
 * @param flankBias          绕侧倾向，越高越愿意走侧路而非正面推进
 * @param pursuitWillingness 追击意愿，越低越容易主动脱离（不恋战）
 * @param breakOffHealth     血量降到此比例即脱离交火，喂给 {@link RetreatPolicy}
 */
public record ModeTactics(double engageRange,
                          double tooCloseRange,
                          double objectivePull,
                          float flankBias,
                          float pursuitWillingness,
                          float breakOffHealth) {

    public ModeTactics {
        if (engageRange <= tooCloseRange) {
            throw new IllegalArgumentException(
                    "交火距离必须大于后撤距离，否则姿态判定没有保持带：engage=" + engageRange
                            + " tooClose=" + tooCloseRange);
        }
        objectivePull = clamp01(objectivePull);
        flankBias = (float) clamp01(flankBias);
        pursuitWillingness = (float) clamp01(pursuitWillingness);
        breakOffHealth = (float) clamp01(breakOffHealth);
    }

    private static double clamp01(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }

    /**
     * 取某模式的战术参数。
     *
     * <p>各档取值依据：
     * <ul>
     *   <li><b>1v1</b> 交火距离最远（20 格）：没有队友掩护，贴近等于把胜负全押在枪法上，
     *       而玩家在中距离才有走位与掩体可用。绕侧最低——独自绕侧只是把侧身白送给对手。</li>
     *   <li><b>2v2</b> 绕侧最高（0.55）：两人里一个压制一个绕，是这个人数下唯一能打出配合的形态。</li>
     *   <li><b>团竞</b> 追击意愿最高（0.75）：人头直接是分，脱离的机会成本最大；绕侧略低于 2v2，
     *       因为场上人多，绕侧更容易撞进第三个人的枪口。</li>
     *   <li><b>个人乱斗</b> 交火距离最近（12 格）但追击意愿最低（0.35）：抢人头要快，
     *       久战必被第三方偷——"打完就走"是这个模式的正解。脱离血线最高（0.40），残血硬拼
     *       在乱斗里几乎必死于第三方。</li>
     *   <li><b>热区</b> 目标吸引 0.85 压倒一切，追击意愿低（0.45）：分数只从占点来，
     *       追着人离开热区是负收益。交火距离取 14，略近于通用值，因为要在点内外的窄范围里打。
     *       脱离血线最低（0.20）：离点就是丢分，值得多扛一会儿。</li>
     * </ul>
     */
    public static ModeTactics forMode(BotMode mode) {
        return switch (mode) {
            case DUEL_1V1 -> new ModeTactics(20.0D, 7.0D, 0.0D, 0.15F, 0.55F, 0.30F);
            case DUEL_2V2 -> new ModeTactics(16.0D, 5.0D, 0.0D, 0.55F, 0.70F, 0.30F);
            case TEAM_DEATHMATCH -> new ModeTactics(16.0D, 5.0D, 0.0D, 0.45F, 0.75F, 0.28F);
            case FREE_FOR_ALL -> new ModeTactics(12.0D, 4.0D, 0.0D, 0.10F, 0.35F, 0.40F);
            case HOT_ZONE -> new ModeTactics(14.0D, 5.0D, 0.85D, 0.35F, 0.45F, 0.20F);
            case OTHER -> new ModeTactics(
                    CombatStance.DEFAULT_ENGAGE_RANGE, CombatStance.DEFAULT_TOO_CLOSE_RANGE,
                    0.0D, 0.30F, 0.60F, 0.30F);
        };
    }

    /** 用本模式的脱离血线构造撤退状态机。 */
    public RetreatPolicy newRetreatPolicy() {
        return new RetreatPolicy(breakOffHealth,
                RetreatPolicy.DEFAULT_REENGAGE_HEALTH, RetreatPolicy.DEFAULT_MIN_BREAK_OFF_TICKS);
    }

    /** 用本模式的距离带构造交火姿态机。 */
    public CombatStance newStance() {
        return new CombatStance(engageRange, tooCloseRange, CombatStance.DEFAULT_STRAFE_FLIP_TICKS);
    }

    /**
     * 是否该为了地图目标放弃追击当前敌人。
     *
     * <p>判据是"目标吸引 &gt; 追击意愿"且确实存在目标点。热区档 0.85 &gt; 0.45 恒成立，
     * 因此热区 bot 一旦离点就会往回走；其余模式 objectivePull 为 0，恒不放弃追击。
     */
    public boolean prefersObjectiveOverPursuit() {
        return objectivePull > pursuitWillingness;
    }

    /** 目标吸引力拉满时容许离点的半径（格）。 */
    public static final double MIN_OBJECTIVE_LEASH = 10.0D;

    /** 目标吸引力趋零时容许离点的半径（格）。 */
    public static final double MAX_OBJECTIVE_LEASH = 40.0D;

    /**
     * 距离地图目标多远就该放弃交火回防（格）；无目标模式返回 {@link Double#MAX_VALUE}（永不回防）。
     *
     * <p>在 {@link #MIN_OBJECTIVE_LEASH} 与 {@link #MAX_OBJECTIVE_LEASH} 之间按
     * {@link #objectivePull} 线性插值：吸引力越强，容许离点越近。热区档（0.85）解得 14.5 格
     * ——从点边缘还能看到点内战况的距离，再远就等于放弃了这个点。
     */
    public double objectiveLeashBlocks() {
        if (objectivePull <= 0.0D) {
            return Double.MAX_VALUE;
        }
        return MIN_OBJECTIVE_LEASH
                + (MAX_OBJECTIVE_LEASH - MIN_OBJECTIVE_LEASH) * (1.0D - objectivePull);
    }
}
