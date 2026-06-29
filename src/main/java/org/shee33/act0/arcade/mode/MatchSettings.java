package org.shee33.act0.arcade.mode;

import org.shee33.act0.arcade.loadout.LoadoutRuleset;
import org.shee33.act0.arcade.round.RespawnPolicy;
import org.shee33.act0.arcade.round.RoundFormat;

import java.util.Objects;

/**
 * 模式描述符：用一组不可变参数完整刻画一种街机模式，供单一通用对局运行时驱动。
 *
 * <p>四种内置模式的全部差异都收敛到这里——队伍大小、参战方数、计分方式、复活策略、赛制、配装规则集；
 * 因此无需为每种模式各写一套臃肿的运行时代码。MC-free，可单测。
 *
 * <p>所有街机模式统一使用 {@link LoadoutRuleset#ARCADE}，从而"无缝禁用"战场兵种专属道具。
 */
public final class MatchSettings {

    private final String modeId;
    private final String displayName;
    private final int sideCount;
    private final int teamSize;
    private final ScoringMode scoringMode;
    private final RoundFormat roundFormat;
    private final RespawnPolicy respawnPolicy;
    private final LoadoutRuleset ruleset;
    private final int countdownSeconds;
    private final int reEquipProtectionSeconds;
    private final int timeLimitSeconds;
    private final RandomWeaponMode randomWeaponMode;

    private MatchSettings(Builder b) {
        this.modeId = Objects.requireNonNull(b.modeId, "modeId");
        this.displayName = b.displayName != null ? b.displayName : b.modeId;
        if (b.sideCount < 2) {
            throw new IllegalArgumentException("sideCount must be >= 2, got " + b.sideCount);
        }
        if (b.teamSize < 1) {
            throw new IllegalArgumentException("teamSize must be >= 1, got " + b.teamSize);
        }
        this.sideCount = b.sideCount;
        this.teamSize = b.teamSize;
        this.scoringMode = Objects.requireNonNull(b.scoringMode, "scoringMode");
        this.roundFormat = Objects.requireNonNull(b.roundFormat, "roundFormat");
        this.respawnPolicy = Objects.requireNonNull(b.respawnPolicy, "respawnPolicy");
        this.ruleset = b.ruleset != null ? b.ruleset : LoadoutRuleset.ARCADE;
        this.countdownSeconds = Math.max(0, b.countdownSeconds);
        this.reEquipProtectionSeconds = Math.max(0, b.reEquipProtectionSeconds);
        this.timeLimitSeconds = Math.max(0, b.timeLimitSeconds);
        this.randomWeaponMode = b.randomWeaponMode != null ? b.randomWeaponMode : RandomWeaponMode.OFF;
    }

    // ---- 内置模式预设 ----

    /** 单挑 1v1：两方各 1 人，赢回合计分，5 局 3 胜，固定出生点复活。 */
    public static MatchSettings duel1v1() {
        return builder("duel_1v1", "单挑 1v1")
                .sideCount(2).teamSize(1)
                .scoringMode(ScoringMode.ROUND_WIN)
                .roundFormat(RoundFormat.bestOf(5))
                .respawnPolicy(RespawnPolicy.FIXED_SPAWN)
                .build();
    }

