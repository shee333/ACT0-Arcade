package org.shee33.act0.arcade.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「房间浏览器」的纯逻辑层 —— 状态派生、筛选判据、详情抽屉设定键值对生成、摇头位移公式、
 * 以及「新旧两帧快照差分」。
 *
 * <p>本类<b>不引用任何 Minecraft 类型</b>（连 {@code RoomDto} 也不引用，改用本类自带的
 * {@link Snapshot} 纯记录做输入），因此可以直接跑 JUnit（见 {@code RoomBrowserModelTest}）——
 * 与 {@link MenuTween} 保持同一条"可单测的纯逻辑与 GUI 绘制彻底分离"的原则。
 *
 * <p><b>关于"实时跳动"</b>：规格文档第 2.3 节的浏览器 A 原型是每 2.6s 客户端自己伪造一个
 * 随机人数 ±1。本仓库有真实数据源（服务端 {@code SyncRoomListPacket} 定期推送），因此这里
 * 不造假随机数，而是把"实时跳动"实现为 {@link #diff(List, List)}：新快照到达时与上一帧缓存
 * 逐房间比对，人数/状态有变化的行才播一次方向性滚轮换字 + 600ms 蓝色行高亮衰减。
 */
final class RoomBrowserModel {

    private RoomBrowserModel() {
    }

    // ============================================================
    // 配色（规格文档 §1.1 / §2.1 原样照抄，不引入新色值）
    // ============================================================

    /** 等待中：绿 {@code #6ee27e}。 */
    static final int STATUS_WAITING = 0xFF6EE27E;
    /** 进行中：金/黄 {@code #ffd76a}（与 {@code MenuChrome.GOLD} 同值，语义不同故各自命名）。 */
    static final int STATUS_RUNNING = 0xFFFFD76A;
    /** 已满：中性文字色 {@code #e8edf2} @ 0.35。 */
    static final int STATUS_FULL = 0x59E8EDF2;
    /** 实时行高亮（数据变动闪一下）：蓝 {@code #4fa8ff}。 */
    static final int ROW_FLASH = 0xFF4FA8FF;
    /** 刷新扫描线：与行高亮同蓝。 */
    static final int SCAN_LINE = 0xFF4FA8FF;

    /** 已满态状态格的文字不透明度（规格：灰 0.35）。 */
    static final float FULL_TEXT_ALPHA = 0.35f;
    /** 实时行高亮的峰值不透明度（规格：α 0.06→0）。 */
    static final float ROW_FLASH_MAX_ALPHA = 0.06f;

    // ============================================================
    // 筛选标签
    // ============================================================

    static final int TAB_ALL = 0;
    static final int TAB_WAITING = 1;
    static final int TAB_RUNNING = 2;
    /**
     * 规格文档第四个标签是「好友」（依赖 {@code friend} 字段）。本仓库<b>没有任何好友系统</b>
     * （无好友名单存储、无好友关系包、服务端也没有可派生该标记的数据），凭空捏造一个假的好友
     * 概念既不能真的过滤，也会在 UI 上撒谎。因此按同等价值替换为「我的」：筛选出玩家自己已在
     * 其中的房间（{@code youAreMember}），这是本仓库真实存在、且与「好友」同类的"这一行跟我
     * 有关系"语义。
     */
    static final int TAB_MINE = 3;

    static final String[] TAB_LABELS = {"全部", "等待中", "进行中", "我的"};

    // ============================================================
    // 状态派生
    // ============================================================

    /** 房间状态三态（规格 §2.1：{@code cur>=max→已满}，{@code run→进行中}，否则{@code 等待中}）。 */
    enum Status {
        WAITING("等待中", STATUS_WAITING),
        RUNNING("进行中", STATUS_RUNNING),
        FULL("已满", STATUS_FULL);

        private final String label;
        private final int color;

        Status(String label, int color) {
            this.label = label;
            this.color = color;
        }

        String label() {
            return label;
        }

        int color() {
            return color;
        }

        /** 已满态整格降到 0.35 不透明度；其余两态实色。 */
        float textAlpha() {
            return this == FULL ? FULL_TEXT_ALPHA : 1f;
        }
    }

    /** 判定顺序严格照抄规格：满员优先于进行中，进行中优先于等待中。 */
    static Status status(int cur, int max, boolean run) {
        if (max > 0 && cur >= max) {
            return Status.FULL;
        }
        return run ? Status.RUNNING : Status.WAITING;
    }

    /** 标签页筛选判据（规格 §2.1，第四页语义见 {@link #TAB_MINE}）。 */
    static boolean pass(int tab, int cur, int max, boolean run, boolean mine) {
        return switch (tab) {
            case TAB_WAITING -> !run && cur < max;
            case TAB_RUNNING -> run;
            case TAB_MINE -> mine;
            default -> true;
        };
    }

    // ============================================================
    // 详情抽屉：设定键值对（规格 §2.2 的 DSET，值按模式语义生成）
    // ============================================================

    /** 抽屉里一行"键 : 值"。 */
    record Setting(String key, String value) {
    }

    /**
     * 计分目标的键名按模式语义生成 —— 对应规格 DSET 里"先到 50 杀 / 先到 200 分 / 先到 20 级 /
     * 先到 5 胜"那套按模式分支的判断，只是本仓库的数值由服务端 {@code targetText} 提供真实值，
     * 这里只负责选一个说得通的键名，不自己编造数值。
     */
    static String targetKey(String modeId) {
        return switch (modeId == null ? "" : modeId) {
            case "team_deathmatch", "free_for_all" -> "击杀目标";
            case "hot_zone" -> "分数目标";
            case "arms_race", "team_arms_race" -> "等级目标";
            case "duel_1v1", "duel_2v2", "jump_sniper" -> "回合目标";
            default -> "计分目标";
        };
    }

    /**
     * 对局限时的值：不限时 → 「不限时」；进行中 → 「剩余 m:ss」（限时减去已用时，钳到 0）；
     * 等待中 → 「n 分钟」。
     */
    static String timeLimitText(int timeLimitSeconds, boolean run, int elapsedSeconds) {
        if (timeLimitSeconds <= 0) {
            return run ? "不限时 · 已进行 " + clock(elapsedSeconds) : "不限时";
        }
        if (run) {
            return "剩余 " + clock(Math.max(0, timeLimitSeconds - Math.max(0, elapsedSeconds)));
        }
        int minutes = timeLimitSeconds / 60;
        return minutes > 0 ? minutes + " 分钟" : timeLimitSeconds + " 秒";
    }

    /** {@code m:ss} 时钟文本。 */
    static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    /**
     * 抽屉设定区的六行：模式 / 地图 / 计分目标 / 对局限时 / 随机武器 / 友伤
     * （顺序与规格 §2.2 一致：前两行来自列数据，后四行来自 DSET）。
     */
    static List<Setting> settings(String modeId, String modeName, String arenaId, String targetText,
                                  int timeLimitSeconds, boolean run, int elapsedSeconds,
                                  boolean randomWeapons, boolean friendlyFire) {
        List<Setting> out = new ArrayList<>(6);
        out.add(new Setting("模式", blankToDash(modeName)));
        out.add(new Setting("地图", blankToDash(arenaId)));
        out.add(new Setting(targetKey(modeId), blankToDash(targetText)));
        out.add(new Setting("对局限时", timeLimitText(timeLimitSeconds, run, elapsedSeconds)));
        out.add(new Setting("随机武器", randomWeapons ? "开" : "关"));
        out.add(new Setting("友伤", friendlyFire ? "开" : "关"));
        return out;
    }

    private static String blankToDash(String raw) {
        return raw == null || raw.isBlank() ? "—" : raw;
    }

    // ============================================================
    // 摇头拒绝 / 滚轮方向
    // ============================================================

    /**
     * 满员时点击加入按钮的"摇头拒绝"横向位移（规格 §2.2）：{@code sin(v·4π)·4·(1-v)} px。
     * {@code v} 为 320ms linear 补间进度，末尾自然收敛到 0。
     */
    static float shakeOffset(float v) {
        float t = Math.max(0f, Math.min(1f, v));
        return (float) Math.sin(t * Math.PI * 4.0) * 4f * (1f - t);
    }

    /**
     * 滚轮方向 = 语义（规格 §1.3）：数值增加向上滚（{@code +1}）、减少向下滚（{@code -1}）、
     * 不变 {@code 0}（不播动画）。
     */
    static int rollDir(int oldValue, int newValue) {
        return Integer.compare(newValue, oldValue);
    }

    // ============================================================
    // 新旧两帧快照差分（取代原型的伪随机跳动）
    // ============================================================

    /** 一行在某一帧的可动数据（人数与是否进行中；状态由 {@link #status} 派生）。 */
    record Snapshot(String roomId, int cur, int max, boolean run) {
    }

    /**
     * 某一行在两帧之间的变化。
     *
     * @param roomId         行标识
     * @param countDir       人数滚轮方向（{@code +1}/{@code -1}），人数未变则为 {@code 0}
     * @param statusChanged  派生状态（等待中/进行中/已满）是否翻转 —— 翻转时状态格也要滚轮换字换色
     */
    record RowDelta(String roomId, int countDir, boolean statusChanged) {
    }

    /**
     * 比对上一帧与新一帧快照，返回需要播动效的行。
     *
     * <p>只出现在 {@code next} 里的新房间不算"变动"（它们走的是级联入场，不该额外闪一下）；
     * 只出现在 {@code prev} 里的已消失房间同样忽略。
     */
    static List<RowDelta> diff(List<Snapshot> prev, List<Snapshot> next) {
        Map<String, Snapshot> before = new LinkedHashMap<>();
        if (prev != null) {
            for (Snapshot s : prev) {
                before.put(s.roomId(), s);
            }
        }
        List<RowDelta> out = new ArrayList<>();
        if (next == null) {
            return out;
        }
        for (Snapshot now : next) {
            Snapshot old = before.get(now.roomId());
            if (old == null) {
                continue;
            }
            int dir = rollDir(old.cur(), now.cur());
            boolean flipped = status(old.cur(), old.max(), old.run()) != status(now.cur(), now.max(), now.run());
            if (dir != 0 || flipped) {
                out.add(new RowDelta(now.roomId(), dir, flipped));
            }
        }
        return out;
    }
}
