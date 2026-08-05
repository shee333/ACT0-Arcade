package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.shee33.act0.arcade.mode.RandomWeaponMode;

/**
 * 创建房间的二级“对局设定”界面。
 *
 * <p>MenuChrome/MenuTween 化改造（11 屏统一动效工作的一部分）：本屏此前是零动效的重灾区
 * （面板/标题/8 行设置卡片瞬间以全 alpha 出现，◀/▶ 数值调整无过渡、无按压反馈）。现补齐
 * 与 {@link CategorySelectScreen} 同款手法——{@link FlatTheme#fadeIn(long)} 做面板级淡入；
 * 每张设置卡片走 {@link MenuTween.Anim} 驱动的错峰级联淡入上移；◀/▶ 改为
 * {@link MenuChrome#drawLightControl} 灯态控件，数值变化时用 {@link MenuChrome#drawRollY}
 * 做方向性纵向滚轮（滚动方向与调整方向一致）；底部“返回”改为 {@link MenuChrome#drawGhostButton}，
 * 且所有 16 个 ◀/▶ 控件与返回按钮均带 {@link MenuChrome#pressScale} 按压回弹 + 点击音效，
 * 与 {@code CreateRoomScreen} 的按压回弹延迟导航手法一致（返回动画播完才真正
 * {@code onClose()}）。
 *
 * <p><b>与 {@link CreateRoomScreen} 的耦合是单向只读的</b>：本屏只通过 16 个包私有
 * accessor/mutator 读写父屏状态（签名冻结，见 {@code CreateRoomScreen} 中对应注释），
 * 不向父屏暴露任何新的回调方法。
 */
public final class MatchSettingsScreen extends Screen {
    private static final int W = 300;
    private static final int H = 238;
    private static final int ROW_H = 22;
    private static final int ROW_COUNT = 8;
    private static final int CARD_H = 18;

    /** 每张设置卡片开场淡入+上移时长（三次方缓出，与 {@link CategorySelectScreen} 同款）。 */
    private static final long CASCADE_DURATION_MS = 220L;
    /** 相邻卡片错峰间隔。 */
    private static final long CASCADE_STAGGER_MS = 30L;
    /** 开场上移幅度（2-4px 区间）。 */
    private static final int CASCADE_OFFSET_Y = 3;
    /** 按压回弹时长，与 {@code CreateRoomAnimator} 的按压动画同款 220ms outBack。 */
    private static final long PRESS_DURATION_MS = 220L;
    /** 数值滚轮时长，与 {@code CreateRoomAnimator.scoreTimePlayersRoll} 同款 190ms outCubic。 */
    private static final long ROLL_DURATION_MS = 190L;

    private static final String[] LABELS = {
            "玩家血量", "武器规则", "重生等待", "友伤", "补给箱", "热区轮换", "热区半径", "热区得分",
    };

    private final CreateRoomScreen parent;
    private final long openedAtMs = MenuTween.now();

    private int left;
    private int top;
    private int backX, backY, backW, backH;

    /** 每张卡片的开场级联动画，下标即设置行下标。 */
    private final MenuTween.Anim[] rowCascade = new MenuTween.Anim[ROW_COUNT];
    /** 每行 ◀ 控件的按压回弹动画。 */
    private final MenuTween.Anim[] leftPress = new MenuTween.Anim[ROW_COUNT];
    /** 每行 ▶ 控件的按压回弹动画。 */
    private final MenuTween.Anim[] rightPress = new MenuTween.Anim[ROW_COUNT];
    /** 每行数值的方向性纵向滚轮动画。 */
    private final MenuTween.Anim[] rowRoll = new MenuTween.Anim[ROW_COUNT];
    /** 每行滚轮出发时刻的旧文案（滑出用）。 */
    private final String[] rowOldText = new String[ROW_COUNT];
    /** 每行滚轮方向：+1 = 新值自下而来（▶/递增），-1 = 自上而来（◀/递减）。 */
    private final int[] rowDir = new int[ROW_COUNT];

