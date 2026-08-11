package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.level.ServerPlayer;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.match.ArcadeMatch;

/**
 * AI 士兵的敌我判定策略。
 *
 * <p><b>判定必须知道"是谁在问"。</b>同一个候选者对不同 bot 的敌我关系不同——这在早先把判定写成
 * {@code Predicate<ServerPlayer>}（只看候选者）时是个隐藏缺陷：一旦接入分阵营的对局，
 * 所有 bot 会共用同一份判定结果，队友也会被当成敌人。因此这里一律接收 {@code asker}。
 *
 * <p><b>默认策略同时覆盖对局内与对局外</b>，因此无需在开局时向 {@link BotManager} 注入任何东西：
 * <ul>
 *   <li>提问方在对局内 → 同局且不同方为敌；不在同局者（旁观者、其它对局的玩家）不是敌人；</li>
 *   <li>提问方不在对局内 → 混战，除自己以外皆为敌。这是调试形态，两个裸生成的 bot 可以直接互射。</li>
 * </ul>
 *
 * <p>个人乱斗（每人自成一方）由"不同方"这一条自然覆盖，无需特例。
 */
public final class BotHostility {

    private BotHostility() {
    }

    /**
     * {@code candidate} 是否为 {@code asker} 的敌人。
     *
     * <p>不在此处排除自己——调用方（{@link BotPerception}）已先行剔除自身。
     */
    public static boolean isEnemy(ServerPlayer asker, ServerPlayer candidate) {
        ArcadeMatch match = Act0Arcade.services().matches().matchOf(asker.getUUID());
        if (match == null) {
            return decide(false, -1, -1);
        }
        return decide(true,
                match.sideIndexOf(asker.getUUID()),
                match.sideIndexOf(candidate.getUUID()));
    }

    /**
     * 敌我判定的纯逻辑部分，与 MC 无关，可穷尽单测。
     *
     * <p>抽出来是因为这三个分支各自对应一种真实误判：把队友当敌人、把别局的玩家当敌人、
     * 以及裸生成的调试 bot 互不为敌导致完全测不动。分开后无需搭建整场对局即可覆盖。
     *
     * @param askerInMatch   提问方是否在某场对局中
     * @param askerSide      提问方的方索引（不在局内时无意义）
     * @param candidateSide  候选者在<b>提问方那场对局</b>中的方索引；{@code < 0} 表示不在该局
     */
    public static boolean decide(boolean askerInMatch, int askerSide, int candidateSide) {
        if (!askerInMatch) {
            return true;
        }
        if (candidateSide < 0) {
            return false;
        }
        return askerSide != candidateSide;
    }
}
