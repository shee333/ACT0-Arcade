package org.shee33.act0.arcade.arena;

import org.junit.jupiter.api.Test;
import org.shee33.act0.arcade.round.RespawnPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 竞技场出生点模型（MC-free）单元测试：{@link SpawnPoint} 数据完整性 + {@link ArcadeArena}
 * 出生点索引/回退/校验逻辑。风格参照 {@code RoundFrameworkTest}（按包主题分组，而非严格一类一文件）。
 */
class ArcadeArenaTest {

    private static SpawnPoint point(double x) {
        return new SpawnPoint("minecraft:overworld", x, 64, 0, 90f, 0f);
    }

    // ---------------- SpawnPoint ----------------

    @Test
    void spawnPointShortConstructorDefaultsYawPitchToZero() {
        SpawnPoint p = new SpawnPoint("minecraft:the_nether", 1.5, 2.5, 3.5);
        assertEquals("minecraft:the_nether", p.dimension());
        assertEquals(1.5, p.x());
        assertEquals(2.5, p.y());
        assertEquals(3.5, p.z());
        assertEquals(0f, p.yaw());
        assertEquals(0f, p.pitch());
    }

    @Test
    void spawnPointFullConstructorKeepsAllFields() {
        SpawnPoint p = new SpawnPoint("minecraft:overworld", 10, 20, 30, 45f, -10f);
        assertEquals("minecraft:overworld", p.dimension());
        assertEquals(10, p.x());
        assertEquals(20, p.y());
        assertEquals(30, p.z());
        assertEquals(45f, p.yaw());
        assertEquals(-10f, p.pitch());
    }

    @Test
    void spawnPointRejectsNullDimension() {
        assertThrows(NullPointerException.class, () -> new SpawnPoint(null, 0, 0, 0));
    }

    @Test
    void spawnPointToStringContainsDimensionAndCoords() {
        SpawnPoint p = new SpawnPoint("minecraft:overworld", 1, 2, 3);
        String s = p.toString();
        assertTrue(s.contains("minecraft:overworld"));
        assertTrue(s.contains("1.0"));
    }

    // ---------------- ArcadeArena: 构造与出生点索引 ----------------

    @Test
    void builderAssemblesArenaWithAllSpawnCategories() {
        SpawnPoint side0 = point(0);
        SpawnPoint side1 = point(100);
        SpawnPoint random0 = point(200);
        SpawnPoint back = point(300);

        ArcadeArena arena = new ArcadeArena.Builder("arena_a")
                .addSideSpawn(side0)
                .addSideSpawn(side1)
                .addRandomSpawn(random0)
                .returnSpawn(back)
                .build();

        assertEquals("arena_a", arena.arenaId());
        assertEquals(List.of(side0, side1), arena.sideSpawns());
        assertEquals(List.of(random0), arena.randomSpawns());
        assertSame(back, arena.returnSpawn());
        assertTrue(arena.hasReturnSpawn());
    }

    @Test
    void sideSpawnWrapsAroundUsingFloorMod() {
        SpawnPoint side0 = point(0);
        SpawnPoint side1 = point(100);
        ArcadeArena arena = new ArcadeArena("arena_b", List.of(side0, side1), List.of(), point(999));

        assertSame(side0, arena.sideSpawn(0));
        assertSame(side1, arena.sideSpawn(1));
        // 越界正向回绕
        assertSame(side0, arena.sideSpawn(2));
        assertSame(side1, arena.sideSpawn(3));
        // 负数索引也应正确回绕（floorMod 而非取余）
        assertSame(side1, arena.sideSpawn(-1));
        assertSame(side0, arena.sideSpawn(-2));
    }

    @Test
    void sideSpawnFallsBackToReturnSpawnWhenNoSideSpawnsConfigured() {
        SpawnPoint back = point(999);
        ArcadeArena arena = new ArcadeArena("arena_c", List.of(), List.of(), back);

        assertSame(back, arena.sideSpawn(0));
        assertSame(back, arena.sideSpawn(5));
    }

    @Test
    void randomSpawnsNullListDefaultsToEmptyNotNpe() {
        ArcadeArena arena = new ArcadeArena("arena_d", List.of(point(0)), null, point(1));
        assertTrue(arena.randomSpawns().isEmpty());
    }

    @Test
    void constructorRejectsNullArenaIdAndSideSpawns() {
        assertThrows(NullPointerException.class,
                () -> new ArcadeArena(null, List.of(), List.of(), null));
        assertThrows(NullPointerException.class,
                () -> new ArcadeArena("arena_e", null, List.of(), null));
    }

    @Test
    void sideSpawnsAndRandomSpawnsListsAreUnmodifiable() {
        ArcadeArena arena = new ArcadeArena("arena_f", List.of(point(0)), List.of(point(1)), point(2));
        assertThrows(UnsupportedOperationException.class, () -> arena.sideSpawns().add(point(9)));
        assertThrows(UnsupportedOperationException.class, () -> arena.randomSpawns().add(point(9)));
    }

    @Test
    void hasReturnSpawnFalseWhenNotConfigured() {
        ArcadeArena arena = new ArcadeArena("arena_g", List.of(point(0)), List.of(), null);
        assertFalse(arena.hasReturnSpawn());
        assertNull(arena.returnSpawn());
    }

    // ---------------- ArcadeArena: validateFor ----------------

    @Test
    void validateForFailsWithoutManualReturnSpawn() {
        ArcadeArena arena = new ArcadeArena("arena_h", List.of(point(0), point(1)), List.of(), null);

        ArcadeArena.Validation result = arena.validateFor(RespawnPolicy.FIXED_SPAWN, 2);

        assertFalse(result.isValid());
        assertTrue(result.reason().contains("返回点"));
    }

    @Test
    void validateForRandomPolicyRequiresAtLeastOneRandomSpawn() {
        ArcadeArena noRandom = new ArcadeArena("arena_i", List.of(), List.of(), point(999));
        ArcadeArena.Validation failed = noRandom.validateFor(RespawnPolicy.RANDOM, 4);
        assertFalse(failed.isValid());
        assertTrue(failed.reason().contains("乱斗"));

        ArcadeArena withRandom = new ArcadeArena("arena_j", List.of(), List.of(point(1)), point(999));
        ArcadeArena.Validation ok = withRandom.validateFor(RespawnPolicy.RANDOM, 4);
        assertTrue(ok.isValid());
        assertNull(ok.reason());
    }

    @Test
    void validateForFixedOrTeammatePolicyRequiresEnoughSideSpawns() {
        ArcadeArena onlyOneSide = new ArcadeArena("arena_k", List.of(point(0)), List.of(), point(999));

        ArcadeArena.Validation failed = onlyOneSide.validateFor(RespawnPolicy.FIXED_SPAWN, 2);
        assertFalse(failed.isValid());
        assertTrue(failed.reason().contains("2"));

        ArcadeArena.Validation ok = onlyOneSide.validateFor(RespawnPolicy.FIXED_SPAWN, 1);
        assertTrue(ok.isValid());

        ArcadeArena.Validation okTeammate = onlyOneSide.validateFor(RespawnPolicy.NEAR_TEAMMATE, 1);
        assertTrue(okTeammate.isValid());
    }

    @Test
    void validationHelpersExposeValidityAndReason() {
        ArcadeArena.Validation ok = ArcadeArena.Validation.ok();
        assertTrue(ok.isValid());
        assertNull(ok.reason());

        ArcadeArena.Validation fail = ArcadeArena.Validation.fail("因为原因");
        assertFalse(fail.isValid());
        assertEquals("因为原因", fail.reason());
    }
}
