package org.shee33.act0.arcade.client.screen;

import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.loadout.LoadoutSlot;

/**
 * 「配装」界面的动画状态聚合 —— 与 {@link CreateRoomAnimator} 同款骨架，供
 * {@link LoadoutScreen} 每帧 {@code render()} 直接读取 {@link MenuTween.Anim} 采样进度。
 *
 * <p>纯逻辑辅助方法（{@code wrapIndex}/{@code lerp}/{@code clamp01}）故意不在此处重复定义——
 * {@code LoadoutScreen} 直接复用 {@link MenuChrome} 上同名的共享副本，避免三处重复实现漂移。
 *
 * <ul>
 *   <li>{@link #leftColumn}：左栏 5 张方案卡片的入场级联（每项 240ms outCubic，
 *       延迟 {@code i×35ms}）。</li>
 *   <li>{@link #rightColumn}：右栏「职业行 + N 个槽位行 + 服饰/外观链接」的入场级联，
 *       与左栏使用不同的基准延迟与间隔（每项 260ms outCubic，延迟 {@code 50 + i×45ms}），
 *       独立成组，不与左栏同步（镜像 {@code CreateRoomAnimator} 的
 *       {@code leftColumn[]}/{@code rightColumn[]} 分组方式）。</li>
 *   <li>{@link #indicatorY}：左栏方案卡片的唯一滑动选中指示器（220ms outCubic + 中途
 *       {@link MenuTween#pulse(float)} 纵向拉伸），替代原先的静态边框颜色切换。</li>
 *   <li>{@link #classSlide}：职业标签横向切换过渡（220ms outCubic，方向随
 *       ◀/▶ 点击方向决定）。</li>
 *   <li>{@link #press}：所有按压回弹控件共享的按压反馈数组，索引语义见
 *       {@link #PRESS_CLASS_LEFT} 起的一组常量 + {@link #slotPressIndex(LoadoutSlot)}。</li>
 * </ul>
 */
final class LoadoutScreenAnimator {

    /** 左栏：固定 5 张方案卡片（{@link LoadoutSet#MAX_SLOTS}）。 */
    static final int LEFT_CARDS = LoadoutSet.MAX_SLOTS;
    /** 右栏：职业行(1) + 槽位行(N) + 服饰/外观链接(1)。 */
    static final int RIGHT_ROWS = 1 + LoadoutSlot.values().length + 1;

    // ============================================================
    // 按压回弹索引语义
    // ============================================================

    /** 职业◀。 */
    static final int PRESS_CLASS_LEFT = 0;
    /** 职业▶。 */
    static final int PRESS_CLASS_RIGHT = 1;
    private static final int PRESS_SLOT_BASE = 2;
    /** 服饰 / 外观 链接按钮。 */
    static final int PRESS_APPAREL = PRESS_SLOT_BASE + LoadoutSlot.values().length;
    /** 保存修改。 */
    static final int PRESS_SAVE = PRESS_APPAREL + 1;
    /** 下次复活使用。 */
    static final int PRESS_RESPAWN = PRESS_SAVE + 1;
    /** 设为默认。 */
    static final int PRESS_DEFAULT = PRESS_RESPAWN + 1;
    /** 关闭。 */
    static final int PRESS_CLOSE = PRESS_DEFAULT + 1;
    /** 按压回弹数组总长度。 */
    static final int PRESS_COUNT = PRESS_CLOSE + 1;

    // ============================================================
    // 开场级联
    // ============================================================

    /** 左栏 5 张方案卡片的入场，每个 240ms outCubic，延迟 {@code i×35ms}。 */
    final MenuTween.Anim[] leftColumn;
    /** 右栏「职业行 + N 槽位行 + 服饰链接」入场，每个 260ms outCubic，延迟 {@code 50 + i×45ms}。 */
    final MenuTween.Anim[] rightColumn;

    // ============================================================
    // 选中 / 切换动效
    // ============================================================

    /** 方案卡片唯一滑动选中指示器：220ms outCubic + pulse 纵向拉伸。 */
    final MenuTween.Anim indicatorY;
    /** 职业标签横向切换过渡：220ms outCubic。 */
    final MenuTween.Anim classSlide;

    // ============================================================
    // 点击后按压回弹
    // ============================================================

    /** 所有可按压控件共享的回弹反馈，每条 220ms outBack（经 {@link MenuChrome#pressScale}）。 */
    final MenuTween.Anim[] press;

    /**
     * 构造时铺设所有 {@link MenuTween.Anim} 并立即启动开场级联（{@link #leftColumn} /
     * {@link #rightColumn}）；其余动效（{@link #indicatorY} / {@link #classSlide} /
     * {@link #press}）按用户交互在具体操作时再单独 {@code start()}。
     *
     * @param nowMs 开屏时刻（应与 {@code LoadoutScreen.openedAtMs} 同一时间源）
     */
    LoadoutScreenAnimator(long nowMs) {
        leftColumn = new MenuTween.Anim[LEFT_CARDS];
        for (int i = 0; i < leftColumn.length; i++) {
            leftColumn[i] = new MenuTween.Anim(240L, (long) i * 35L);
        }
        rightColumn = new MenuTween.Anim[RIGHT_ROWS];
        for (int i = 0; i < rightColumn.length; i++) {
            rightColumn[i] = new MenuTween.Anim(260L, 50L + (long) i * 45L);
        }

        indicatorY = new MenuTween.Anim(220L, 0L);
        classSlide = new MenuTween.Anim(220L, 0L);

        press = new MenuTween.Anim[PRESS_COUNT];
        for (int i = 0; i < press.length; i++) {
            press[i] = new MenuTween.Anim(220L, 0L);
        }

        for (MenuTween.Anim a : leftColumn) {
            a.start(nowMs);
        }
        for (MenuTween.Anim a : rightColumn) {
            a.start(nowMs);
        }
    }

    /**
     * 槽位「选择」按钮在 {@link #press} 数组中的索引：按 {@link LoadoutSlot#ordinal()} 偏移，
     * 避免每个槽位手写一个具名常量导致新增槽位时忘记同步。
     */
    static int slotPressIndex(LoadoutSlot slot) {
        return PRESS_SLOT_BASE + slot.ordinal();
    }
}
