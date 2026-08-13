package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.shee33.act0.arcade.network.RoomDto;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 房间大厅的「AI 士兵」席位行：bot 名单解析（供槽位标记）+ 撤走/添加/难度三个房主控件的
 * 布局、绘制与命中检测。
 *
 * <p>从 {@link RoomLobbyScreen} 拆出成独立类，而非继续堆在 Screen 里：Screen 本身已 400+ 行，
 * 本项目要求单文件控制在 250 行内；且 bot 名单解析、难度循环、可见槽位行数这些纯逻辑拆出来
 * 才能脱离 Minecraft 环境做 JUnit 单测。
 *
 * <p>本类只做绘制与命中判定，<b>不发任何指令</b>——网络副作用统一留在
 * {@link RoomLobbyScreen#mouseClicked}，避免副作用散进绘制层。
 *
 * <p>悬停态没有直接复用 {@link MenuChrome#drawLightControl} / {@link MenuChrome#drawGhostButton}：
 * 那两个原语的 {@code hovered} 是布尔量，无法表达"进入/离开都走对称补间"（项目规范硬要求，
 * 离开不许瞬间弹回）。故此处就地用 MenuChrome <b>公开的</b>透明度分层常量做插值绘制——
 * 色板、形状语言、层级语义与原语完全一致，只是把两级台阶换成连续补间。
 */
final class RoomLobbyBotPanel {

    /**
     * 难度五档：中文展示名与指令参数按下标一一对应，数组顺序即点击循环顺序。
     *
     * <p>这两份是 {@code AimModel.Difficulty} 的客户端副本——展示名要对上服务端经 RoomDto
     * 下发的 {@code displayName()}，指令参数要对上 {@code ArcadeCommand} 按 {@code values()}
     * 生成的命令字面量。副本一旦与枚举脱节就是静默故障：循环不到新档位，或发出服务端不认识
     * 的参数而毫无反馈。故由 {@code RoomLobbyBotPanelTest} 逐项锁住三者一致。
     */
    static final String[] DIFFICULTY_NAMES = {"新手", "普通", "高级", "写实", "终极"};
    static final String[] DIFFICULTY_ARGS = {"rookie", "normal", "advanced", "realistic", "ultimate"};

    /** 难度未知（房间条目来自进行中的对局，服务端不下发难度）时的占位，不臆造默认值。 */
    private static final String DIFFICULTY_UNKNOWN = "—";

    /** 真人槽位标记：实心圆点 + 亮色名字。 */
    static final String HUMAN_PREFIX = "§a● §f";
    /** bot 槽位标记：空心菱形 + 灰名字——"空心 vs 实心"是 0.3 秒内可辨的形状差异，不引入新色板。 */
    static final String BOT_PREFIX = "§8◇ §7";
    /** bot 槽位右端的诚实告知标签（大厅属于信息界面，与战斗中的悬浮名牌刻意不标记相反）。 */
    static final String BOT_TAG = "AI";

    // 行内几何：全部相对面板左边距 left+12（与底部按钮行同一左基线），偏移量取 8 的倍数
    private static final int LABEL_DX = 12;
    /** 撤走按钮左沿：与下方"人数 −"同列（left+92），两行竖直对齐成一个网格。 */
    private static final int REMOVE_DX = 92;
    /** 添加按钮左沿：与下方"人数 +"同列（left+180）。 */
    private static final int ADD_DX = 180;
    private static final int ADJUST_W = 24;
    /** 难度按钮左沿：left+228（相对左基线 216 = 8×27），右沿贴齐面板内缘。 */
    private static final int DIFF_DX = 228;
    private static final int ROW_H = 20;
    private static final int PAD = 8;

    /** 槽位网格下方要给"…还有 N 个槽位"那一行预留的高度：4px 间隙 + 8px 文字。 */
    private static final int OVERFLOW_RESERVE = 12;

    private final Set<String> botNames = new LinkedHashSet<>();

    private int rowY;
    private int removeX;
    private int addX;
    private int diffX;
    private int diffW;

    // 方向性滚轮的过渡状态（哪个旧值在滑出、往哪滑）
    private int lastBotCount = -1;
    private String countOldText = "";
    private int countDir = 1;
    private String lastDiffName;
    private String diffOldText = "";
    private int diffDir = 1;

    /** 三个控件的悬停朝向（true=正在进入），与 {@code anim.botHover[]} 下标对应。 */
    private final boolean[] hoverEntering = new boolean[RoomLobbyAnimator.HOVER_COUNT];

    /**
     * 采样房间状态：刷新 bot 名单并在数量/难度发生变化时启动方向性滚轮。
     *
     * <p>必须在槽位绘制<b>之前</b>调用——槽位要靠这里解析出的名单判断某一格是不是 bot。
     */
    void update(RoomDto room, long now, RoomLobbyAnimator anim) {
        botNames.clear();
        botNames.addAll(parseNames(room.botNames()));

        int count = botNames.size();
        if (lastBotCount >= 0 && count != lastBotCount) {
            countOldText = countText(lastBotCount);
            countDir = MenuChrome.scrollDir(lastBotCount, count);
            anim.botCountRoll.start(now);
        }
        lastBotCount = count;

        String diffName = displayDifficulty(room);
        if (lastDiffName != null && !lastDiffName.equals(diffName)) {
            diffOldText = lastDiffName;
            diffDir = cycleDir(difficultyIndex(lastDiffName), difficultyIndex(diffName), DIFFICULTY_NAMES.length);
            anim.botDiffRoll.start(now);
        }
        lastDiffName = diffName;
    }

    boolean isBot(String name) {
        return botNames.contains(name);
    }

    /** 房主且对局未开始才可增删——与服务端 {@code accessBotSeats} 的 WAITING 校验保持一致。 */
    private static boolean manageable(RoomDto room, boolean host) {
        return host && !room.inProgress();
    }

    boolean canAdd(RoomDto room, boolean host) {
        return manageable(room, host) && room.size() < room.capacity();
    }

    boolean canRemove(RoomDto room, boolean host) {
        return manageable(room, host) && !botNames.isEmpty();
    }

    boolean canSetDifficulty(RoomDto room, boolean host) {
        return manageable(room, host);
    }

    boolean hitRemove(double mx, double my) {
        return MenuChrome.inRect(mx, my, removeX, rowY, ADJUST_W, ROW_H);
    }

    boolean hitAdd(double mx, double my) {
        return MenuChrome.inRect(mx, my, addX, rowY, ADJUST_W, ROW_H);
    }

    boolean hitDifficulty(double mx, double my) {
        return MenuChrome.inRect(mx, my, diffX, rowY, diffW, ROW_H);
    }

    /** 下一档难度的指令参数：五档向前循环，未知当前值时从「新手」的下一档起算。 */
    String nextDifficultyArg() {
        int idx = difficultyIndex(lastDiffName);
        return DIFFICULTY_ARGS[MenuChrome.wrapIndex(Math.max(0, idx), 1, DIFFICULTY_ARGS.length)];
    }

    /**
     * 绘制分隔线 + 「AI 士兵」标签 + 撤走/计数滚轮/添加 + 难度循环按钮。
     *
     * @param dividerDy 分隔线相对 {@code top} 的偏移
     * @param rowDy     控件行相对 {@code top} 的偏移
     */
    void render(GuiGraphics gg, Font font, RoomDto room, boolean host, int mouseX, int mouseY,
                long now, RoomLobbyAnimator anim, int left, int top, int panelW, int dividerDy, int rowDy) {
        FlatTheme.divider(gg, left + LABEL_DX, top + dividerDy, panelW - LABEL_DX * 2);
        rowY = top + rowDy;
        removeX = left + REMOVE_DX;
        addX = left + ADD_DX;
        diffX = left + DIFF_DX;
        diffW = panelW - LABEL_DX - DIFF_DX;

        gg.drawString(font, "§7AI 士兵", left + LABEL_DX, rowY + 6, FlatTheme.TEXT_DIM, false);

        boolean removeEnabled = canRemove(room, host);
        boolean addEnabled = canAdd(room, host);
        boolean diffEnabled = canSetDifficulty(room, host);

        drawLight(gg, font, removeX, "-", removeEnabled,
                hoverT(anim, now, RoomLobbyAnimator.H_BOT_REMOVE, removeEnabled && hitRemove(mouseX, mouseY)),
                MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_BOT_REMOVE], now));

        int clipLeft = removeX + ADJUST_W;
        gg.enableScissor(clipLeft, rowY, addX, rowY + ROW_H);
        MenuChrome.drawRollY(gg, font, (clipLeft + addX) / 2, rowY + ROW_H / 2,
                countOldText, countText(botNames.size()), countDir, anim.botCountRoll, now,
                FlatTheme.TEXT_DIM, ROW_H);
        gg.disableScissor();

        drawLight(gg, font, addX, "+", addEnabled,
                hoverT(anim, now, RoomLobbyAnimator.H_BOT_ADD, addEnabled && hitAdd(mouseX, mouseY)),
                MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_BOT_ADD], now));

        drawDifficulty(gg, font, displayDifficulty(room), diffEnabled,
                hoverT(anim, now, RoomLobbyAnimator.H_BOT_DIFF, diffEnabled && hitDifficulty(mouseX, mouseY)),
                MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_BOT_DIFF], now), anim, now);
    }

    /**
     * 悬停进度 [0,1]：朝向翻转时重启 140ms 补间，离开走同一条曲线反向收回，绝不瞬变。
     * 与 {@code CreateRoomScreen} 里模式列表项的 {@code hoverEntering} 记账方式同一套。
     */
    private float hoverT(RoomLobbyAnimator anim, long now, int index, boolean hoveredNow) {
        if (hoveredNow != hoverEntering[index]) {
            hoverEntering[index] = hoveredNow;
            anim.botHover[index].start(now);
        }
        float e = anim.botHover[index].easedT(now, MenuTween.Ease.OUT_CUBIC);
        return hoverEntering[index] ? e : 1f - e;
    }

    private void drawLight(GuiGraphics gg, Font font, int x, String glyph, boolean enabled,
                           float hoverT, float pressScale) {
        final float bgA = enabled
                ? MenuChrome.lerp(MenuChrome.LIGHT_BG_ALPHA_IDLE, MenuChrome.LIGHT_BG_ALPHA_HOVER, hoverT)
                : MenuChrome.LIGHT_BG_ALPHA_DISABLED;
        final float textA = enabled
                ? MenuChrome.lerp(MenuChrome.LIGHT_TEXT_ALPHA_IDLE, 1f, hoverT)
                : MenuChrome.LIGHT_TEXT_ALPHA_DISABLED;
        MenuChrome.drawScaled(gg, x + ADJUST_W / 2, rowY + ROW_H / 2, pressScale, () -> {
            gg.fill(x, rowY, x + ADJUST_W, rowY + ROW_H, FlatTheme.withAlpha(MenuChrome.WHITE, bgA));
            gg.drawCenteredString(font, glyph, x + ADJUST_W / 2, rowY + 5,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, textA));
        });
    }

    /**
     * 难度按钮：幽灵按钮框 + 固定「难度」标签 + 右侧滚轮里的当前档位。
     *
     * <p>档位值恒用主文本色（不随禁用态变暗）：非房主也必须能一眼读到当前难度，禁用语义
     * 由边框与标签的透明度承担就够了。
     */
    private void drawDifficulty(GuiGraphics gg, Font font, String value, boolean enabled, float hoverT,
                                float pressScale, RoomLobbyAnimator anim, long now) {
        final float borderA = enabled
                ? MenuChrome.lerp(MenuChrome.GHOST_BORDER_ALPHA_IDLE, MenuChrome.GHOST_BORDER_ALPHA_HOVER, hoverT)
                : MenuChrome.GHOST_BORDER_ALPHA_DISABLED;
        final float labelA = enabled
                ? MenuChrome.lerp(MenuChrome.GHOST_TEXT_ALPHA_IDLE, 1f, hoverT)
                : MenuChrome.GHOST_TEXT_ALPHA_DISABLED;
        final int labelW = font.width("难度");
        MenuChrome.drawScaled(gg, diffX + diffW / 2, rowY + ROW_H / 2, pressScale, () -> {
            int border = FlatTheme.withAlpha(MenuChrome.WHITE, borderA);
            gg.fill(diffX, rowY, diffX + diffW, rowY + 1, border);
            gg.fill(diffX, rowY + ROW_H - 1, diffX + diffW, rowY + ROW_H, border);
            gg.fill(diffX, rowY, diffX + 1, rowY + ROW_H, border);
            gg.fill(diffX + diffW - 1, rowY, diffX + diffW, rowY + ROW_H, border);
            gg.drawString(font, "难度", diffX + PAD, rowY + 6,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, labelA), false);
            int clipLeft = diffX + PAD + labelW + 6;
            int clipRight = diffX + diffW - PAD;
            gg.enableScissor(clipLeft, rowY + 1, clipRight, rowY + ROW_H - 1);
            MenuChrome.drawRollY(gg, font, (clipLeft + clipRight) / 2, rowY + ROW_H / 2,
                    diffOldText, value, diffDir, anim.botDiffRoll, now, FlatTheme.TEXT, ROW_H);
            gg.disableScissor();
        });
    }

    // ============================================================
    // 纯逻辑（RoomLobbyBotPanelTest 覆盖）
    // ============================================================

    private static String countText(int count) {
        return count + " 名";
    }

    static String displayDifficulty(RoomDto room) {
        String name = room.botDifficulty();
        return name == null || name.isBlank() ? DIFFICULTY_UNKNOWN : name;
    }

    static int difficultyIndex(String name) {
        for (int i = 0; i < DIFFICULTY_NAMES.length; i++) {
            if (DIFFICULTY_NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 环形序列的滚动方向：正向距离不超过半圈算 {@code +1}，否则 {@code -1}——
     * 这样"终极→新手"的循环回绕仍读作向前（+1），而被别人改回上一档才读作 {@code -1}。
     * 任一侧索引未知时默认 {@code +1}，宁可方向猜错也不能不播动画。
     */
    static int cycleDir(int from, int to, int size) {
        if (from == to) {
            return 0;
        }
        if (size <= 0 || from < 0 || to < 0) {
            return 1;
        }
        return Math.floorMod(to - from, size) * 2 <= size ? 1 : -1;
    }

    static Set<String> parseNames(String raw) {
        Set<String> names = new LinkedHashSet<>();
        if (raw == null || raw.isBlank() || raw.equals("-")) {
            return names;
        }
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 可见槽位行数：网格加上"…还有 N 个槽位"那一行都必须落在 {@code limitDy} 之上，
     * 否则槽位会压到队伍选择行或本类的 AI 士兵行上（原先固定画满 12 格正是这样压到了下方元素）。
     */
    static int visibleSlotRows(int startDy, int pitch, int limitDy, int maxRows) {
        int rows = 0;
        while (rows < maxRows && startDy + pitch * (rows + 1) + OVERFLOW_RESERVE <= limitDy) {
            rows++;
        }
        return Math.max(1, rows);
    }
}
