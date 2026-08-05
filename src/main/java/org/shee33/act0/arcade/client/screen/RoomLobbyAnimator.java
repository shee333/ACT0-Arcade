package org.shee33.act0.arcade.client.screen;

/**
 * 「房间大厅」界面的动画状态聚合：12 槽位开场对角级联 + 队伍切换滑动指示器 +
 * 人数滚轮 + 全部可点击控件共享的按压回弹反馈。与 {@link CreateRoomAnimator} 同款骨架
 * （构造时铺好 {@link MenuTween.Anim} 数组，{@link RoomLobbyScreen} 逐帧采样），
 * 但规模小得多——大厅只有一屏静态布局，无需 §3/§4/§5 完整分区级联。
 *
 * <p><b>槽位级联顺序（本类的设计选择，文档化于此）</b>：对角线错峰
 * （{@code delay = (row + col) * 40ms}），而非逐行整体跳出——2 列布局下第 0 槽 (0,0)
 * 最先出现，第 1/2 槽 (0,1)/(1,0) 同处一条反对角线、同时于 40ms 出现，如此类推，形成
 * 左上到右下的斜向波浪。比"整行一起跳出"更有陆续到达的层次感，且实现成本与逐行方案
 * 相同（仅 {@link #cascadeDelayMs} 的公式不同）。
 */
final class RoomLobbyAnimator {

    /** 玩家槽位网格容量上限（2 列 × 6 行）。 */
    static final int SLOT_COUNT = 12;
    /** 玩家槽位网格列数。 */
    static final int SLOT_COLS = 2;

    /** 单槽入场时长：200ms outCubic（比 CreateRoomScreen 左栏 260ms 略快，大厅信息更轻量）。 */
    private static final long SLOT_DURATION_MS = 200L;
    /** 对角线错峰间隔：每级 (row+col) 增加 40ms 延迟。 */
    private static final long SLOT_STAGGER_MS = 40L;

    // 按压回弹数组索引（对应 press[]，共 7 个可按压控件）
    static final int P_START = 0;
    static final int P_MINUS = 1;
    static final int P_PLUS = 2;
    static final int P_LEAVE = 3;
    static final int P_BROWSER = 4;
    static final int P_TEAM_BLUE = 5;
    static final int P_TEAM_RED = 6;
    static final int PRESS_COUNT = 7;

    /** 12 槽位的开场对角级联，每槽 200ms outCubic + 对角线错峰延迟。 */
    final MenuTween.Anim[] slotCascade;
    /** 队伍切换滑动指示器：220ms outCubic，与 {@code CreateRoomAnimator.indicatorY} 同参数。 */
    final MenuTween.Anim teamIndicator;
    /** 房间人数数值滚轮：190ms outCubic，与 {@code CreateRoomAnimator.scoreTimePlayersRoll} 同参数。 */
    final MenuTween.Anim capacityRoll;
    /** 所有可点击控件共享的按压回弹反馈：220ms outBack（经 {@link MenuChrome#pressScale} 消费）。 */
    final MenuTween.Anim[] press;

    RoomLobbyAnimator(long nowMs) {
        slotCascade = new MenuTween.Anim[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotCascade[i] = new MenuTween.Anim(SLOT_DURATION_MS, cascadeDelayMs(i, SLOT_COLS));
        }
        teamIndicator = new MenuTween.Anim(220L, 0L);
        capacityRoll = new MenuTween.Anim(190L, 0L);

        press = new MenuTween.Anim[PRESS_COUNT];
        for (int i = 0; i < press.length; i++) {
            press[i] = new MenuTween.Anim(220L, 0L);
        }

        // 开场即铺满 12 槽的对角级联；其余动效按用户交互在具体操作时再单独 start()。
        for (MenuTween.Anim a : slotCascade) {
            a.start(nowMs);
        }
    }

    /**
     * 对角线错峰延迟：{@code index} 对应槽位 {@code (row = index/cols, col = index%cols)}，
     * 延迟 {@code (row+col) * 40ms}——同一条反对角线上的槽位延迟相同、同时出现。
     */
    static long cascadeDelayMs(int index, int cols) {
        int row = index / cols;
        int col = index % cols;
        return (row + col) * SLOT_STAGGER_MS;
    }
}
