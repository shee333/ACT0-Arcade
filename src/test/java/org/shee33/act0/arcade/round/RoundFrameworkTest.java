package org.shee33.act0.arcade.round;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回合框架核心（MC-free）单元测试：赛制换算、比分判定、倒计时边沿。
 */
class RoundFrameworkTest {

    @Test
    void bestOfFiveNeedsThreeWins() {
        RoundFormat format = RoundFormat.bestOf(5);
        assertEquals(3, format.pointsToWin());
        assertEquals(5, format.maxGames());
    }

    @Test
    void firstToEightIsFifteenGameMax() {
        RoundFormat format = RoundFormat.firstTo(8);
        assertEquals(8, format.pointsToWin());
        assertEquals(15, format.maxGames());
    }

    @Test
    void invalidFormatRejected() {
        assertThrows(IllegalArgumentException.class, () -> RoundFormat.firstTo(0));
        assertThrows(IllegalArgumentException.class, () -> RoundFormat.bestOf(0));
    }

    @Test
    void matchScoreDeclaresWinnerOnReachingThreshold() {
        MatchScore score = new MatchScore(RoundFormat.bestOf(5));
        score.registerSide("A");
        score.registerSide("B");
        assertFalse(score.isReached());

        score.addPoint("A"); // 1
        score.addPoint("B"); // 1
        score.addPoint("A"); // 2
        assertFalse(score.isReached());
        assertEquals("A", score.leader().orElse(null));

        assertEquals(3, score.addPoint("A")); // 3 -> reached
        assertTrue(score.isReached());
        assertEquals("A", score.winner().orElse(null));
        assertEquals(3, score.score("A"));
        assertEquals(1, score.score("B"));
    }

    @Test
    void leaderEmptyOnTie() {
        MatchScore score = new MatchScore(5);
        score.addPoint("A");
        score.addPoint("B");
        assertTrue(score.leader().isEmpty());
    }

    @Test
    void killCountModeReusesMatchScore() {
        // 团队死斗 / 个人乱斗：每击杀 +1，先到 10 分
        MatchScore score = new MatchScore(RoundFormat.firstTo(10));
        for (int i = 0; i < 9; i++) {
            score.addPoint("RED");
        }
        assertFalse(score.isReached());
        score.addPoint("RED");
        assertTrue(score.isReached());
        assertEquals("RED", score.winner().orElse(null));
    }

    @Test
    void phaseTimerFiresExactlyOnZeroEdge() {
        PhaseTimer timer = new PhaseTimer();
        timer.start(3);
        assertTrue(timer.isRunning());
        assertFalse(timer.tick()); // 2
        assertFalse(timer.tick()); // 1
        assertTrue(timer.tick());  // 0 -> edge
        assertTrue(timer.isFinished());
        assertFalse(timer.tick()); // already zero, no edge
    }

    @Test
    void phaseTimerSecondsConversion() {
        PhaseTimer timer = new PhaseTimer();
        timer.startSeconds(2.0);
        assertEquals(40, timer.remainingTicks());
        assertEquals(2, timer.remainingSeconds());
    }
}
