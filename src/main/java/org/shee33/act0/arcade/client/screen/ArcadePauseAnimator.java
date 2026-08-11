package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.shee33.act0.arcade.client.ClientSidebar;

import javax.annotation.Nullable;

/**
 * 街机暂停菜单的全部动画状态与绘制（《战地暂停菜单动效规格文档》的街机裁剪版）。
 *
 * <p>裁剪的边界：街机模式<b>没有票数、也没有小队系统</b>，所以规格文档 §2 的右侧状态区与
 * §3.4 的小队管理子页面在这里不存在——不为了"看起来完整"去造一个没有数据源的假面板。
 * 其余一切（遮罩左重右轻、左上红点呼吸 +「对局仍在进行」+ 模式名、金色滑动指示器、
 * 800ms 长按确认、Toast、开关时序）与战地逐帧一致，时序取自 {@link PauseMenuAnim}。
 *
 * <p>视觉语言遵循 AGENTS.md 指定的街机统一标准：金色强调色与绘制原语走
 * {@link MenuChrome}，时间源走 {@link MenuTween#now()}（{@code System.currentTimeMillis()}）。
 * 曲线取 {@link PauseMenuAnim} 而非 {@link MenuTween.Ease} 的原因是本规格的时长/延迟是一整套
 * 与战地对齐的常量，拆成两处会必然漂移；两边公式逐点等价，已由 {@code PauseMenuAnimTest} 断言。
 *
 * <p>动画状态是<b>实例字段</b>（随 Screen 创建/销毁），"每次打开重播、无需手动复位"因此是天然
 * 结果，与 {@link CreateRoomAnimator} 同一手法。
 */
final class ArcadePauseAnimator {

    // ---- 布局（规格文档 §2；demo 舞台 900×540 与 GUI scale 2 下的 960×540 近乎 1:1） ----
    private static final int MARGIN_LEFT = 36;
    private static final int TAG_TOP = 26;
    private static final int MENU_TOP = 130;
    private static final int MENU_W = 230;
    /** 项高 24 + 间距 8 = 步距 32，落在 AGENTS.md 的 8px 网格上。 */
    private static final int ITEM_H = 24;
    private static final int ITEM_STRIDE = 32;
    private static final int IND_OUTSET = 14;
    private static final int IND_W = 3;
    private static final int IND_H = 22;

    private static final int TOAST_BOTTOM = 26;
    private static final int TOAST_PAD_X = 18;
    private static final int TOAST_H = 20;

    /** 遮罩纵向切条数：条越多渐变越平滑，48 条在 1080p 下每条 ~20px，肉眼已看不出台阶。 */
    private static final int OVERLAY_STRIPS = 48;
    private static final int OVERLAY_RGB = 0x080A0E;
    private static final int TOAST_BG = 0xF20D1116;

    /** 红点呼吸周期基数：{@code 0.45+0.55·|sin(t/600)|}（§2）。 */
    private static final float DOT_PERIOD_MS = 600f;

    /** 菜单项：街机无小队，故只有四项（顺序与文案即验收基准）。 */
    enum Item {
        RESUME("回到游戏", false, 0, ""),
        SETTINGS("设置", false, 0, ""),
        LEAVE_MATCH("退出战局", true, MenuChrome.GOLD, "已退出战局"),
        QUIT_GAME("退出游戏", true, FlatTheme.BRAVO, "正在退出游戏");

        final String label;
        /** 是否为长按确认项（危险操作不用弹窗，用 800ms 填充）。 */
        final boolean hold;
        final int holdColor;
        final String doneToast;

        Item(String label, boolean hold, int holdColor, String doneToast) {
            this.label = label;
            this.hold = hold;
            this.holdColor = holdColor;
            this.doneToast = doneToast;
        }
    }

    private static final Item[] ITEMS = Item.values();

    private final long openedAtMs;
    private long closingAtMs = -1L;

    private int focus;
    private long indicatorStartMs;
    private float indicatorFrom;
    private float indicatorTo;

    private int holdIndex = -1;
    private long holdStartMs;
    private int rewindIndex = -1;
    private float rewindFrom;
    private long rewindStartMs;

    private String toastText = "";
    private long toastStartMs = -1L;

    @Nullable
    private Item pendingItem;

    ArcadePauseAnimator(long nowMs) {
        this.openedAtMs = nowMs;
        this.indicatorStartMs = nowMs;
        this.indicatorFrom = indicatorTargetY(0);
        this.indicatorTo = this.indicatorFrom;
    }

