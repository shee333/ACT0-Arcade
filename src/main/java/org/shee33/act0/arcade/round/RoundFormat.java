package org.shee33.act0.arcade.round;

/**
 * 赛制：把"几局几胜 / 先到几分"统一归约为一个胜利阈值 {@link #pointsToWin()}。
 *
 * <p>四种街机模式的计分都可表达为"某一方先到 N 分"：
 * <ul>
 *   <li>单挑/2v2：赢一回合得 1 分，{@code 5局3胜} = {@code bestOf(5)}，{@code 15局8胜} = {@code firstTo(8)}。</li>
 *   <li>团队死斗/个人乱斗：每击杀得 1 分，{@code firstTo(killTarget)}。</li>
 * </ul>
 *
 * <p>不可变值对象，参数非法时抛 {@link IllegalArgumentException}。
 */
public final class RoundFormat {

    private final int pointsToWin;
    private final int maxGames;

    private RoundFormat(int pointsToWin, int maxGames) {
        if (pointsToWin < 1) {
            throw new IllegalArgumentException("pointsToWin must be >= 1, got " + pointsToWin);
        }
        if (maxGames < pointsToWin) {
            throw new IllegalArgumentException(
                    "maxGames (" + maxGames + ") must be >= pointsToWin (" + pointsToWin + ")");
        }
        this.pointsToWin = pointsToWin;
        this.maxGames = maxGames;
    }

    /**
     * 先到 {@code wins} 分获胜（无固定总局数上限，理论上限为 {@code 2*wins-1}）。
     * 例如 {@code firstTo(8)} 表示 15 局 8 胜。
     */
    public static RoundFormat firstTo(int wins) {
        return new RoundFormat(wins, 2 * wins - 1);
    }

    /**
     * N 局制（{@code games} 为奇数时多数胜）。例如 {@code bestOf(5)} = 5 局 3 胜。
     */
    public static RoundFormat bestOf(int games) {
        if (games < 1) {
            throw new IllegalArgumentException("games must be >= 1, got " + games);
        }
        return new RoundFormat(games / 2 + 1, games);
    }

    /** 获胜所需分数（局数）。 */
    public int pointsToWin() {
        return pointsToWin;
    }

    /** 理论最大可进行的局数。 */
    public int maxGames() {
        return maxGames;
    }

    @Override
    public String toString() {
        return "RoundFormat{firstTo=" + pointsToWin + ", maxGames=" + maxGames + "}";
    }
}
