package org.shee33.act0.arcade.client.screen;

/**
 * 「游戏浏览器」界面的动画状态聚合 —— 对照 {@link CreateRoomAnimator} 的骨架模式，为
 * {@link RoomBrowserScreen} 提供开场级联（标题栏 → 列表行 → 底部按钮）与按压回弹的
 * {@link MenuTween.Anim} 状态持有器。
 *
 * <p><b>刻意不提供滑动指示器字段</b>：本界面没有 {@code CreateRoomScreen} 模式列表那种
 * 「唯一持久选中项」概念——房间列表是纯滚动、逐行独立可操作的列表，每行的加入/离开
 * 按钮代表该行自己的即时动作，不存在跨帧持续的「当前选中行」。因此没有一个合理的
 * 单一实体可以在行之间"滑动"表示选中状态，本类不做 {@code indicatorY} 之类的字段——
 * 具体决策依据详见 {@link RoomBrowserScreen} 类头注释。
 *
 * <p>开场级联时序（供 {@code render()} 每帧采样）：
 * <ul>
 *   <li>{@link #titleBar}：标题栏文字，220ms outCubic，0ms 延迟（最先出现）</li>
 *   <li>{@link #rows}：可见列表行，每行 240ms outCubic，延迟
 *       {@value #ROW_BASE_DELAY} + i×{@value #ROW_STAGGER_MS}ms（逐行错峰 35ms/行——
 *       落在 AGENTS.md 规定的 30-40ms 建议区间内）</li>
 *   <li>{@link #footer}：底部"刷新 / 创建房间 / 关闭"按钮行，220ms outCubic，延迟
 *       {@value #ROW_BASE_DELAY} + visibleRows×{@value #ROW_STAGGER_MS} +
 *       {@value #FOOTER_EXTRA_DELAY}ms（等最后一行开始动画后再等一拍出现，
 *       确保"标题→列表→底部"的到达顺序不被打乱）</li>
 * </ul>
 *
 * <p>按压回弹：{@link #press} 数组前 {@value #STATIC_PRESS_COUNT} 位固定对应
 * 刷新/创建房间/关闭三个底部按钮（索引常量定义在 {@link RoomBrowserScreen} 的
 * {@code P_REFRESH}/{@code P_CREATE}/{@code P_CLOSE}），其余 {@code visibleRows} 位
 * 对应当前可见的各行加入/离开动作按钮——按"可见槽位"（0..visibleRows-1，即
 * {@code index - scrollRow}）索引，而非房间在全量列表中的下标，因为列表会滚动，
 * 槽位比全局下标更适合复用同一组回弹动画实例，与
 * {@code CreateRoomAnimator.press[11]} 按"控件角色"而非"数据下标"分配的思路一致。
 */
final class RoomBrowserAnimator {

    /** 列表行开始出现前的基础延迟（毫秒），标题栏动效播放期间给列表让出一点时间差。 */
    static final long ROW_BASE_DELAY = 90L;
    /** 逐行错峰间隔（毫秒）：行 i 的延迟 = {@link #ROW_BASE_DELAY} + i × 本值。 */
    static final long ROW_STAGGER_MS = 35L;
    /** 底部按钮行相对"最后一行开始动画"再额外等待的时间（毫秒）。 */
    static final long FOOTER_EXTRA_DELAY = 40L;
    /** {@link #press} 数组前段固定分配给刷新/创建房间/关闭三个底部按钮。 */
    static final int STATIC_PRESS_COUNT = 3;

    final MenuTween.Anim titleBar;
    final MenuTween.Anim[] rows;
    final MenuTween.Anim footer;
    final MenuTween.Anim[] press;

    /**
     * @param nowMs       开屏时刻（{@link MenuTween#now()} 或测试传入的固定时间戳）
     * @param visibleRows 同屏可见的列表行数（{@code RoomBrowserScreen.VISIBLE_ROWS}），
     *                    决定 {@link #rows} 长度、{@link #footer} 延迟计算基数，以及
     *                    {@link #press} 数组在固定 3 位之外追加的行按压槽位数
     */
    RoomBrowserAnimator(long nowMs, int visibleRows) {
        titleBar = new MenuTween.Anim(220L, 0L);

        int rowCount = Math.max(0, visibleRows);
        rows = new MenuTween.Anim[rowCount];
        for (int i = 0; i < rowCount; i++) {
            rows[i] = new MenuTween.Anim(240L, ROW_BASE_DELAY + (long) i * ROW_STAGGER_MS);
        }

        long footerDelay = ROW_BASE_DELAY + (long) rowCount * ROW_STAGGER_MS + FOOTER_EXTRA_DELAY;
        footer = new MenuTween.Anim(220L, footerDelay);

        press = new MenuTween.Anim[STATIC_PRESS_COUNT + rowCount];
        for (int i = 0; i < press.length; i++) {
            press[i] = new MenuTween.Anim(220L, 0L);
        }

        // 启动开场级联；press[] 按用户交互在具体点击时再单独 start()（见 RoomBrowserScreen）。
        titleBar.start(nowMs);
        for (MenuTween.Anim a : rows) {
            a.start(nowMs);
        }
        footer.start(nowMs);
    }
}