    // ============================================================
    // 对外状态
    // ============================================================

    boolean isClosing() {
        return closingAtMs >= 0L;
    }

    /** 开始关闭序列（反向播放）；真正 {@code setScreen(null)} 由 Screen 在 {@link #closeDoneAt} 后执行。 */
    void beginClose(long now) {
        if (closingAtMs >= 0L) {
            return;
        }
        cancelHold(now);
        closingAtMs = now;
    }

    /** 关闭序列结束时刻（毫秒绝对时间）；未在关闭中返回 {@link Long#MAX_VALUE}。 */
    long closeDoneAt() {
        return closingAtMs < 0L ? Long.MAX_VALUE : closingAtMs + PauseMenuAnim.closeTotalMs(ITEMS.length);
    }

    /** 取走一次已达成的菜单动作（长按填满 / 普通项点击 / 键盘确认）。 */
    @Nullable
    Item pollPendingItem() {
        Item item = pendingItem;
        pendingItem = null;
        return item;
    }

    void toast(String text, long now) {
        toastText = text;
        toastStartMs = now;
    }

    // ============================================================
    // 焦点与输入
    // ============================================================

    int focusIndex() {
        return focus;
    }

    Item focusedItem() {
        return ITEMS[focus];
    }

    Item itemOf(int index) {
        return ITEMS[Math.floorMod(index, ITEMS.length)];
    }

    /** 移动焦点（键盘上下 / 鼠标悬停共用；指示器是唯一焦点指示物，因此这里只动指示器）。 */
    void setFocus(int index, long now) {
        int clamped = MenuChrome.wrapIndex(index, 0, ITEMS.length);
        if (clamped == focus) {
            return;
        }
        // 从"当前实际画到哪"起补间而非从上一个目标起：连点两次上键时指示器不会跳回去重滑。
        indicatorFrom = indicatorY(now);
        focus = clamped;
        indicatorTo = indicatorTargetY(clamped);
        indicatorStartMs = now;
        cancelHold(now);
    }

    void onMouseMoved(double mx, double my, long now) {
        int hovered = itemAt(mx, my);
        if (hovered >= 0) {
            setFocus(hovered, now);
        } else if (holdIndex >= 0) {
            // 规格文档 §3.2：移出长按项等于松手取消。
            cancelHold(now);
        }
    }

    /** @return 命中的菜单项下标，未命中返回 -1 */
    int itemAt(double mx, double my) {
        for (int i = 0; i < ITEMS.length; i++) {
            if (MenuChrome.inRect(mx, my, MARGIN_LEFT, MENU_TOP + i * ITEM_STRIDE, MENU_W, ITEM_H)) {
                return i;
            }
        }
        return -1;
    }

    /** 开始长按确认（鼠标按下或 Enter 按下）。 */
    void beginHold(int index, long now) {
        if (index < 0 || index >= ITEMS.length || !ITEMS[index].hold) {
            return;
        }
        holdIndex = index;
        holdStartMs = now;
        rewindIndex = -1;
    }

    /** 松手/移出：按<b>当前宽度</b>启动 180ms 回退（不是从满条回退）。 */
    void cancelHold(long now) {
        if (holdIndex < 0) {
            return;
        }
        rewindFrom = PauseMenuAnim.holdFill(now - holdStartMs, -1f, 0L);
        rewindIndex = holdIndex;
        rewindStartMs = now;
        holdIndex = -1;
    }

    boolean isHolding() {
        return holdIndex >= 0;
    }

    int holdingIndex() {
        return holdIndex;
    }

    /** 触发一个普通项（非长按）。 */
    void activate(Item item) {
        pendingItem = item;
    }

    // ============================================================
    // 渲染
    // ============================================================

    void render(GuiGraphics gg, Font font, int screenW, int screenH, long now) {
        Frame f = frame(now);
        drawOverlay(gg, screenW, screenH, f.overlay);
        checkHoldCompletion(now);
        drawTag(gg, font, f.tag, now);
        drawMenu(gg, font, f, now);
        drawIndicator(gg, f, now);
        drawToast(gg, font, screenW, screenH, now);
    }

    /** 长按填满即执行（规格文档 §3.2：填满就是确认，没有弹窗）。 */
    private void checkHoldCompletion(long now) {
        if (holdIndex < 0 || !PauseMenuAnim.holdCompleted(now - holdStartMs)) {
            return;
        }
        Item item = ITEMS[holdIndex];
        holdIndex = -1;
        rewindIndex = -1;
        pendingItem = item;
        toast(item.doneToast, now);
    }

