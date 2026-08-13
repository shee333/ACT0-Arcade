package org.shee33.act0.arcade.bot;

/**
 * AI 关心的对局模式分类。MC-free，可单测。
 *
 * <p><b>为什么不直接用 {@code MatchSettings} 的模式字符串。</b>AI 需要的不是"这局叫什么"，而是
 * "这局的战术形态是什么"——有没有队友、有没有需要占的点、是不是人人为敌。把字符串收敛成这个枚举，
 * 战术层就能在编译期穷举（{@code switch} 无 default），新增一个模式字符串时漏配战术会立刻显形。
 *
 * <p>{@link #OTHER} 兜住军备竞赛、跳狙等尚未做差异化的模式：它们沿用通用战术，而不是被特殊对待。
 * 这比给每个模式都硬塞一套参数诚实——没调过的模式就该明确标为"没调过"。
 */
public enum BotMode {

    /** 1v1 决斗：无队友，单一对手，回合制。 */
    DUEL_1V1,

    /** 2v2 决斗：一个队友，协同收益最高。 */
    DUEL_2V2,

    /** 团队竞技：多队友，按击杀计分。 */
    TEAM_DEATHMATCH,

    /** 个人乱斗：人人为敌，随时可能被第三方偷。 */
    FREE_FOR_ALL,

    /** 热区：分数只从占点来，位置价值高于人头。 */
    HOT_ZONE,

    /** 尚未做差异化的模式（军备竞赛、跳狙等），沿用通用战术。 */
    OTHER;

    /**
     * 由 {@code MatchSettings#modeId()} 映射；未知或空一律归 {@link #OTHER}。
     *
     * <p>用 modeId 而非 {@code ScoringMode}：团队竞技与热区共享 {@code sideCount}/复活策略，
     * 只有 modeId 能无歧义区分全部五种目标形态（{@code ScoringMode} 也能分出热区，但分不出
     * 1v1 与 2v2）。
     */
    public static BotMode fromModeId(String modeId) {
        if (modeId == null) {
            return OTHER;
        }
        return switch (modeId) {
            case "duel_1v1" -> DUEL_1V1;
            case "duel_2v2" -> DUEL_2V2;
            case "team_deathmatch" -> TEAM_DEATHMATCH;
            case "free_for_all" -> FREE_FOR_ALL;
            case "hot_zone" -> HOT_ZONE;
            default -> OTHER;
        };
    }

    /** 本模式是否存在队友，即小队协同是否有意义。 */
    public boolean hasTeammates() {
        return this == DUEL_2V2 || this == TEAM_DEATHMATCH || this == HOT_ZONE || this == OTHER;
    }

    /** 本模式是否存在需要争夺的地图目标（当前只有热区）。 */
    public boolean hasObjective() {
        return this == HOT_ZONE;
    }
}
