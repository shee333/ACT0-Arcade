package org.shee33.act0.arcade.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shee33.act0.arcade.storage.BotTuningIO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bot 难度配置（MC-free）单元测试：{@link BotDifficultyRegistry} 回退语义 +
 * {@link BotTuningIO} 的 JSON 往返、部分覆盖与容错。
 *
 * <p>把"更高难度更强"这条守卫留在 {@code AimTest} 里针对<b>内置默认值</b>强制保证；
 * 本文件只验证配置层不会把它悄悄破坏掉（违反时报告而非拒绝）。
 */
class BotTuningTest {

    private static final AimModel.Difficulty NORMAL = AimModel.Difficulty.NORMAL;

    // ---------------- BotDifficultyRegistry ----------------

    @Test
    void newRegistryStartsAtBuiltInDefaults() {
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        for (AimModel.Difficulty tier : AimModel.Difficulty.values()) {
            assertEquals(tier.defaults(), registry.get(tier));
            assertFalse(registry.isOverridden(tier));
        }
    }

    @Test
    void putOverridesAndResetRestores() {
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        AimModel tweaked = faster(NORMAL.defaults());
        registry.put(NORMAL, tweaked);

        assertEquals(tweaked, registry.get(NORMAL));
        assertTrue(registry.isOverridden(NORMAL));
        assertNotEquals(NORMAL.defaults(), registry.get(NORMAL));

        registry.resetToDefault(NORMAL);
        assertEquals(NORMAL.defaults(), registry.get(NORMAL));
        assertFalse(registry.isOverridden(NORMAL));
    }

    @Test
    void defaultsSatisfyMonotonicityInvariant() {
        assertTrue(new BotDifficultyRegistry().monotonicityViolations().isEmpty(),
                "内置默认值必须满足更高难度更强");
    }

    @Test
    void monotonicityViolationsAreReportedNotThrown() {
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        // 把 ULTIMATE 的反应调得比 ADVANCED 还慢，故意打破单调性
        AimModel elite = AimModel.Difficulty.ULTIMATE.defaults();
        registry.put(AimModel.Difficulty.ULTIMATE, withReactionTicks(elite, 99));

        List<String> violations = registry.monotonicityViolations();
        assertFalse(violations.isEmpty(), "应报告违反项");
        assertTrue(violations.stream().anyMatch(v -> v.contains("反应应更快")),
                "应指出具体违反的规则，实际：" + violations);
    }

    // ---------------- BotTuningIO ----------------

    @Test
    void seedsTemplateWhenFileAbsentAndTemplateReloadsToDefaults(@TempDir Path dir) throws IOException {
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult first = BotTuningIO.loadInto(registry, dir);

        assertTrue(first.seeded(), "首次运行应写出模板");
        assertEquals(0, first.loaded());
        Path file = dir.resolve(BotTuningIO.DIFFICULTY_FILE);
        assertTrue(Files.exists(file));

        // 模板是内置默认值的完整快照，因此再次加载应把五档全部读回且与默认值一致
        BotTuningIO.LoadResult second = BotTuningIO.loadInto(registry, dir);
        assertFalse(second.seeded());
        assertEquals(AimModel.Difficulty.values().length, second.loaded());
        assertTrue(second.problems().isEmpty(), "模板不应产生任何问题：" + second.problems());
        for (AimModel.Difficulty tier : AimModel.Difficulty.values()) {
            assertEquals(tier.defaults(), registry.get(tier), tier + " 往返后应等于默认值");
        }
    }

    @Test
    void partialOverrideKeepsUnspecifiedKeysAtDefaults(@TempDir Path dir) throws IOException {
        write(dir, "{ \"tiers\": { \"NORMAL\": { \"reactionTicks\": 3 } } }");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult result = BotTuningIO.loadInto(registry, dir);

        assertEquals(1, result.loaded());
        assertTrue(result.problems().isEmpty());
        AimModel loaded = registry.get(NORMAL);
        AimModel defaults = NORMAL.defaults();
        assertEquals(3, loaded.reactionTicks(), "指定的键应被覆盖");
        assertEquals(defaults.errorSettledDegrees(), loaded.errorSettledDegrees(),
                "未指定的键应沿用默认值");
        assertEquals(defaults.burstMaxShots(), loaded.burstMaxShots());
    }

