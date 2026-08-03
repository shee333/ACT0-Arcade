package org.shee33.act0.arcade.mode;

import java.util.List;

/**
 * 创建房间界面的纯数据/纯逻辑目录：从 {@code CreateRoomScreen} 中抽取出来，便于
 * 单元测试与未来的 UI 重写复用。
 *
 * <p>本类是 MC-free 的——不依赖任何 Minecraft / Forge / 客户端 API；只持有：
 * <ul>
 *   <li>{@link ModeDef} —— 单个模式的元数据 record</li>
 *   <li>{@link #MODES} —— 全部 8 个模式的常量列表（顺序与字段值与原 {@code CreateRoomScreen} 完全一致）</li>
 *   <li>{@link #quoteArg(String)} —— 命令行参数引号转义</li>
 *   <li>{@link #nearestIndex(int[], int)} / {@link #nearestIndex(double[], double)}
 *       —— 在一组离散值中找最接近的索引（平局返回首次出现）</li>
 * </ul>
 *
 * <p>本类目前与 {@code CreateRoomScreen} 内私有副本并存；下一轮 UI 重写任务会把
 * 屏幕代码切换到引用本类，并删除屏幕里的私有副本。
 */
public final class CreateRoomModeCatalog {

    private CreateRoomModeCatalog() {
        // 工具类：禁止实例化。
    }

    /**
     * 模式定义：id、显示名、是否击杀制、目标单位、默认目标、目标下限、目标上限、步长。
     *
     * <p>字段顺序与取值与原 {@code CreateRoomScreen.ModeDef} 完全一致。
     */
    public record ModeDef(String id, String name, boolean killBased,
                          String targetUnit, int defTarget,
                          int minTarget, int maxTarget, int step) {
    }

    /** 全部 8 个模式，顺序与字段值与 {@code CreateRoomScreen} 私有 {@code MODES} 一致。 */
    public static final List<ModeDef> MODES = List.of(
            new ModeDef("duel_1v1", "单挑 1v1", false, "胜", 3, 1, 15, 1),
            new ModeDef("duel_2v2", "2v2", false, "胜", 3, 1, 15, 1),
            new ModeDef("team_deathmatch", "团队死斗", true, "杀", 30, 5, 99, 5),
            new ModeDef("hot_zone", "热区", false, "分", 150, 30, 300, 10),
            new ModeDef("arms_race", "军备竞赛", false, "级", 8, 4, 16, 1),
            new ModeDef("team_arms_race", "军备竞赛 团队", false, "级", 8, 4, 16, 1),
            new ModeDef("free_for_all", "个人乱斗", true, "杀", 20, 5, 99, 5),
            new ModeDef("jump_sniper", "跳狙飞人", false, "胜", 5, 1, 15, 1));

    /**
     * 将字符串包裹成带双引号的命令行参数，并对内部反斜杠与双引号进行转义。
     *
     * <p>保持与原 {@code CreateRoomScreen.quoteArg} 完全一致的语义：
     * 先转反斜杠（{@code \} → {@code \\}），再转双引号（{@code "} → {@code \"}）。
     */
    public static String quoteArg(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * 在一组离散整数值中找最接近 {@code current} 的索引。平局时返回首次出现的索引。
     */
    public static int nearestIndex(int[] values, int current) {
        int best = 0;
        int diff = Integer.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            int d = Math.abs(values[i] - current);
            if (d < diff) {
                diff = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * 在一组离散浮点值中找最接近 {@code current} 的索引。平局时返回首次出现的索引。
     */
    public static int nearestIndex(double[] values, double current) {
        int best = 0;
        double diff = Double.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            double d = Math.abs(values[i] - current);
            if (d < diff) {
                diff = d;
                best = i;
            }
        }
        return best;
    }
}
