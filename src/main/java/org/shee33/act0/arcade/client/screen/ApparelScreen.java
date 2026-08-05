package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientApparel;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SaveApparelPacket;

/**
 * 单独的共享服饰编辑界面。
 *
 * <p>MenuChrome/MenuTween 化改造（11 屏统一动效工作的一部分）：面板走
 * {@link FlatTheme#fadeIn(long)} 基线淡入，各槽位行改由 {@link MenuTween.Anim} 驱动的
 * 错峰级联淡入 + 轻微上移；原版 Button 全部移除，改为手动命中检测 + {@link MenuChrome}
 * 绘制原语（幽灵按钮 + 金色主按钮），点击不再零反馈瞬间跳转——"选择"/"关闭" 这类会
 * 导航离开本界面的点击，先播放 {@link MenuChrome#pressScale(MenuTween.Anim, long)} 按压
 * 回弹，动画播完（{@link MenuTween.Anim#isDone(long)}）才真正跳转，与
 * {@code LoadoutQuickSelectScreen}/{@code RoomBrowserScreen} 的 pending-nav 延迟导航手法
 * 一致；"保存服饰"不离开本界面，按压反馈与保存动作可以同帧触发，无需延迟。
 */
public final class ApparelScreen extends Screen {
    private static final int MAX_W = 360;
    private static final int MAX_H = 220;

    private static final ApparelSlot[] SLOTS = ApparelSlot.values();

    /** 单行开场淡入+上移时长（三次方缓出，与 FlatTheme.fadeIn 默认时长一致）。 */
    private static final long CASCADE_DURATION_MS = 220L;
    /** 相邻行错峰间隔。 */
    private static final long CASCADE_STAGGER_MS = 35L;
    /** 开场上移幅度（2-4px 区间取上限）。 */
    private static final int CASCADE_OFFSET_Y = 4;
    /** 按压回弹时长，与 CreateRoomAnimator 的按压动画同款 220ms outBack。 */
    private static final long PRESS_DURATION_MS = 220L;

    private static final int SELECT_BTN_W = 44;
    private static final int SELECT_BTN_H = 16;
    private static final int SAVE_BTN_W = 90;
    private static final int SAVE_BTN_H = 20;
    private static final int CLOSE_BTN_W = 54;
    private static final int CLOSE_BTN_H = 20;

    private final Screen parent;
    private final ApparelSelection working;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int rowX;
    private int rowW;
    private int rowY;
    private int rowH;
    private int footY;

    private final long openedAtMs = System.currentTimeMillis();

    /** 每行的开场级联动画，下标即 {@link #SLOTS} 下标。 */
    private final MenuTween.Anim[] cascadeAnim = new MenuTween.Anim[SLOTS.length];
    /** 每行"选择"按钮的点击按压回弹动画，下标即 {@link #SLOTS} 下标。 */
    private final MenuTween.Anim[] selectPressAnim = new MenuTween.Anim[SLOTS.length];
    /** "保存服饰"按钮的按压回弹动画：不涉及导航，动作立即执行，仅用于视觉反馈。 */
    private final MenuTween.Anim savePressAnim = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
    /** "关闭"按钮的按压回弹动画。 */
    private final MenuTween.Anim closePressAnim = new MenuTween.Anim(PRESS_DURATION_MS, 0L);

    /** 已点击、等待按压动画播完再真正打开选择界面的槽位下标；-1 = 无待处理选择。 */
    private int pendingSelectIndex = -1;
    /** 已点击"关闭"、等待按压动画播完再真正 {@link #onClose()}。 */
    private boolean pendingClose;

    public ApparelScreen(Screen parent) {
        super(Component.literal("服饰"));
        this.parent = parent;
        this.working = ClientApparel.selection().copy();
        for (int i = 0; i < SLOTS.length; i++) {
            cascadeAnim[i] = new MenuTween.Anim(CASCADE_DURATION_MS, i * CASCADE_STAGGER_MS);
            cascadeAnim[i].start(openedAtMs);
            selectPressAnim[i] = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
        }
    }

    @Override
    protected void init() {
        computeLayout();
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        rowX = left + 16;
        rowW = panelW - 32;
        rowY = top + 48;
        footY = top + panelH - 28;
    }

    private void computeLayout() {
        panelW = Math.min(MAX_W, Math.max(260, width - 24));
        panelH = Math.min(MAX_H, Math.max(190, height - 24));
        rowH = panelH < 210 ? 28 : 32;
    }

    private int rowYAt(int index) {
        return rowY + index * rowH;
    }

    private void openSelect(ApparelSlot slot) {
        Minecraft.getInstance().setScreen(new ApparelSelectScreen(this, working, slot));
    }

    private void save() {
        ClientApparel.setSelection(working.copy());
        ArcadeNetwork.CHANNEL.sendToServer(new SaveApparelPacket(working));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingClose || pendingSelectIndex >= 0) {
            // 上一次点击的按压反馈还没播完，忽略这一帧的新点击，避免连击打断反馈。
            return true;
        }

        for (int i = 0; i < SLOTS.length; i++) {
            int y = rowYAt(i);
            int btnX = rowX + rowW - SELECT_BTN_W;
            if (MenuChrome.inRect(mouseX, mouseY, btnX, y, SELECT_BTN_W, SELECT_BTN_H)) {
                pendingSelectIndex = i;
                selectPressAnim[i].start(MenuTween.now());
                playClick();
                return true;
            }
        }