    @Test
    void otherTiersUntouchedWhenOnlyOneIsOverridden(@TempDir Path dir) throws IOException {
        write(dir, "{ \"tiers\": { \"ROOKIE\": { \"reactionTicks\": 20 } } }");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.loadInto(registry, dir);

        assertTrue(registry.isOverridden(AimModel.Difficulty.ROOKIE));
        assertFalse(registry.isOverridden(AimModel.Difficulty.ADVANCED));
        assertEquals(AimModel.Difficulty.ADVANCED.defaults(), registry.get(AimModel.Difficulty.ADVANCED));
    }

    @Test
    void unknownTierIsReportedAndIgnored(@TempDir Path dir) throws IOException {
        write(dir, "{ \"tiers\": { \"IMPOSSIBLE\": { \"reactionTicks\": 1 } } }");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult result = BotTuningIO.loadInto(registry, dir);

        assertEquals(0, result.loaded());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("IMPOSSIBLE")),
                "应报告未知档名，实际：" + result.problems());
    }

    @Test
    void illegalValueFallsBackToThatTierOnly(@TempDir Path dir) throws IOException {
        // turnRateDegPerTick 必须 > 0，AimModel 的构造器会拒绝
        write(dir, "{ \"tiers\": {"
                + " \"NORMAL\": { \"turnRateDegPerTick\": 0 },"
                + " \"ADVANCED\": { \"reactionTicks\": 4 } } }");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult result = BotTuningIO.loadInto(registry, dir);

        assertEquals(1, result.loaded(), "只有 ADVANCED 应加载成功");
        assertFalse(result.problems().isEmpty());
        assertEquals(NORMAL.defaults(), registry.get(NORMAL), "非法档应回退默认值");
        assertEquals(4, registry.get(AimModel.Difficulty.ADVANCED).reactionTicks(),
                "同文件中的合法档不应受影响");
    }

    @Test
    void malformedJsonLeavesAllDefaults(@TempDir Path dir) throws IOException {
        write(dir, "{ this is not json");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult result = BotTuningIO.loadInto(registry, dir);

        assertEquals(0, result.loaded());
        assertFalse(result.problems().isEmpty());
        for (AimModel.Difficulty tier : AimModel.Difficulty.values()) {
            assertEquals(tier.defaults(), registry.get(tier));
        }
    }

    @Test
    void missingTiersObjectIsReported(@TempDir Path dir) throws IOException {
        write(dir, "{ \"somethingElse\": {} }");
        BotDifficultyRegistry registry = new BotDifficultyRegistry();
        BotTuningIO.LoadResult result = BotTuningIO.loadInto(registry, dir);

        assertEquals(0, result.loaded());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("tiers")),
                "应报告缺少 tiers，实际：" + result.problems());
    }

    // ---------------- 辅助 ----------------

    private static void write(Path dir, String json) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(BotTuningIO.DIFFICULTY_FILE), json, StandardCharsets.UTF_8);
    }

    private static AimModel faster(AimModel base) {
        return withReactionTicks(base, Math.max(0, base.reactionTicks() - 1));
    }

    private static AimModel withReactionTicks(AimModel base, int reactionTicks) {
        return new AimModel(reactionTicks, base.fovHalfAngleDegrees(), base.turnRateDegPerTick(),
                base.errorInitialDegrees(), base.errorSettledDegrees(), base.errorConvergeTicks(),
                base.errorPerShotDegrees(), base.errorRecoveryPerTick(),
                base.burstMinShots(), base.burstMaxShots(), base.burstPauseTicks(),
                base.reacquireTicks());
    }
}
