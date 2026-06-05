package org.shee33.act0.arcade.round;

/**
 * 复活点策略：决定玩家死亡后在哪里重新进入战斗。
 *
 * <ul>
 *   <li>{@link #FIXED_SPAWN} —— 回到本方固定出生点（单挑 1v1）。</li>
 *   <li>{@link #NEAR_TEAMMATE} —— 在存活队友附近复活（2v2 / 团队死斗）。</li>
 *   <li>{@link #RANDOM} —— 在竞技场内随机点复活（个人乱斗）。</li>
 * </ul>
 *
 * <p>本枚举只描述"语义意图"，具体坐标解析由 MC 层运行时根据竞技场配置完成，保持核心 MC-free。
 */
public enum RespawnPolicy {
    FIXED_SPAWN,
    NEAR_TEAMMATE,
    RANDOM
}