    /** 每帧的各区域不透明度/位移：开场用级联延迟，关闭用反向错峰。 */
    private Frame frame(long now) {
        float[] itemA = new float[ITEMS.length];
        float[] itemX = new float[ITEMS.length];
        if (closingAtMs >= 0L) {
            long e = now - closingAtMs;
            float fade = 1f - PauseMenuAnim.inCubic(PauseMenuAnim.progress(e, 0, PauseMenuAnim.CLOSE_FADE_MS));
            float ov = 1f - PauseMenuAnim.inCubic(PauseMenuAnim.progress(e,
                    PauseMenuAnim.CLOSE_OVERLAY_DELAY_MS, PauseMenuAnim.CLOSE_OVERLAY_MS));
            for (int i = 0; i < ITEMS.length; i++) {
                float v = PauseMenuAnim.itemCloseProgress(e, i);
                itemA[i] = 1f - v;
                itemX[i] = PauseMenuAnim.CLOSE_ITEM_SLIDE_PX * v;
            }
            return new Frame(ov, fade, itemA, itemX, fade);
        }
        long e = now - openedAtMs;
        float ov = PauseMenuAnim.outCubic(PauseMenuAnim.progress(e, 0, PauseMenuAnim.OVERLAY_IN_MS));
        float tag = PauseMenuAnim.outCubic(PauseMenuAnim.progress(e,
                PauseMenuAnim.TAG_DELAY_MS, PauseMenuAnim.TAG_IN_MS));
        float ind = PauseMenuAnim.outCubic(PauseMenuAnim.progress(e,
                PauseMenuAnim.INDICATOR_DELAY_MS, PauseMenuAnim.INDICATOR_IN_MS));
        for (int i = 0; i < ITEMS.length; i++) {
            float v = PauseMenuAnim.itemOpenProgress(e, i);
            itemA[i] = v;
            itemX[i] = PauseMenuAnim.ITEM_SLIDE_PX * (1f - v);
        }
        return new Frame(ov, tag, itemA, itemX, ind);
    }

    /** 遮罩：横向三档渐变（左重右轻），保证右侧战场保持可见。 */
    private static void drawOverlay(GuiGraphics gg, int screenW, int screenH, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        for (int i = 0; i < OVERLAY_STRIPS; i++) {
            int x1 = screenW * i / OVERLAY_STRIPS;
            int x2 = screenW * (i + 1) / OVERLAY_STRIPS;
            if (x2 <= x1) {
                continue;
            }
            float ratio = (i + 0.5f) / OVERLAY_STRIPS;
            int a = Math.round(255f * PauseMenuAnim.overlayAlphaAt(ratio) * alpha);
            gg.fill(x1, 0, x2, screenH, (a << 24) | OVERLAY_RGB);
        }
    }

