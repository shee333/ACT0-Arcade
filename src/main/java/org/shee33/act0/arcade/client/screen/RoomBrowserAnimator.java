package org.shee33.act0.arcade.client.screen;

/**
 * 「房间浏览器」界面的动画状态聚合 —— 《游戏浏览器动效双版本规格文档》第 1 节（共享底盘）
 * 与第 2 节（浏览器 A 专属）里全部动效的 {@link MenuTween.Anim} 持有器。时长/错峰/曲线全部
 * 原样照抄规格第 4 节的浏览器 A 源码，不自行发明数值。
 *
 * <p>本类只负责"有哪些补间、各自多长、延迟多少"，不含任何绘制或判定逻辑；纯逻辑（状态派生、
 * 筛选、抽屉设定键值对、摇头位移、快照差分）在 {@link RoomBrowserModel}；绘制与输入在
 * {@link RoomBrowserScreen}。
 *
 * <h2>开场级联（规格 §1.3）</h2>
 * <ul>
 *   <li>{@link #chrome}：顶部/筛选/表头等 {@code .ch} 块，{@value #CH_MS}ms outCubic，
 *       错峰 {@value #CH_STAGGER_MS}ms/块</li>
 *   <li>{@link #tabLine}：金色下划线宽度 0→标签宽，{@value #TAB_LINE_MS}ms outExpo，
 *       延迟 {@value #TAB_LINE_DELAY}ms</li>
 *   <li>{@link #rows}：列表行 opacity + translateY 14→0，{@value #ROW_MS}ms outCubic，
 *       错峰 {@value #ROW_STAGGER_MS}ms/行</li>
 *   <li>{@link #empty}：空状态淡入 {@value #EMPTY_MS}ms outCubic，延迟 {@value #EMPTY_DELAY}ms</li>
 * </ul>
 *
 * <h2>唯一滑动指示器</h2>
 * 本界面存在两个货真价实的"唯一持久选中"实体，因此<b>与旧版实现不同</b>，这一版确实提供了
 * 滑动指示器：{@link #tabSlide}（筛选标签下的金色下划线，FLIP 滑动 left+width）与
 * {@link #rowAccentIn}/{@link #rowAccentOut}（被选中行左侧 2px 金条 scaleY）。旧版注释里
 * "房间列表没有持久选中概念"的判断随右侧详情抽屉的引入而失效——抽屉正是靠"当前选中房间"
 * 这个跨帧状态驱动的。
 */
final class RoomBrowserAnimator {

    // ============================================================
    // 开场级联（§1.3）
    // ============================================================

    /** {@code .ch} 块淡入时长。 */
    static final long CH_MS = 260L;
    /** {@code .ch} 块错峰间隔。 */
    static final long CH_STAGGER_MS = 70L;
    /** {@code .ch} 块数量：标题 / 右上(计数+刷新) / 筛选行 / 表头 / 分隔线 / 底部按钮行。 */
    static final int CH_COUNT = 6;

    static final int CH_TITLE = 0;
    static final int CH_TOP_RIGHT = 1;
    static final int CH_TABS = 2;
    static final int CH_HEAD = 3;
    static final int CH_DIVIDER = 4;
    static final int CH_FOOTER = 5;

    /** 下划线展开时长/延迟。 */
    static final long TAB_LINE_MS = 320L;
    static final long TAB_LINE_DELAY = 280L;

    /** 列表行入场时长与错峰（规格明确 50ms/行）。 */
    static final long ROW_MS = 260L;
    static final long ROW_STAGGER_MS = 50L;
    /** 行入场的初始下移量（px，规格 translateY 14→0；MC 面板尺寸更小，等比缩到 6px）。 */
    static final int ROW_RISE_PX = 6;

    /** 空状态淡入。 */
    static final long EMPTY_MS = 300L;
    static final long EMPTY_DELAY = 150L;

    // ============================================================
    // 标签切换（§1.3）
    // ============================================================

    /** 下划线 FLIP 滑动。 */
    static final long TAB_SLIDE_MS = 220L;
    /** 旧列表逐行下沉淡出。 */
    static final long ROW_EXIT_MS = 140L;
    static final long ROW_EXIT_STAGGER_MS = 18L;
    /** 旧行退场的下沉量（px，规格 translateY 0→8，等比缩到 4px）。 */
    static final int ROW_SINK_PX = 4;

    // ============================================================
    // 刷新（§1.3）
    // ============================================================

    /** 刷新图标自转 360°。 */
    static final long REFRESH_SPIN_MS = 500L;
    /** 刷新时行淡出（比标签切换更快、错峰更密）。 */
    static final long ROW_FADE_MS = 130L;
    static final long ROW_FADE_STAGGER_MS = 15L;
    /** 蓝色扫描线从列表顶扫到底。 */
    static final long SCAN_MS = 450L;