    /** 2v2：两方各 2 人，赢回合计分，5 局 3 胜，队友附近复活。 */
    public static MatchSettings duel2v2() {
        return builder("duel_2v2", "2v2")
                .sideCount(2).teamSize(2)
                .scoringMode(ScoringMode.ROUND_WIN)
                .roundFormat(RoundFormat.bestOf(5))
                .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE)
                .build();
    }

    /** 团队死斗：两队，按击杀计分，先到 40 杀，队友附近复活。 */
    public static MatchSettings teamDeathmatch(int teamSize, int killTarget) {
        return builder("team_deathmatch", "团队死斗")
                .sideCount(2).teamSize(teamSize)
                .scoringMode(ScoringMode.KILL_COUNT)
                .roundFormat(RoundFormat.firstTo(killTarget))
                .respawnPolicy(RespawnPolicy.NEAR_TEAMMATE)
                .build();
    }

    /** 个人乱斗：N 个独立"方"（每方 1 人），按击杀计分，先到目标分，随机点复活。 */
    public static MatchSettings freeForAll(int players, int killTarget) {
        return builder("free_for_all", "个人乱斗")
                .sideCount(Math.max(2, players)).teamSize(1)
                .scoringMode(ScoringMode.KILL_COUNT)
                .roundFormat(RoundFormat.firstTo(killTarget))
                .respawnPolicy(RespawnPolicy.RANDOM)
                .build();
    }

    public static Builder builder(String modeId, String displayName) {
        return new Builder(modeId, displayName);
    }

    // ---- 访问器 ----

    public String modeId() {
        return modeId;
    }

    public String displayName() {
        return displayName;
    }

    /** 参战方数（决斗/团队=2，个人乱斗=人数）。 */
    public int sideCount() {
        return sideCount;
    }

    /** 每方人数（1v1/乱斗=1，2v2=2，团队死斗可配置）。 */
    public int teamSize() {
        return teamSize;
    }

    public ScoringMode scoringMode() {
        return scoringMode;
    }

    public RoundFormat roundFormat() {
        return roundFormat;
    }

    public RespawnPolicy respawnPolicy() {
        return respawnPolicy;
    }

    public LoadoutRuleset ruleset() {
        return ruleset;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int reEquipProtectionSeconds() {
        return reEquipProtectionSeconds;
    }

    /** 对局限时（秒）；{@code 0} 表示不限时（仅按分数目标结束）。 */
    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    /** 是否随机武器模式（忽略玩家配装，每次重生/回合随机发枪）。 */
    public boolean randomWeapons() {
        return randomWeaponMode.enabled();
    }

    /** 随机武器细分模式。 */
    public RandomWeaponMode randomWeaponMode() {
        return randomWeaponMode;
    }

    /**
     * 是否为“弹性人数”模式：击杀/目标计分类可以不满员开局并中途补人；
     * 回合制决斗（单挑/2v2）为固定人数，必须满员开局。
     */
    public boolean flexible() {
        return scoringMode == ScoringMode.KILL_COUNT || scoringMode == ScoringMode.HOT_ZONE;
    }

    /** 每局总参战人数。 */
    public int totalPlayers() {
        return sideCount * teamSize;
    }

    @Override
    public String toString() {
        return "MatchSettings{" + modeId + ", sides=" + sideCount + "x" + teamSize
                + ", scoring=" + scoringMode + ", " + roundFormat
                + ", respawn=" + respawnPolicy + ", ruleset=" + ruleset + "}";
    }

    /** 构造器，便于自定义模式或调整默认参数。 */
    public static final class Builder {
        private final String modeId;
        private final String displayName;
        private int sideCount = 2;
        private int teamSize = 1;
        private ScoringMode scoringMode = ScoringMode.ROUND_WIN;
        private RoundFormat roundFormat = RoundFormat.bestOf(5);
        private RespawnPolicy respawnPolicy = RespawnPolicy.FIXED_SPAWN;
        private LoadoutRuleset ruleset = LoadoutRuleset.ARCADE;
        private int countdownSeconds = 5;
        private int reEquipProtectionSeconds = 3;
        private int timeLimitSeconds = 0;
        private RandomWeaponMode randomWeaponMode = RandomWeaponMode.OFF;

        private Builder(String modeId, String displayName) {
            this.modeId = modeId;
            this.displayName = displayName;
        }

        public Builder sideCount(int sideCount) {
            this.sideCount = sideCount;
            return this;
        }

        public Builder teamSize(int teamSize) {
            this.teamSize = teamSize;
            return this;
        }

        public Builder scoringMode(ScoringMode scoringMode) {
            this.scoringMode = scoringMode;
            return this;
        }

        public Builder roundFormat(RoundFormat roundFormat) {
            this.roundFormat = roundFormat;
            return this;
        }

        public Builder respawnPolicy(RespawnPolicy respawnPolicy) {
            this.respawnPolicy = respawnPolicy;
            return this;
        }

        public Builder ruleset(LoadoutRuleset ruleset) {
            this.ruleset = ruleset;
            return this;
        }

        public Builder countdownSeconds(int countdownSeconds) {
            this.countdownSeconds = countdownSeconds;
            return this;
        }

        public Builder reEquipProtectionSeconds(int reEquipProtectionSeconds) {
            this.reEquipProtectionSeconds = reEquipProtectionSeconds;
            return this;
        }

        /** 对局限时（秒）；{@code 0}=不限时。 */
        public Builder timeLimitSeconds(int timeLimitSeconds) {
            this.timeLimitSeconds = timeLimitSeconds;
            return this;
        }

        /** 随机武器模式开关。 */
        public Builder randomWeapons(boolean randomWeapons) {
            this.randomWeaponMode = randomWeapons ? RandomWeaponMode.ALL : RandomWeaponMode.OFF;
            return this;
        }

        /** 随机武器细分模式。 */
        public Builder randomMode(RandomWeaponMode randomWeaponMode) {
            this.randomWeaponMode = randomWeaponMode != null ? randomWeaponMode : RandomWeaponMode.OFF;
            return this;
        }

        public MatchSettings build() {
            return new MatchSettings(this);
        }
    }
}
