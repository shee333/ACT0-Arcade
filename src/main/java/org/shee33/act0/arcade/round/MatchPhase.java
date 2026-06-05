package org.shee33.act0.arcade.round;

/**
 * 对局阶段：所有回合制/计分制模式共享的生命周期阶段。
 *
 * <pre>
 *   WAITING ──(人齐)──▶ COUNTDOWN ──(倒计时到0)──▶ COMBAT
 *      ▲                                              │
 *      │                                    (一回合结束/有击杀)
 *      │                                              ▼
 *   (下一回合) ◀── RE_EQUIP ◀── ROUND_RESULT ──(达成赛点)──▶ MATCH_RESULT ──▶ ENDED
 * </pre>
 *
 * <p>具体迁移由各模式的 MC 层运行时驱动（既受计时器也受游戏事件影响）；本枚举只定义阶段集合。
 */
public enum MatchPhase {
    /** 等待玩家到齐 / 准备。 */
    WAITING,
    /** 开局倒计时（锁定移动/开火）。 */
    COUNTDOWN,
    /** 战斗进行中。 */
    COMBAT,
    /** 单回合结算（决斗：本回合胜负已分）。 */
    ROUND_RESULT,
    /** 重新装备窗口（传送回出生点 + 发配装 + 短暂保护）。 */
    RE_EQUIP,
    /** 整场比赛结算（已达赛点）。 */
    MATCH_RESULT,
    /** 已结束（资源可回收）。 */
    ENDED
}