        if (MenuChrome.inRect(mouseX, mouseY, rowX, footY, SAVE_BTN_W, SAVE_BTN_H)) {
            savePressAnim.start(MenuTween.now());
            save();
            playClick();
            return true;
        }
        int closeX = rowX + rowW - CLOSE_BTN_W;
        if (MenuChrome.inRect(mouseX, mouseY, closeX, footY, CLOSE_BTN_W, CLOSE_BTN_H)) {
            pendingClose = true;
            closePressAnim.start(MenuTween.now());
            playClick();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        if (pendingClose && closePressAnim.isDone(now)) {
            pendingClose = false;
            onClose();
            return;
        }
        if (pendingSelectIndex >= 0 && selectPressAnim[pendingSelectIndex].isDone(now)) {
            int index = pendingSelectIndex;
            pendingSelectIndex = -1;
            openSelect(SLOTS[index]);
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());
        gg.drawCenteredString(font, "共享服饰", left + panelW / 2, top + 10,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, fade.alpha()));
        gg.drawCenteredString(font, "§7所有配装共用这一套外观", left + panelW / 2, top + 26,
                FlatTheme.withAlpha(FlatTheme.TEXT_DIM, fade.alpha()));

        boolean interactionLocked = pendingClose || pendingSelectIndex >= 0;
        for (int i = 0; i < SLOTS.length; i++) {
            renderSlotRow(gg, i, SLOTS[i], rowYAt(i), mouseX, mouseY, now, interactionLocked);
        }

        renderFooter(gg, mouseX, mouseY, now, fade.alpha(), interactionLocked);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void renderSlotRow(GuiGraphics gg, int index, ApparelSlot slot, int y, int mouseX, int mouseY, long now,
                                boolean interactionLocked) {
        float cascadeT = cascadeAnim[index].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));

        drawFadedRow(gg, rowX, drawY - 2, rowW, 24, cascadeT);
        gg.drawString(font, slot.displayName(), rowX + 6, drawY + 4,
                FlatTheme.withAlpha(FlatTheme.TEXT_DIM, cascadeT), false);

        String key = working.selectedKey(slot).orElse(null);
        String name = key == null ? "§8— 未选 —" : ClientApparel.registry().find(key).map(ApparelItem::displayName).orElse(key);
        if (key != null) {
            ItemStack icon = ClientApparel.iconFor(key);
            if (!icon.isEmpty()) {
                gg.renderItem(icon, rowX + 58, drawY);
            }
        }
        gg.drawString(font, trim(name, rowW - 128), rowX + 78, drawY + 4,
                FlatTheme.withAlpha(key == null ? FlatTheme.TEXT_DIM : FlatTheme.TEXT, cascadeT), false);

        int btnX = rowX + rowW - SELECT_BTN_W;
        boolean hovered = !interactionLocked
                && MenuChrome.inRect(mouseX, mouseY, btnX, y, SELECT_BTN_W, SELECT_BTN_H);
        float pressScale = MenuChrome.pressScale(selectPressAnim[index], now);
        MenuChrome.drawScaled(gg, btnX + SELECT_BTN_W / 2, drawY + SELECT_BTN_H / 2, pressScale, () ->
                MenuChrome.drawGhostButton(gg, font, btnX, drawY, SELECT_BTN_W, SELECT_BTN_H, "选择", hovered, true, cascadeT));
    }

    /**
     * {@link FlatTheme#row} 的等价形状但支持透明度，用于本行的开场级联淡入
     * （{@code FlatTheme.row} 本身无 alpha 参数，无法直接用于逐行错峰淡入）。
     */
    private void drawFadedRow(GuiGraphics gg, int x, int y, int w, int h, float alpha) {
        gg.fill(x, y, x + w, y + h, FlatTheme.withAlpha(FlatTheme.SURFACE, alpha));
        gg.fill(x, y, x + w, y + 1, FlatTheme.withAlpha(FlatTheme.DIVIDER, alpha));
    }

    private void renderFooter(GuiGraphics gg, int mouseX, int mouseY, long now, float alpha, boolean interactionLocked) {
        boolean saveHovered = !interactionLocked
                && MenuChrome.inRect(mouseX, mouseY, rowX, footY, SAVE_BTN_W, SAVE_BTN_H);
        float savePress = MenuChrome.pressScale(savePressAnim, now);
        MenuChrome.drawPrimaryButton(gg, font, rowX, footY, SAVE_BTN_W, SAVE_BTN_H, "保存服饰",
                saveHovered, true, false, 0f, 0f, savePress);

        int closeX = rowX + rowW - CLOSE_BTN_W;
        boolean closeHovered = !interactionLocked
                && MenuChrome.inRect(mouseX, mouseY, closeX, footY, CLOSE_BTN_W, CLOSE_BTN_H);
        float closePress = MenuChrome.pressScale(closePressAnim, now);
        MenuChrome.drawScaled(gg, closeX + CLOSE_BTN_W / 2, footY + CLOSE_BTN_H / 2, closePress, () ->
                MenuChrome.drawGhostButton(gg, font, closeX, footY, CLOSE_BTN_W, CLOSE_BTN_H, "关闭", closeHovered, true, alpha));
    }

    private String trim(String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && font.width(out + "…") > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
