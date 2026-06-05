package org.shee33.act0.arcade.round;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 比分板：按"方"（side，可为队伍 id 或玩家 id 字符串）累计分数，并依据 {@link RoundFormat}
 * 判定比赛是否结束及胜者。MC-free，纯逻辑，可单测。
 *
 * <p>"得分"语义由模式决定：决斗模式赢一回合 +1，死斗模式每击杀 +1。
 *
 * <p>非线程安全；由单个游戏循环线程驱动。
 */
public final class MatchScore {

    private final int pointsToWin;
    private final Map<String, Integer> scores = new LinkedHashMap<>();

    public MatchScore(RoundFormat format) {
        this.pointsToWin = Objects.requireNonNull(format, "format").pointsToWin();
    }

    public MatchScore(int pointsToWin) {
        if (pointsToWin < 1) {
            throw new IllegalArgumentException("pointsToWin must be >= 1, got " + pointsToWin);
        }
        this.pointsToWin = pointsToWin;
    }

    /** 注册一方（初始 0 分）；重复注册无副作用。 */
    public void registerSide(String side) {
        scores.putIfAbsent(Objects.requireNonNull(side, "side"), 0);
    }

    /**
     * 给某一方 +1 分（自动注册），返回该方新的总分。
     */
    public int addPoint(String side) {
        Objects.requireNonNull(side, "side");
        int next = scores.getOrDefault(side, 0) + 1;
        scores.put(side, next);
        return next;
    }

    /** 某一方当前分数（未注册返回 0）。 */
    public int score(String side) {
        return scores.getOrDefault(side, 0);
    }

    /** 是否已有一方达到胜利阈值。 */
    public boolean isReached() {
        return scores.values().stream().anyMatch(v -> v >= pointsToWin);
    }

    /**
     * 胜者：达到阈值且分数最高的一方。若无人达到阈值，或并列最高（罕见，
     * 正常计分流程下不会两方同 tick 越线），返回空。
     */
    public Optional<String> winner() {
        String best = null;
        int bestScore = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            int v = e.getValue();
            if (v < pointsToWin) {
                continue;
            }
            if (v > bestScore) {
                best = e.getKey();
                bestScore = v;
                tie = false;
            } else if (v == bestScore) {
                tie = true;
            }
        }
        return (best == null || tie) ? Optional.empty() : Optional.of(best);
    }

    /** 当前领先者（不要求达到阈值；并列时返回空）。 */
    public Optional<String> leader() {
        String best = null;
        int bestScore = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            int v = e.getValue();
            if (v > bestScore) {
                best = e.getKey();
                bestScore = v;
                tie = false;
            } else if (v == bestScore) {
                tie = true;
            }
        }
        return (best == null || tie) ? Optional.empty() : Optional.of(best);
    }

    /** 获胜所需分数。 */
    public int pointsToWin() {
        return pointsToWin;
    }

    /** 所有方的当前分数快照（只读）。 */
    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    }
}
