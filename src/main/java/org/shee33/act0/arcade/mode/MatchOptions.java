package org.shee33.act0.arcade.mode;

/**
 * 一局对局的可配置项（房间创建时由房主选择），独立于固定的模式预设。
 *
 * @param winTarget        自定义计分目标（回合制=先到几胜，击杀制=先到几杀）；{@code null} 用模式默认
 * @param timeLimitSeconds 对局限时（秒）；{@code 0}=不限时
 * @param randomWeapons    是否随机武器模式
 */
public record MatchOptions(Integer winTarget, int timeLimitSeconds, boolean randomWeapons) {

    /** 全部使用默认（无自定义目标、不限时、非随机武器）。 */
    public static MatchOptions defaults() {
        return new MatchOptions(null, 0, false);
    }
}
