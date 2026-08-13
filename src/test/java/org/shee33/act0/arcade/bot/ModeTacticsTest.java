package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeTacticsTest {

    @Test
    void everyModeHasTactics() {
        for (BotMode mode : BotMode.values()) {
            ModeTactics t = ModeTactics.forMode(mode);
            assertTrue(t.engageRange() > t.tooCloseRange(), mode + " 必须留出保持带");
        }
    }

    /**
     * 各模式 javadoc 里写明的设计意图锁。这些不是任意数字：改动会直接改变玩家感受到的
     * "这局 bot 在按什么打法行动"，因此把意图而非具体数值锁在测试里。
     */
    @Test
    void duelKeepsTheLongestEngagementRange() {
        double duel = ModeTactics.forMode(BotMode.DUEL_1V1).engageRange();
        for (BotMode mode : BotMode.values()) {
            if (mode != BotMode.DUEL_1V1) {
                assertTrue(duel > ModeTactics.forMode(mode).engageRange(),
                        "1v1 交火距离应远于 " + mode + "：无队友掩护，贴近是纯赌枪法");
            }
        }
    }

    @Test
    void freeForAllFightsClosestAndCommitsLeast() {
        ModeTactics ffa = ModeTactics.forMode(BotMode.FREE_FOR_ALL);
        for (BotMode mode : BotMode.values()) {
            ModeTactics other = ModeTactics.forMode(mode);
            if (mode == BotMode.FREE_FOR_ALL) {
                continue;
            }
            assertTrue(ffa.engageRange() < other.engageRange(), "乱斗交火距离应最近");
            assertTrue(ffa.pursuitWillingness() <= other.pursuitWillingness(), "乱斗追击意愿应最低");
            assertTrue(ffa.breakOffHealth() >= other.breakOffHealth(), "乱斗应最早脱离");
        }
    }

    @Test
    void teamModesFlankMoreThanSoloModes() {
        float pair = ModeTactics.forMode(BotMode.DUEL_2V2).flankBias();
        assertTrue(pair > ModeTactics.forMode(BotMode.DUEL_1V1).flankBias(), "2v2 应比 1v1 更愿绕侧");
        assertTrue(pair > ModeTactics.forMode(BotMode.FREE_FOR_ALL).flankBias(), "2v2 应比乱斗更愿绕侧");
        assertTrue(pair >= ModeTactics.forMode(BotMode.TEAM_DEATHMATCH).flankBias(),
                "2v2 的绕侧收益最高，应不低于团竞");
    }

    @Test
    void onlyHotZonePrefersObjectiveOverPursuit() {
        for (BotMode mode : BotMode.values()) {
            assertEquals(mode == BotMode.HOT_ZONE,
                    ModeTactics.forMode(mode).prefersObjectiveOverPursuit(),
                    mode + " 的目标优先判定");
        }
        assertTrue(ModeTactics.forMode(BotMode.HOT_ZONE).breakOffHealth()
                        < ModeTactics.forMode(BotMode.TEAM_DEATHMATCH).breakOffHealth(),
                "热区离点就是丢分，应比团竞更能扛");
    }

    @Test
    void objectiveLeashIsUnboundedWithoutObjective() {
        assertEquals(Double.MAX_VALUE, ModeTactics.forMode(BotMode.TEAM_DEATHMATCH).objectiveLeashBlocks());
        assertEquals(Double.MAX_VALUE, ModeTactics.forMode(BotMode.DUEL_1V1).objectiveLeashBlocks());
    }

    @Test
    void hotZoneLeashMatchesDocumentedValue() {
        // javadoc 写明热区档（objectivePull=0.85）解得 14.5 格；文档与实现必须一致。
        assertEquals(14.5D, ModeTactics.forMode(BotMode.HOT_ZONE).objectiveLeashBlocks(), 1.0e-6D);
    }

    @Test
    void leashShrinksAsObjectivePullGrows() {
        double weak = new ModeTactics(16, 5, 0.2D, 0.3F, 0.5F, 0.3F).objectiveLeashBlocks();
        double strong = new ModeTactics(16, 5, 0.9D, 0.3F, 0.5F, 0.3F).objectiveLeashBlocks();
        assertTrue(strong < weak, "目标吸引力越强，容许离点越近");
        assertTrue(strong >= ModeTactics.MIN_OBJECTIVE_LEASH);
        assertTrue(weak <= ModeTactics.MAX_OBJECTIVE_LEASH);
    }

    @Test
    void rejectsInvertedDistanceBands() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModeTactics(5.0D, 16.0D, 0.0D, 0.3F, 0.5F, 0.3F));
        assertThrows(IllegalArgumentException.class,
                () -> new ModeTactics(8.0D, 8.0D, 0.0D, 0.3F, 0.5F, 0.3F));
    }

    @Test
    void weightsAreClampedToUnitRange() {
        ModeTactics t = new ModeTactics(16, 5, 9.0D, 9.0F, -9.0F, 9.0F);
        assertEquals(1.0D, t.objectivePull());
        assertEquals(1.0F, t.flankBias());
        assertEquals(0.0F, t.pursuitWillingness());
        assertEquals(1.0F, t.breakOffHealth());
    }

    @Test
    void stanceInheritsModeDistanceBands() {
        ModeTactics t = ModeTactics.forMode(BotMode.DUEL_1V1);
        CombatStance stance = t.newStance();
        assertEquals(CombatStance.Mode.ADVANCE, stance.modeFor(t.engageRange() + 1.0D));
        assertEquals(CombatStance.Mode.HOLD, stance.modeFor((t.engageRange() + t.tooCloseRange()) / 2));
        assertEquals(CombatStance.Mode.RETREAT, stance.modeFor(t.tooCloseRange() - 1.0D));
    }

    @Test
    void retreatPolicyInheritsModeBreakOffHealth() {
        ModeTactics ffa = ModeTactics.forMode(BotMode.FREE_FOR_ALL);
        RetreatPolicy policy = ffa.newRetreatPolicy();
        assertEquals(ffa.breakOffHealth(), policy.breakOffHealth(), 1.0e-6F);
        assertFalse(policy.shouldBreakOff(), "刚建立时应处于交火状态");
    }
}