    /** 底部返回按钮的开场级联动画（紧随最后一张卡片之后错峰）。 */
    private MenuTween.Anim backCascade;
    /** 底部返回按钮的按压回弹动画。 */
    private MenuTween.Anim backPress;
    /** 返回按钮是否已点击、等待按压动画播完再真正 {@link #onClose()}。 */
    private boolean pendingBack;

    public MatchSettingsScreen(CreateRoomScreen parent) {
        super(Component.literal("对局设定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        backW = 76;
        backH = 20;
        backX = left + W / 2 - backW / 2;
        backY = top + H - 28;

        for (int i = 0; i < ROW_COUNT; i++) {
            MenuTween.Anim cascade = new MenuTween.Anim(CASCADE_DURATION_MS, i * CASCADE_STAGGER_MS);
            cascade.start(openedAtMs);
            rowCascade[i] = cascade;
            leftPress[i] = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
            rightPress[i] = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
            rowRoll[i] = new MenuTween.Anim(ROLL_DURATION_MS, 0L);
            rowOldText[i] = "";
            rowDir[i] = 1;
        }

        backCascade = new MenuTween.Anim(CASCADE_DURATION_MS, ROW_COUNT * CASCADE_STAGGER_MS);
        backCascade.start(openedAtMs);
        backPress = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
        pendingBack = false;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingBack && backPress.isDone(now)) {
            pendingBack = false;
            onClose();
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        float panelAlpha = fade.alpha();

        FlatTheme.panel(gg, left, top, W, H, panelAlpha);
        FlatTheme.titleBar(gg, left, top, W, 24);
        gg.drawCenteredString(font, "对局设定", left + W / 2, top + 9, FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, panelAlpha));
        gg.drawString(font, "§7仅影响本房间", left + 14, top + 32, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, panelAlpha), false);

        int y = top + 48;
        for (int i = 0; i < ROW_COUNT; i++) {
            renderRow(gg, mouseX, mouseY, now, i, y, LABELS[i], valueTextFor(i), panelAlpha);
            y += ROW_H;
        }

        renderBackButton(gg, mouseX, mouseY, now, panelAlpha);
        super.render(gg, mouseX, mouseY, partialTick);
    }

    private String healthText() {
        double health = parent.settingsHealthOverride();
        return health <= 0.0D ? "默认" : Integer.toString((int) Math.round(health));
    }

    /** 当前显示文案，按行下标分派——供渲染与滚轮起点采样共用（滚轮记旧文案时先调用一次）。 */
    private String valueTextFor(int index) {
        return switch (index) {
            case 0 -> healthText();
            case 1 -> parent.settingsRandomMode().enabled() ? parent.settingsRandomMode().displayName() : "使用配装";
            case 2 -> parent.settingsRespawnDelaySeconds() + " 秒";
            case 3 -> parent.settingsFriendlyFire() ? "开启" : "关闭";
            case 4 -> parent.settingsAmmoCrateChancePercent() + "%";
            case 5 -> parent.settingsHotZoneRotateSeconds() + " 秒";
            case 6 -> String.format("%.0f 格", parent.settingsHotZoneRadius());
            case 7 -> parent.settingsHotZoneScorePerSecond() + " / 秒";
            default -> "";
        };
    }

    /** ◀ 控件左上角 X（相对卡片 {@code x+w}），与 {@link #rightControlX} 之间夹着居中数值。 */
    private static int leftControlX(int x, int w) {
        return x + w - 81;
    }

    private static int rightControlX(int x, int w) {
        return x + w - 19;
    }

    /**
     * 设置卡片：{@link FlatTheme#card} 底 + 标签 + {@link MenuChrome#drawLightControl} ◀/▶
     * + {@link MenuChrome#drawRollY} 方向性数值滚轮，叠加错峰级联淡入上移。
     */
    private void renderRow(GuiGraphics gg, int mouseX, int mouseY, long now, int index, int y, String label,
                            String value, float panelAlpha) {
        int x = left + 14;
        int w = W - 28;

        float cascadeT = rowCascade[index].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        float alpha = cascadeT * panelAlpha;

        int lcx = leftControlX(x, w);
        int rcx = rightControlX(x, w);

        boolean cardHovered = !pendingBack && MenuChrome.inRect(mouseX, mouseY, x, drawY, w, CARD_H);
        FlatTheme.card(gg, x, drawY, w, CARD_H, cardHovered);
        gg.drawString(font, "§7" + label, x + 7, drawY + 5, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, alpha), false);

