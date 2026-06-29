package org.shee33.act0.arcade.mode;

/**
 * 计分方式：决定 {@link org.shee33.act0.arcade.round.MatchScore} 的"得分"语义。
 *
 * <ul>
 *   <li>{@link #ROUND_WIN} —— 决斗类（1v1/2v2）：赢下一整回合 +1 分，配合 BoN 赛制。</li>
 *   <li>{@link #KILL_COUNT} —— 死斗类（团队死斗/个人乱斗）：每次击杀 +1 分，先到目标分获胜。</li>
 *   <li>{@link #HOT_ZONE} —— 热区类：控制当前热区时按秒加分，先到目标分获胜。</li>
 * </ul>
 */
public enum ScoringMode {
    ROUND_WIN,
    KILL_COUNT,
    HOT_ZONE
}
