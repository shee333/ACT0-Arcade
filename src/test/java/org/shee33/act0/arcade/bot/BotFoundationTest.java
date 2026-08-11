package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 基础层（MC-free）单元测试：{@link BotNames} 身份派生 + {@link Steering} 朝向解算。
 * 按包主题分组，风格参照 {@code ArcadeArenaTest}。
 */
class BotFoundationTest {

    private static final float EPS = 1.0e-4F;

    // ---------------- BotNames ----------------

    @Test
    void poolSizeMatchesDeclaredConstant() {
        assertEquals(BotNames.POOL_SIZE, BotNames.size());
        assertEquals(BotNames.POOL_SIZE, BotNames.pool().size());
    }

    @Test
    void poolNamesAreUniqueAndLegalProfileNames() {
        Set<String> seen = new HashSet<>();
        for (String name : BotNames.pool()) {
            assertTrue(seen.add(name.toLowerCase()), "名池出现重复名：" + name);
            assertTrue(name.length() >= 3 && name.length() <= 16, "名字长度越界：" + name);
            assertTrue(name.matches("[A-Za-z0-9_]+"), "名字含非法字符：" + name);
        }
    }

    @Test
    void uuidDerivationIsStableAcrossCalls() {
        UUID first = BotNames.uuidOf("Novak");
        UUID second = BotNames.uuidOf("Novak");
        assertEquals(first, second);
    }

    @Test
    void differentNamesDeriveDifferentUuids() {
        assertNotEquals(BotNames.uuidOf("Novak"), BotNames.uuidOf("Ivanov"));
    }

    @Test
    void botUuidDoesNotCollideWithVanillaOfflinePlayerUuid() {
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:Novak").getBytes(StandardCharsets.UTF_8));
        assertNotEquals(offline, BotNames.uuidOf("Novak"),
                "bot UUID 必须与原版离线玩家命名空间区隔，否则同名真人会被顶掉");
    }

    @Test
    void uuidOfRejectsNull() {
        assertThrows(NullPointerException.class, () -> BotNames.uuidOf(null));
    }

    @Test
    void atWrapsAroundInBothDirections() {
        assertEquals(BotNames.at(0), BotNames.at(BotNames.size()));
        assertEquals(BotNames.at(BotNames.size() - 1), BotNames.at(-1));
    }

    @Test
    void pickReturnsRequestedCountOfDistinctNames() {
        List<String> picked = BotNames.pick(5, Set.of(), 42L);
        assertEquals(5, picked.size());
        assertEquals(5, new HashSet<>(picked).size());
    }

    @Test
    void pickSkipsTakenNamesCaseInsensitively() {
        String taken = BotNames.at(0);
        List<String> picked = BotNames.pick(BotNames.size(), Set.of(taken.toUpperCase()), 7L);
        assertFalse(picked.stream().anyMatch(n -> n.equalsIgnoreCase(taken)),
                "已占用名必须被跳过，否则 tab 列表与名牌会重名");
        assertEquals(BotNames.size() - 1, picked.size());
    }

    @Test
    void pickIsDeterministicForSameSeed() {
        assertEquals(BotNames.pick(6, Set.of(), 1234L), BotNames.pick(6, Set.of(), 1234L));
    }

    @Test
    void pickReturnsEmptyForNonPositiveCountAndCapsAtPoolSize() {
        assertTrue(BotNames.pick(0, Set.of(), 1L).isEmpty());
        assertTrue(BotNames.pick(-3, Set.of(), 1L).isEmpty());
        assertEquals(BotNames.size(), BotNames.pick(BotNames.size() + 10, null, 1L).size());
    }

    // ---------------- Steering ----------------

    @Test
    void wrapDegreesNormalisesToHalfOpenRange() {
        assertEquals(0.0F, Steering.wrapDegrees(360.0F), EPS);
        assertEquals(-180.0F, Steering.wrapDegrees(180.0F), EPS);
        assertEquals(-180.0F, Steering.wrapDegrees(-180.0F), EPS);
        assertEquals(-90.0F, Steering.wrapDegrees(270.0F), EPS);
        assertEquals(90.0F, Steering.wrapDegrees(-270.0F), EPS);
    }

    @Test
    void yawTowardMatchesMinecraftAxisConvention() {
        assertEquals(0.0F, Steering.yawToward(0, 1), EPS);      // +Z
        assertEquals(-90.0F, Steering.yawToward(1, 0), EPS);    // +X
        assertEquals(-180.0F, Steering.yawToward(0, -1), EPS);  // -Z
        assertEquals(90.0F, Steering.yawToward(-1, 0), EPS);    // -X
    }

    @Test
    void pitchTowardIsNegativeWhenLookingUp() {
        assertTrue(Steering.pitchToward(5, 5) < 0.0F);
        assertTrue(Steering.pitchToward(-5, 5) > 0.0F);
        assertEquals(0.0F, Steering.pitchToward(0, 5), EPS);
        assertEquals(-45.0F, Steering.pitchToward(5, 5), EPS);
    }

    @Test
    void pitchTowardHandlesZeroHorizontalDistance() {
        assertEquals(-90.0F, Steering.pitchToward(3, 0), EPS);
        assertEquals(90.0F, Steering.pitchToward(-3, 0), EPS);
        assertEquals(0.0F, Steering.pitchToward(0, 0), EPS);
    }

    @Test
    void pitchTowardIsClampedToLookLimits() {
        assertTrue(Steering.pitchToward(1000, 1) >= -90.0F);
        assertTrue(Steering.pitchToward(-1000, 1) <= 90.0F);
    }

    @Test
    void turnTowardTakesShortestArcAcrossTheWrapBoundary() {
        // 170° → -170° 的最短弧是 +20°，而非绕远路的 -340°
        assertEquals(175.0F, Steering.turnToward(170.0F, -170.0F, 5.0F), EPS);
        assertEquals(-175.0F, Steering.turnToward(-170.0F, 170.0F, 5.0F), EPS);
    }

    @Test
    void turnTowardStopsExactlyOnTargetWhenWithinRate() {
        assertEquals(90.0F, Steering.turnToward(0.0F, 90.0F, 200.0F), EPS);
    }

    @Test
    void turnTowardIsNoOpForNonPositiveRate() {
        assertEquals(30.0F, Steering.turnToward(30.0F, 120.0F, 0.0F), EPS);
        assertEquals(30.0F, Steering.turnToward(30.0F, 120.0F, -5.0F), EPS);
    }

    @Test
    void angleBetweenUsesShortestArc() {
        assertEquals(20.0F, Steering.angleBetween(170.0F, -170.0F), EPS);
        assertEquals(90.0F, Steering.angleBetween(0.0F, 90.0F), EPS);
        assertEquals(0.0F, Steering.angleBetween(45.0F, 45.0F), EPS);
    }
}
