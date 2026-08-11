package org.shee33.act0.arcade.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.bot.BotDifficultyRegistry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * bot 难度参数的 JSON 数据驱动读写，与 {@link AttachmentCatalogIO}（配件）平行。
 *
 * <p>文件位于 {@code config/act0_arcade/bot/difficulty.json}；首次运行时以内置默认值写出完整模板，
 * 便于管理员看到全部 12 个旋钮的出厂值。配合 {@code /arcade bot reload} 可在局内即时生效。
 *
 * <p><b>支持部分覆盖</b>：只需写出想改的键，其余自动沿用该档内置默认值。调参时最常见的操作
 * 就是只动一个数字，若要求整档 12 个键写全，实际会逼人复制粘贴、进而更容易手误。
 *
 * <p>格式：
 * <pre>
 * {
 *   "tiers": {
 *     "NORMAL": { "reactionTicks": 8, "errorSettledDegrees": 1.5 },
 *     "ELITE":  { "reactionTicks": 2 }
 *   }
 * }
 * </pre>
 */
public final class BotTuningIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 难度参数文件名。 */
    public static final String DIFFICULTY_FILE = "difficulty.json";

    private static final String TIERS = "tiers";

    private BotTuningIO() {
    }

    /**
     * 一次加载的结果。
     *
     * @param loaded   成功应用覆盖的档位数
     * @param seeded   是否因文件缺失而写出了默认模板
     * @param problems 可读的问题描述；非空不代表加载失败，对应档位已回退到内置默认值
     */
    public record LoadResult(int loaded, boolean seeded, List<String> problems) {
    }

    /**
     * 从配置目录加载难度覆盖并填充注册表。
     *
     * <p>注册表先整体复位为内置默认值，再逐档应用覆盖；任一档非法只影响该档，
     * 不会导致整体加载失败——管理员改坏一个数字不应让 bot 完全不可用。
     */
    public static LoadResult loadInto(BotDifficultyRegistry registry, Path botDir) throws IOException {
        Files.createDirectories(botDir);
        registry.resetToDefaults();

        Path file = botDir.resolve(DIFFICULTY_FILE);
        if (!Files.exists(file)) {
            writeTemplate(file);
            return new LoadResult(0, true, List.of());
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return new LoadResult(0, false, List.of(DIFFICULTY_FILE + " 根节点不是对象，已全部回退默认值"));
            }
            root = parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            return new LoadResult(0, false, List.of(DIFFICULTY_FILE + " JSON 语法错误：" + e.getMessage()));
        }

        if (!root.has(TIERS) || !root.get(TIERS).isJsonObject()) {
            return new LoadResult(0, false, List.of(DIFFICULTY_FILE + " 缺少 tiers 对象，已全部回退默认值"));
        }

        List<String> problems = new ArrayList<>();
        int loaded = 0;
        JsonObject tiers = root.getAsJsonObject(TIERS);
        for (Map.Entry<String, JsonElement> entry : tiers.entrySet()) {
            AimModel.Difficulty tier = parseTier(entry.getKey());
            if (tier == null) {
                problems.add("未知难度档：" + entry.getKey());
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                problems.add(tier + " 的值不是对象，已回退默认值");
                continue;
            }
            try {
                registry.put(tier, overlay(tier.defaults(), entry.getValue().getAsJsonObject()));
                loaded++;
            } catch (IllegalArgumentException e) {
                problems.add(tier + " 参数非法（" + e.getMessage() + "），已回退默认值");
            }
        }
        return new LoadResult(loaded, false, problems);
    }

    /** 把配置里出现的键覆盖到默认值上；未出现的键沿用默认。 */
    private static AimModel overlay(AimModel base, JsonObject json) {
        return new AimModel(
                readInt(json, "reactionTicks", base.reactionTicks()),
                readFloat(json, "fovHalfAngleDegrees", base.fovHalfAngleDegrees()),
                readFloat(json, "turnRateDegPerTick", base.turnRateDegPerTick()),
                readFloat(json, "errorInitialDegrees", base.errorInitialDegrees()),
                readFloat(json, "errorSettledDegrees", base.errorSettledDegrees()),
                readInt(json, "errorConvergeTicks", base.errorConvergeTicks()),
                readFloat(json, "errorPerShotDegrees", base.errorPerShotDegrees()),
                readFloat(json, "errorRecoveryPerTick", base.errorRecoveryPerTick()),
                readInt(json, "burstMinShots", base.burstMinShots()),
                readInt(json, "burstMaxShots", base.burstMaxShots()),
                readInt(json, "burstPauseTicks", base.burstPauseTicks()),
                readInt(json, "reacquireTicks", base.reacquireTicks()));
    }

    /** 以内置默认值写出完整模板（12 个键全写，便于管理员看到全部旋钮）。 */
    private static void writeTemplate(Path file) throws IOException {
        Map<String, Map<String, Number>> tiers = new LinkedHashMap<>();
        for (AimModel.Difficulty tier : AimModel.Difficulty.values()) {
            tiers.put(tier.name(), fullFields(tier.defaults()));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(TIERS, tiers);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static Map<String, Number> fullFields(AimModel m) {
        Map<String, Number> fields = new LinkedHashMap<>();
        fields.put("reactionTicks", m.reactionTicks());
        fields.put("fovHalfAngleDegrees", m.fovHalfAngleDegrees());
        fields.put("turnRateDegPerTick", m.turnRateDegPerTick());
        fields.put("errorInitialDegrees", m.errorInitialDegrees());
        fields.put("errorSettledDegrees", m.errorSettledDegrees());
        fields.put("errorConvergeTicks", m.errorConvergeTicks());
        fields.put("errorPerShotDegrees", m.errorPerShotDegrees());
        fields.put("errorRecoveryPerTick", m.errorRecoveryPerTick());
        fields.put("burstMinShots", m.burstMinShots());
        fields.put("burstMaxShots", m.burstMaxShots());
        fields.put("burstPauseTicks", m.burstPauseTicks());
        fields.put("reacquireTicks", m.reacquireTicks());
        return fields;
    }

    private static AimModel.Difficulty parseTier(String name) {
        try {
            return AimModel.Difficulty.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int readInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static float readFloat(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsFloat() : fallback;
    }
}
