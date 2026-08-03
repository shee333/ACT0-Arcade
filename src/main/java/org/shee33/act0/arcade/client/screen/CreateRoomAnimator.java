package org.shee33.act0.arcade.client.screen;

/**
 * 「创建房间」界面的动画状态聚合 —— 规格文档 §3 / §4 / §5 罗列的开场级联、选择动效、
 * 点击后动画的状态持有器。{@link CreateRoomScreen} 每帧 {@code render()} 直接读这里的
 * {@link MenuTween.Anim} 采样进度，无需自己做时间戳管理。
 *
 * <p><b>本类当前是骨架版本</b>：只搭好字段（每个动效项一个 {@code Anim}）+ 简单 getter
 * + 纯逻辑辅助方法（{@link #scrollDir} / {@link #clamp01} / {@link #lerp} /
 * {@link #wrapIndex}）。{@code render()} 绘制逻辑留给下一轮任务。
 *
 * <p>字段与 §3/§4/§5 表格一一对应：
 * <ul>
 *   <li>§3 开场：{@link #backdrop}（遮罩）/ {@link #panel}（面板透明+位移+缩放）/
 *       {@link #titleLine}（标题金线 scaleX）/ {@link #leftColumn}（10 个 .li 项 35ms/个）/
 *       {@link #rightColumn}（6 个 .sec 区块 60ms/个，起点 120ms）/
 *       {@link #footer}（底部操作行 opacity）</li>
 *   <li>§4 选中：{@link #indicatorY}（滑动指示器 220ms outCubic）/
 *       {@link #scoreRoll}（计分目标方向性滚轮 190ms outCubic）/
 *       {@link #playersExpand}（房间人数区块收展 240ms outCubic）/
 *       {@link #weaponSweep}（武器切换扫光 400ms outCubic）；
 *       {@link #hoverProgress}[i] 是第 i 个模式项的悬停进度（140ms outCubic）</li>
 *   <li>§5 点击：{@link #scoreTimePlayersRoll}[]（数值滚轮 190ms outCubic × 3：计分/限时/人数）/
 *       {@link #arenaSlide}（竞技场横向滚动 220ms outCubic）/
 *       {@link #createSweep}（创建按钮扫光 350ms outCubic）/
 *       {@link #createColorShift}（创建按钮颜色过渡 150ms outCubic）</li>
 * </ul>
 */
final class CreateRoomAnimator {

    /** 左栏 .li 项总数：标签("选择模式") + 8 个模式 + "对局设定"按钮。 */
    private static final int LEFT_COLUMN_ITEMS = 10;
    /** 右栏 .sec 区块数：竞技场 / 计分目标 / 限时 / 房间人数 / 随机武器 / 底部说明。 */
    private static final int RIGHT_COLUMN_SECTIONS = 6;
    /** 可调节数值行的数量（计分 / 限时 / 房间人数），各自独立的方向性滚轮。 */
    private static final int ROLLABLE_ROWS = 3;
    /** 默认模式项数（与 {@code CreateRoomScreen.MODES} 同步，hoverProgress 数组按此分配）。 */
    private static final int DEFAULT_MODES = 8;

    // ============================================================
    // §3 开场级联
    // ============================================================

    /** 遮罩 opacity：0 → 0.45，200ms outCubic，0ms 延迟（#1）。 */
    final MenuTween.Anim backdrop;
    /** 面板 opacity + translateY(12→0) + scale(0.97→1)，300ms outCubic，0ms 延迟（#2）。 */
    final MenuTween.Anim panel;
    /** 标题金线 scaleX（origin left）：0 → 1，350ms outExpo，**300ms 延迟**（面板完成后 #3）。 */
    final MenuTween.Anim titleLine;
    /** 左栏 .li 项（标签 + 8 模式 + 对局设定）的入场，每个 260ms outCubic，延迟 300 + i×35ms（#4）。 */
    final MenuTween.Anim[] leftColumn;
    /** 右栏 .sec 区块（6 个）的入场，每个 280ms outCubic，延迟 120 + i×60ms（#5）。 */
    final MenuTween.Anim[] rightColumn;
    /** 底部操作行 opacity：0 → 1，250ms outCubic，延迟 120 + 区块数×60ms（#6）。 */
    final MenuTween.Anim footer;

    // ============================================================
    // §4 选中动效
    // ============================================================

    /** 滑动指示器 Y 位置补间（应用于实际 Y 时按当前/目标项 offsetTop lerp）：220ms outCubic。 */
    final MenuTween.Anim indicatorY;
    /** 计分目标方向性滚轮（+1 新值自下、-1 自上）：190ms outCubic。 */
    final MenuTween.Anim scoreRoll;
    /** 房间人数区块高度 + opacity + margin-bottom 同步补间：240ms outCubic。 */
    final MenuTween.Anim playersExpand;
    /** 武器切换扫光（left −40% → 140%）：400ms outCubic。 */
    final MenuTween.Anim weaponSweep;