    private void drawTag(GuiGraphics gg, Font font, float alpha, long now) {
        if (alpha <= 0.002f) {
            return;
        }
        int y = TAG_TOP + Math.round(PauseMenuAnim.TAG_SLIDE_PX * (1f - alpha));
        // 红点呼吸：这是"游戏没停"这条第一原则唯一的持续动效，不能跟着开场淡入的 alpha 一起冻住。
        float pulse = 0.45f + 0.55f * Math.abs((float) Math.sin(now / DOT_PERIOD_MS));
        gg.fill(MARGIN_LEFT, y + 1, MARGIN_LEFT + 7, y + 8, FlatTheme.withAlpha(FlatTheme.BRAVO, pulse * alpha));
        gg.drawString(font, "对局仍在进行", MARGIN_LEFT + 15, y,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.6f * alpha), false);
        String mode = modeName();
        if (!mode.isEmpty()) {
            MenuChrome.drawScaledAxis(gg, MARGIN_LEFT, y + 16, 1.5f, 1.5f,
                    () -> gg.drawString(font, mode, MARGIN_LEFT, y + 16,
                            FlatTheme.withAlpha(MenuChrome.WHITE, alpha), false));
        }
    }

    private void drawMenu(GuiGraphics gg, Font font, Frame f, long now) {
        for (int i = 0; i < ITEMS.length; i++) {
            float a = f.itemAlpha[i];
            if (a <= 0.002f) {
                continue;
            }
            Item item = ITEMS[i];
            int top = MENU_TOP + i * ITEM_STRIDE;
            int x = MARGIN_LEFT + Math.round(f.itemShiftX[i]);
            float fill = holdFillOf(i, now);
            if (fill > 0.001f) {
                gg.fill(x, top, x + Math.round(MENU_W * fill), top + ITEM_H,
                        FlatTheme.withAlpha(item.holdColor, 0.22f * a));
            }
            // 焦点唯一由金色竖条表达：这里绝不画行底色或边框，只提亮文字。
            int color = i == focus
                    ? FlatTheme.withAlpha(MenuChrome.WHITE, a)
                    : FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.55f * a);
            gg.drawString(font, item.label, x + 12, top + 8, color, false);
        }
    }

    private float holdFillOf(int index, long now) {
        if (index == holdIndex) {
            return PauseMenuAnim.holdFill(now - holdStartMs, -1f, 0L);
        }
        if (index == rewindIndex) {
            float v = PauseMenuAnim.holdFill(0L, rewindFrom, now - rewindStartMs);
            if (v <= 0.001f) {
                rewindIndex = -1;
            }
            return v;
        }
        return 0f;
    }

    private void drawIndicator(GuiGraphics gg, Frame f, long now) {
        if (f.indicator <= 0.002f) {
            return;
        }
        int x = MARGIN_LEFT - IND_OUTSET;
        int top = Math.round(indicatorY(now));
        float v = PauseMenuAnim.outCubic(PauseMenuAnim.progress(now - indicatorStartMs, 0,
                PauseMenuAnim.INDICATOR_SLIDE_MS));
        MenuChrome.drawScaledAxis(gg, x, top + IND_H / 2, 1f, PauseMenuAnim.indicatorStretch(v), () -> {
            gg.fill(x, top, x + IND_W, top + IND_H, FlatTheme.withAlpha(MenuChrome.GOLD, f.indicator));
            // 微光：规格文档的 box-shadow 在 MC 里没有等价物，用向外一圈低透明度同色描边近似。
            gg.fill(x - 1, top, x, top + IND_H, FlatTheme.withAlpha(MenuChrome.GOLD, 0.25f * f.indicator));
            gg.fill(x + IND_W, top, x + IND_W + 1, top + IND_H,
                    FlatTheme.withAlpha(MenuChrome.GOLD, 0.25f * f.indicator));
        });
    }

    private float indicatorY(long now) {
        float v = PauseMenuAnim.outCubic(PauseMenuAnim.progress(now - indicatorStartMs, 0,
                PauseMenuAnim.INDICATOR_SLIDE_MS));
        return MenuChrome.lerp(indicatorFrom, indicatorTo, v);
    }

    private static float indicatorTargetY(int index) {
        return MENU_TOP + index * ITEM_STRIDE + (ITEM_H - IND_H) / 2f;
    }

    private void drawToast(GuiGraphics gg, Font font, int screenW, int screenH, long now) {
        if (toastStartMs < 0L || toastText.isEmpty()) {
            return;
        }
        float a = PauseMenuAnim.toastAlpha(now - toastStartMs);
        if (a <= 0.002f) {
            toastStartMs = -1L;
            return;
        }
        int w = font.width(toastText) + TOAST_PAD_X * 2;
        int x = (screenW - w) / 2;
        int y = screenH - TOAST_BOTTOM - TOAST_H;
        gg.fill(x, y, x + w, y + TOAST_H, FlatTheme.withAlpha(TOAST_BG, a));
        int border = FlatTheme.withAlpha(MenuChrome.GOLD, 0.5f * a);
        gg.fill(x, y, x + w, y + 1, border);
        gg.fill(x, y + TOAST_H - 1, x + w, y + TOAST_H, border);
        gg.fill(x, y, x + 1, y + TOAST_H, border);
        gg.fill(x + w - 1, y, x + w, y + TOAST_H, border);
        gg.drawString(font, toastText, x + TOAST_PAD_X, y + 6, FlatTheme.withAlpha(MenuChrome.GOLD, a), false);
    }

    /**
     * 模式名取自服务端下发的侧边栏标题——街机没有独立的"模式名"同步字段，侧边栏标题就是
     * 玩家在 HUD 上一直看到的那行，用它才不会出现"菜单里叫一个名、HUD 里叫另一个名"。
     * 颜色代码要剥掉：这里的字号/配色由菜单自己的层级决定，不该被服务端文案里的 §c 之类夺走。
     */
    private static String modeName() {
        String title = ClientSidebar.title();
        return title == null ? "" : title.replaceAll("\u00a7.", "").trim();
    }

    /** 每帧的区域可见度快照，避免 render 方法之间来回重算级联进度。 */
    private record Frame(float overlay, float tag, float[] itemAlpha, float[] itemShiftX, float indicator) {
    }
}
