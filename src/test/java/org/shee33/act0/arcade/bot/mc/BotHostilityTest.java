package org.shee33.act0.arcade.bot.mc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敌我判定纯逻辑单元测试。
 *
 * <p>三种误判各自都会毁掉玩法，故逐一钉住：把队友当敌人（团队模式下互射）、
 * 把别局玩家当敌人（跨局开火）、以及裸生成的调试 bot 互不为敌（完全测不动）。
 */
class BotHostilityTest {

    @Test
    void botOutsideAnyMatchTreatsEveryoneAsEnemy() {
        // 调试形态：两个裸生成的 bot 必须能互相交火，否则感知层无法验证
        assertTrue(BotHostility.decide(false, -1, -1));
        assertTrue(BotHostility.decide(false, 0, 0), "未入局时不看方索引");
        assertTrue(BotHostility.decide(false, 3, 3));
    }

    @Test
    void teammatesAreNeverEnemies() {
        assertFalse(BotHostility.decide(true, 0, 0));
        assertFalse(BotHostility.decide(true, 1, 1));
        assertFalse(BotHostility.decide(true, 7, 7));
    }

    @Test
    void opposingSidesAreEnemies() {
        assertTrue(BotHostility.decide(true, 0, 1));
        assertTrue(BotHostility.decide(true, 1, 0));
    }

    @Test
    void outsidersToTheMatchAreNotEnemies() {
        // 候选者不在提问方那场对局里（旁观者、其它对局的玩家）——绝不能开火
        assertFalse(BotHostility.decide(true, 0, -1));
        assertFalse(BotHostility.decide(true, 5, -1));
        assertFalse(BotHostility.decide(true, 0, -99));
    }

    @Test
    void freeForAllGivesEveryPlayerTheirOwnSideSoAllAreEnemies() {
        // 个人乱斗每人自成一方，由"不同方即为敌"自然覆盖，无需特例
        for (int mine = 0; mine < 6; mine++) {
            for (int theirs = 0; theirs < 6; theirs++) {
                boolean enemy = BotHostility.decide(true, mine, theirs);
                if (mine == theirs) {
                    assertFalse(enemy, "同方不应为敌: " + mine);
                } else {
                    assertTrue(enemy, "不同方应为敌: " + mine + " vs " + theirs);
                }
            }
        }
    }
}