    /** 每个模式项的悬停进度 0↔1：140ms outCubic；离开也要补间，不许瞬变（规格 §4.3）。 */
    final MenuTween.Anim[] hoverProgress;

    // ============================================================
    // §5 点击后动画
    // ============================================================

    /** 数值滚轮（计分目标 / 对局限时 / 房间人数），共 {@value #ROLLABLE_ROWS} 条，每条 190ms outCubic。 */
    final MenuTween.Anim[] scoreTimePlayersRoll;
    /** 竞技场横向滚动（▶ 新值自右、◀ 自左）：220ms outCubic。 */
    final MenuTween.Anim arenaSlide;
    /** 创建按钮扫光：350ms outCubic。 */
    final MenuTween.Anim createSweep;
    /** 创建按钮颜色过渡（金 → 绿）：150ms outCubic。 */
    final MenuTween.Anim createColorShift;

    /**
     * 构造时即按规格文档铺设所有 {@link MenuTween.Anim}，并在 {@code nowMs} 启动
     * 开场级联（其余动效按用户交互在具体操作时再单独 {@code start}）。
     *
     * @param nowMs     开屏时刻（{@link MenuTween#now()} 或测试传入的固定时间戳）
     * @param modeCount 模式项数（影响 {@link #hoverProgress} 长度）
     */
    CreateRoomAnimator(long nowMs, int modeCount) {
        this(nowMs, modeCount, LEFT_COLUMN_ITEMS, RIGHT_COLUMN_SECTIONS);
    }

    /** 完整构造器（测试可注入不同的级联规模）。 */
    CreateRoomAnimator(long nowMs, int modeCount, int leftItems, int rightSections) {
        backdrop = new MenuTween.Anim(200L, 0L);
        panel = new MenuTween.Anim(300L, 0L);
        titleLine = new MenuTween.Anim(350L, 300L);

        leftColumn = new MenuTween.Anim[leftItems];
        for (int i = 0; i < leftColumn.length; i++) {
            leftColumn[i] = new MenuTween.Anim(260L, 300L + (long) i * 35L);
        }
        rightColumn = new MenuTween.Anim[rightSections];
        for (int i = 0; i < rightColumn.length; i++) {
            rightColumn[i] = new MenuTween.Anim(280L, 120L + (long) i * 60L);
        }
        footer = new MenuTween.Anim(250L, 120L + (long) rightSections * 60L);

        indicatorY = new MenuTween.Anim(220L, 0L);
        scoreRoll = new MenuTween.Anim(190L, 0L);
        playersExpand = new MenuTween.Anim(240L, 0L);
        weaponSweep = new MenuTween.Anim(400L, 0L);

        hoverProgress = new MenuTween.Anim[Math.max(0, modeCount)];
        for (int i = 0; i < hoverProgress.length; i++) {
            hoverProgress[i] = new MenuTween.Anim(140L, 0L);
        }

        scoreTimePlayersRoll = new MenuTween.Anim[ROLLABLE_ROWS];
        for (int i = 0; i < scoreTimePlayersRoll.length; i++) {
            scoreTimePlayersRoll[i] = new MenuTween.Anim(190L, 0L);
        }
        arenaSlide = new MenuTween.Anim(220L, 0L);
        createSweep = new MenuTween.Anim(350L, 0L);
        createColorShift = new MenuTween.Anim(150L, 0L);

        // 启动开场级联（#1 ~ #6）。其它字段按用户交互在具体操作时再单独 start()。
        backdrop.start(nowMs);
        panel.start(nowMs);
        titleLine.start(nowMs);
        for (MenuTween.Anim a : leftColumn) {
            a.start(nowMs);
        }
        for (MenuTween.Anim a : rightColumn) {
            a.start(nowMs);
        }
        footer.start(nowMs);
    }

    /** 默认模式数（{@value #DEFAULT_MODES}，与 {@code CreateRoomScreen.MODES} 同步）。 */
    static int defaultModeCount() {
        return DEFAULT_MODES;
    }

    // ============================================================
    // 纯逻辑辅助方法（可单测，下面 CreateRoomAnimatorTest 覆盖 scrollDir 等行为）
    // ============================================================

    /**
     * 滚动方向：目标索引更大 → {@code +1}（新值自下向上），更小 → {@code -1}（自上向下），
     * 相同 → {@code 0}（规格 §4.2 右栏联动 dir 决定方向性滚轮的走向）。
     */
    static int scrollDir(int from, int to) {
        return Integer.compare(to, from);
    }

    /** 把任意浮点钳制到 [0,1]：渲染时叠加变换用的兜底。 */
    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** 在钳制 {@code t}∈[0,1] 的前提下做线性插值 {@code a + (b-a)*t}。 */
    static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /** 循环容器索引推进：{@code (current + dir) mod size}，负值安全（规格 §5 竞技场左右切换）。 */
    static int wrapIndex(int current, int dir, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(current + dir, size);
    }
}