    // ============================================================
    // 行交互（§1.3）
    // ============================================================

    /** 行悬停缩进（双向对称补间）。 */
    static final long ROW_HOVER_MS = 140L;
    /** 选中行左侧金条 scaleY 展开 / 收回。 */
    static final long ROW_ACCENT_IN_MS = 200L;
    static final long ROW_ACCENT_OUT_MS = 140L;
    /** 通用滚轮换字。 */
    static final long ROLL_MS = 190L;
    /** 实时变动行的蓝色高亮衰减。 */
    static final long ROW_FLASH_MS = 600L;

    // ============================================================
    // 右侧详情抽屉（§2.2）
    // ============================================================

    static final long DRAWER_OPEN_MS = 300L;
    static final long DRAWER_CLOSE_MS = 240L;
    /** 换选时内容压暗到 0.3 / 回到 1。 */
    static final long DRAWER_DIM_MS = 120L;
    static final long DRAWER_LIT_MS = 160L;
    /** 玩家列表逐行 translateX 10→0 + 淡入。 */
    static final long PLAYER_MS = 200L;
    static final long PLAYER_STAGGER_MS = 40L;
    /** 抽屉同屏最多展示的玩家行数（超出以"… 还有 N 人"折叠）。 */
    static final int PLAYER_SLOTS = 7;
    /** 玩家行入场的初始右移量（px，规格 translateX 10→0，等比缩到 5px）。 */
    static final int PLAYER_SHIFT_PX = 5;

    // ============================================================
    // 加入按钮与加入转场（§2.4）
    // ============================================================

    static final long JOIN_PRESS_MS = 220L;
    static final long JOIN_SWEEP_MS = 350L;
    /** 满员摇头拒绝（linear，位移公式见 {@link RoomBrowserModel#shakeOffset(float)}）。 */
    static final long SHAKE_MS = 320L;
    /** 按压→压暗遮罩之间的等待。 */
    static final long JOIN_HANDOFF_MS = 200L;
    static final long OVERLAY_IN_MS = 250L;
    static final long JOIN_BAR_MS = 950L;
    static final long JOIN_BAR_DELAY = 250L;
    /** 「已连接」停留时长。 */
    static final long JOIN_HOLD_MS = 700L;
    static final long OVERLAY_OUT_MS = 300L;

    // ============================================================
    // 底部按钮按压回弹
    // ============================================================

    /** {@link #press} 数组固定分配给刷新 / 创建房间 / 关闭三个控件。 */
    static final int STATIC_PRESS_COUNT = 3;
    static final int P_REFRESH = 0;
    static final int P_CREATE = 1;
    static final int P_CLOSE = 2;
    static final long PRESS_MS = 220L;

    // ============================================================
    // 字段
    // ============================================================

    final MenuTween.Anim[] chrome;
    final MenuTween.Anim tabLine;
    final MenuTween.Anim tabSlide;
    final MenuTween.Anim[] rows;
    final MenuTween.Anim[] rowExit;
    final MenuTween.Anim[] rowFade;
    final MenuTween.Anim[] rowHover;
    final MenuTween.Anim[] countRoll;
    final MenuTween.Anim[] statusRoll;
    final MenuTween.Anim[] rowFlash;
    final MenuTween.Anim empty;
    final MenuTween.Anim refreshSpin;
    final MenuTween.Anim scan;
    final MenuTween.Anim rowAccentIn;
    final MenuTween.Anim rowAccentOut;
    final MenuTween.Anim drawerOpen;
    final MenuTween.Anim drawerClose;
    final MenuTween.Anim drawerDim;
    final MenuTween.Anim drawerLit;
    final MenuTween.Anim[] players;
    final MenuTween.Anim joinPress;
    final MenuTween.Anim joinSweep;
    final MenuTween.Anim shake;
    final MenuTween.Anim overlayIn;
    final MenuTween.Anim joinBar;
    final MenuTween.Anim overlayOut;
    final MenuTween.Anim[] press;