        boolean leftHovered = !pendingBack && MenuChrome.inRect(mouseX, mouseY, lcx, drawY, CARD_H, CARD_H);
        boolean rightHovered = !pendingBack && MenuChrome.inRect(mouseX, mouseY, rcx, drawY, CARD_H, CARD_H);
        MenuChrome.drawLightControl(gg, font, lcx, drawY, CARD_H, CARD_H, "◀", leftHovered, true,
                MenuChrome.pressScale(leftPress[index], now), alpha);
        MenuChrome.drawLightControl(gg, font, rcx, drawY, CARD_H, CARD_H, "▶", rightHovered, true,
                MenuChrome.pressScale(rightPress[index], now), alpha);

        int cx = (lcx + CARD_H + rcx) / 2;
        int cy = drawY + CARD_H / 2;
        gg.enableScissor(lcx + CARD_H, drawY, rcx, drawY + CARD_H);
        MenuChrome.drawRollY(gg, font, cx, cy, rowOldText[index], value, rowDir[index], rowRoll[index], now,
                FlatTheme.withAlpha(FlatTheme.TEXT, alpha), CARD_H);
        gg.disableScissor();
    }

    /** 底部返回按钮：{@link MenuChrome#drawGhostButton} + 独立级联/按压动画。 */
    private void renderBackButton(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float cascadeT = backCascade.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = backY + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        float alpha = cascadeT * panelAlpha;

        boolean hovered = !pendingBack && MenuChrome.inRect(mouseX, mouseY, backX, drawY, backW, backH);
        float scale = MenuChrome.pressScale(backPress, now);
        int cx = backX + backW / 2;
        int cy = drawY + backH / 2;
        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> MenuChrome.drawGhostButton(gg, font, backX, drawY, backW, backH, "返回", hovered, true, alpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingBack) {
            // 返回按钮的按压反馈还没播完，忽略这一帧的新点击。
            return true;
        }
        if (MenuChrome.inRect(mouseX, mouseY, backX, backY, backW, backH)) {
            pendingBack = true;
            backPress.start(MenuTween.now());
            playClick();
            return true;
        }

        int x = left + 14;
        int w = W - 28;
        int lcx = leftControlX(x, w);
        int rcx = rightControlX(x, w);
        int y = top + 48;
        for (int i = 0; i < ROW_COUNT; i++) {
            if (MenuChrome.inRect(mouseX, mouseY, lcx, y, CARD_H, CARD_H)) {
                onAdjustClicked(i, -1);
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rcx, y, CARD_H, CARD_H)) {
                onAdjustClicked(i, 1);
                return true;
            }
            y += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 记录旧文案 → 调用冻结的 {@code parent.adjustXxx}/{@code toggleXxx} 突变状态 → 启动方向性
     * 滚轮 + 对应侧的按压回弹 + 点击音效。值域全部走 {@code floorMod} 循环（见
     * {@code CreateRoomScreen} 对应 mutator），恒定会变化，无需处理“静默边界”分支。
     */
    private void onAdjustClicked(int index, int dir) {
        String oldText = valueTextFor(index);
        adjust(index, dir);
        rowOldText[index] = oldText;
        rowDir[index] = dir;
        long now = MenuTween.now();
        rowRoll[index].start(now);
        (dir > 0 ? rightPress[index] : leftPress[index]).start(now);
        playClick();
    }

    private void adjust(int index, int dir) {
        switch (index) {
            case 0 -> parent.adjustSettingsHealth(dir);
            case 1 -> parent.adjustSettingsRandomMode(dir);
            case 2 -> parent.adjustSettingsRespawn(dir);
            case 3 -> parent.toggleSettingsFriendlyFire();
            case 4 -> parent.adjustSettingsAmmoCrate(dir);
            case 5 -> parent.adjustSettingsHotZoneRotate(dir);
            case 6 -> parent.adjustSettingsHotZoneRadius(dir);
            case 7 -> parent.adjustSettingsHotZoneScore(dir);
            default -> {
            }
        }
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