    /**
     * @param nowMs       开屏时刻（{@link MenuTween#now()} 或测试传入的固定时间戳）
     * @param visibleRows 同屏可见列表行数，决定所有"按可见槽位"分配的数组长度
     */
    RoomBrowserAnimator(long nowMs, int visibleRows) {
        int rowCount = Math.max(0, visibleRows);

        chrome = new MenuTween.Anim[CH_COUNT];
        for (int i = 0; i < CH_COUNT; i++) {
            chrome[i] = new MenuTween.Anim(CH_MS, (long) i * CH_STAGGER_MS);
        }
        tabLine = new MenuTween.Anim(TAB_LINE_MS, TAB_LINE_DELAY);
        tabSlide = new MenuTween.Anim(TAB_SLIDE_MS, 0L);

        rows = newRowAnims(rowCount, ROW_MS, ROW_STAGGER_MS);
        rowExit = newRowAnims(rowCount, ROW_EXIT_MS, ROW_EXIT_STAGGER_MS);
        rowFade = newRowAnims(rowCount, ROW_FADE_MS, ROW_FADE_STAGGER_MS);
        rowHover = newRowAnims(rowCount, ROW_HOVER_MS, 0L);
        countRoll = newRowAnims(rowCount, ROLL_MS, 0L);
        statusRoll = newRowAnims(rowCount, ROLL_MS, 0L);
        rowFlash = newRowAnims(rowCount, ROW_FLASH_MS, 0L);

        empty = new MenuTween.Anim(EMPTY_MS, EMPTY_DELAY);
        refreshSpin = new MenuTween.Anim(REFRESH_SPIN_MS, 0L);
        scan = new MenuTween.Anim(SCAN_MS, 0L);

        rowAccentIn = new MenuTween.Anim(ROW_ACCENT_IN_MS, 0L);
        rowAccentOut = new MenuTween.Anim(ROW_ACCENT_OUT_MS, 0L);

        drawerOpen = new MenuTween.Anim(DRAWER_OPEN_MS, 0L);
        drawerClose = new MenuTween.Anim(DRAWER_CLOSE_MS, 0L);
        drawerDim = new MenuTween.Anim(DRAWER_DIM_MS, 0L);
        drawerLit = new MenuTween.Anim(DRAWER_LIT_MS, 0L);
        players = newRowAnims(PLAYER_SLOTS, PLAYER_MS, PLAYER_STAGGER_MS);

        joinPress = new MenuTween.Anim(JOIN_PRESS_MS, 0L);
        joinSweep = new MenuTween.Anim(JOIN_SWEEP_MS, 0L);
        shake = new MenuTween.Anim(SHAKE_MS, 0L);
        overlayIn = new MenuTween.Anim(OVERLAY_IN_MS, 0L);
        joinBar = new MenuTween.Anim(JOIN_BAR_MS, JOIN_BAR_DELAY);
        overlayOut = new MenuTween.Anim(OVERLAY_OUT_MS, 0L);

        press = new MenuTween.Anim[STATIC_PRESS_COUNT];
        for (int i = 0; i < press.length; i++) {
            press[i] = new MenuTween.Anim(PRESS_MS, 0L);
        }

        playOpening(nowMs);
    }

    private static MenuTween.Anim[] newRowAnims(int count, long durationMs, long staggerMs) {
        MenuTween.Anim[] out = new MenuTween.Anim[count];
        for (int i = 0; i < count; i++) {
            out[i] = new MenuTween.Anim(durationMs, (long) i * staggerMs);
        }
        return out;
    }

    /** 开场级联：{@code .ch} 块 + 下划线展开 + 列表行错峰（规格 {@code openAnim()}）。 */
    void playOpening(long nowMs) {
        for (MenuTween.Anim a : chrome) {
            a.start(nowMs);
        }
        tabLine.start(nowMs);
        empty.start(nowMs);
        startRows(nowMs);
    }

    /** 列表行重建后的入场级联（规格 {@code staggerIn()}）。 */
    void startRows(long nowMs) {
        for (MenuTween.Anim a : rows) {
            a.start(nowMs);
        }
    }

    /** 标签切换时旧列表的逐行下沉淡出。 */
    void startRowExit(long nowMs) {
        for (MenuTween.Anim a : rowExit) {
            a.start(nowMs);
        }
    }

    /** 刷新时旧列表的逐行淡出（比标签切换更快）。 */
    void startRowFade(long nowMs) {
        for (MenuTween.Anim a : rowFade) {
            a.start(nowMs);
        }
    }

    /** 抽屉玩家列表的逐行 40ms 错峰入场。 */
    void startPlayers(long nowMs) {
        for (MenuTween.Anim a : players) {
            a.start(nowMs);
        }
    }

    /** 标签切换的旧行退场总时长（末行延迟 + 单行时长），用于排下一阶段的时机。 */
    long rowExitTotalMs() {
        return ROW_EXIT_MS + Math.max(0, rows.length - 1) * ROW_EXIT_STAGGER_MS;
    }

    /** 刷新的旧行淡出总时长。 */
    long rowFadeTotalMs() {
        return ROW_FADE_MS + Math.max(0, rows.length - 1) * ROW_FADE_STAGGER_MS;
    }
}
