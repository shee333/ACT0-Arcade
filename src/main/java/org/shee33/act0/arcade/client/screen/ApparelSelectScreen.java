package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientApparel;
import org.shee33.act0.arcade.client.ClientUnlocks;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择某个服饰槽位的服饰。
 *
 * <p>MenuChrome/MenuTween 化改造（11 屏统一动效工作的一部分）：本屏此前是零动效的重灾区
 * （面板/文字/格子瞬间以全 alpha 出现，点击零反馈瞬间跳转），现补齐与 {@link CategorySelectScreen} /
 * {@link LoadoutQuickSelectScreen} 同款手法——{@link FlatTheme#fadeIn(long)} 做面板级淡入；
 * 每个物品格 + 底部"清空"/"返回"按钮走 {@link MenuTween.Anim} 驱动的错峰级联淡入（{@code i*25ms}）；
 * 点击不再瞬间导航，先播放 {@link MenuChrome#pressScale(MenuTween.Anim, long)} 按压回弹，
 * 动画播完（{@link MenuTween.Anim#isDone(long)}）才真正执行选中/清空+关闭，与 {@code CreateRoomScreen}
 * 的 {@code pendingNav} 延迟导航手法一致。购买未解锁物品不关闭本屏，因此只播放按压反馈、不进入
 * 延迟导航队列。底部按钮不再用原版 {@code Button.builder}，改为 {@link MenuChrome#drawGhostButton}
 * 手绘 + 手动命中检测（同 {@link CategorySelectScreen}）。
 *
 * <p>既有的"视口外物品静默截断、无滚动条"行为（约原 74/101 行）为本次任务范围外的既有缺口，
 * 未做改动——沿用 {@link WeaponSelectScreen} / {@link GunModScreen} 的滚动条模式并非可简单嫁接
 * （需要额外的滚动偏移状态 + 滚轮事件处理 + 独立视口裁剪，非一行可完成），故按任务说明保留原状。
 */
public final class ApparelSelectScreen extends Screen {
    private static final int MAX_W = 320;
    private static final int MAX_H = 220;
    private static final int CELL = 54;
    private static final int GAP = 8;

    /** 单元格/按钮开场淡入+上移时长（三次方缓出，与 FlatTheme.fadeIn 默认时长一致）。 */
    private static final long CASCADE_DURATION_MS = 220L;
    /** 相邻格子错峰间隔。 */
    private static final long CASCADE_STAGGER_MS = 25L;
    /** 开场上移幅度（2-4px 区间）。 */
    private static final int CASCADE_OFFSET_Y = 3;
    /** 按压回弹时长，与 CreateRoomAnimator 的按压动画同款 220ms outBack。 */
    private static final long PRESS_DURATION_MS = 220L;

    private final Screen parent;
    private final ApparelSelection selection;
    private final ApparelSlot slot;
    private final List<ApparelItem> options = new ArrayList<>();
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int cols;
    private int gridX;
    private int gridY;

    private final long openedAtMs = MenuTween.now();

    /** 每个物品格的开场级联动画，下标即 {@link #options} 下标。 */
    private final List<MenuTween.Anim> cellCascade = new ArrayList<>();
    /** 每个物品格的点击按压回弹动画，下标即 {@link #options} 下标。 */
    private final List<MenuTween.Anim> cellPress = new ArrayList<>();
    /** "清空"幽灵按钮的开场级联动画（紧随最后一格之后错峰）。 */
    private MenuTween.Anim clearCascade;
    /** "清空"幽灵按钮的按压回弹动画。 */
    private MenuTween.Anim clearPress;
    /** "返回"幽灵按钮的开场级联动画（与"清空"同拍）。 */
    private MenuTween.Anim backCascade;
    /** "返回"幽灵按钮的按压回弹动画。 */
    private MenuTween.Anim backPress;

    /** 已点击（未锁定）、等待按压动画播完再真正选中+关闭的格子下标；-1 = 无待处理选择。 */
    private int pendingCellIndex = -1;
    /** "清空"是否已点击、等待按压动画播完再真正清空+关闭。 */
    private boolean pendingClear;
    /** "返回"是否已点击、等待按压动画播完再真正 {@link #onClose()}。 */
    private boolean pendingBack;

    public ApparelSelectScreen(Screen parent, ApparelSelection selection, ApparelSlot slot) {
        super(Component.literal("选择服饰"));
        this.parent = parent;
        this.selection = selection;
        this.slot = slot;
    }

    @Override
    protected void init() {
        options.clear();
        options.addAll(ClientApparel.registry().itemsForSlot(slot));
        panelW = Math.min(MAX_W, Math.max(220, width - 24));
        panelH = Math.min(MAX_H, Math.max(170, height - 24));
        cols = Math.max(2, Math.min(4, (panelW - 32 + GAP) / (CELL + GAP)));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        gridX = left + 16;
        gridY = top + 38;

        cellCascade.clear();
        cellPress.clear();
        for (int i = 0; i < options.size(); i++) {
            MenuTween.Anim cascade = new MenuTween.Anim(CASCADE_DURATION_MS, i * CASCADE_STAGGER_MS);
            cascade.start(openedAtMs);
            cellCascade.add(cascade);
            cellPress.add(new MenuTween.Anim(PRESS_DURATION_MS, 0L));
        }
        long footerDelay = options.size() * CASCADE_STAGGER_MS;
        clearCascade = new MenuTween.Anim(CASCADE_DURATION_MS, footerDelay);
        clearCascade.start(openedAtMs);
        clearPress = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
        backCascade = new MenuTween.Anim(CASCADE_DURATION_MS, footerDelay);
        backCascade.start(openedAtMs);
        backPress = new MenuTween.Anim(PRESS_DURATION_MS, 0L);

        pendingCellIndex = -1;
        pendingClear = false;
        pendingBack = false;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingCellIndex >= 0 && cellPress.get(pendingCellIndex).isDone(now)) {
            int index = pendingCellIndex;
            pendingCellIndex = -1;
            selection.set(slot, options.get(index).key());
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        if (pendingClear && clearPress.isDone(now)) {
            pendingClear = false;
            selection.set(slot, null);
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        if (pendingBack && backPress.isDone(now)) {
            pendingBack = false;
            onClose();
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());
        gg.drawCenteredString(font, slot.displayName(), left + panelW / 2, top + 10,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, fade.alpha()));
        gg.drawCenteredString(font, "§7默认免费，未解锁点击购买", left + panelW / 2, top + 24,
                FlatTheme.withAlpha(FlatTheme.TEXT_DIM, fade.alpha()));

        String selected = selection.selectedKey(slot).orElse(null);
        for (int i = 0; i < options.size(); i++) {
            renderCell(gg, i, now, selected);
        }
        renderClearButton(gg, mouseX, mouseY, now);
        renderBackButton(gg, mouseX, mouseY, now);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    /** 单格：级联淡入+上移 + 按压回弹缩放，正文绘制委派给 {@link #drawCellContent}。 */
    private void renderCell(GuiGraphics gg, int index, long now, String selected) {
        ApparelItem item = options.get(index);
        int x = gridX + (index % cols) * (CELL + GAP);
        int y = gridY + (index / cols) * (CELL + GAP);
        if (y + CELL > top + panelH - 34) {
            return;
        }
        boolean locked = !item.isDefault() && !ClientUnlocks.isUnlocked(item.key());
        boolean isSelected = item.key().equals(selected);

        float cascadeT = cellCascade.get(index).easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        float scale = MenuChrome.pressScale(cellPress.get(index), now);
        int cx = x + CELL / 2;
        int cy = drawY + CELL / 2;

        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> drawCellContent(gg, item, x, drawY, locked, isSelected, cascadeT));
    }

    /** 单格正文：表面/边框跟随 {@code alpha} 淡入；锁定态叠加 {@link MenuChrome#LOCK_OVERLAY}
     * 并保留价格的 {@link FlatTheme#DANGER} 高亮渲染。 */
    private void drawCellContent(GuiGraphics gg, ApparelItem item, int x, int y, boolean locked, boolean isSelected,
                                  float alpha) {
        gg.fill(x, y, x + CELL, y + CELL,
                FlatTheme.withAlpha(isSelected ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE, alpha));
        gg.fill(x, y, x + CELL, y + 1, FlatTheme.withAlpha(isSelected ? FlatTheme.ACCENT : FlatTheme.DIVIDER, alpha));
        ItemStack icon = ClientApparel.iconFor(item);
        if (!icon.isEmpty()) {
            gg.renderItem(icon, x + (CELL - 16) / 2, y + 8);
        }
        gg.drawCenteredString(font, trim(item.displayName(), CELL - 4), x + CELL / 2, y + CELL - 18,
                FlatTheme.withAlpha(locked ? FlatTheme.TEXT_DIM : FlatTheme.TEXT, alpha));
        if (locked) {
            gg.fill(x, y, x + CELL, y + CELL, FlatTheme.withAlpha(MenuChrome.LOCK_OVERLAY, alpha));
            gg.drawCenteredString(font, String.valueOf(item.price()), x + CELL / 2, y + CELL / 2 - 4,
                    FlatTheme.withAlpha(FlatTheme.DANGER, alpha));
        }
    }

    /** "清空"幽灵按钮：与格子同款级联淡入 + 按压回弹，点击后延迟到按压播完才清空+关闭。 */
    private void renderClearButton(GuiGraphics gg, int mouseX, int mouseY, long now) {
        int w = 80;
        int h = 20;
        int x = left + panelW / 2 - 84;
        int y = top + panelH - 28;
        float cascadeT = clearCascade.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        boolean hovered = pendingCellIndex < 0 && !pendingClear && !pendingBack
                && MenuChrome.inRect(mouseX, mouseY, x, drawY, w, h);
        float scale = MenuChrome.pressScale(clearPress, now);
        int cx = x + w / 2;
        int cy = drawY + h / 2;
        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> MenuChrome.drawGhostButton(gg, font, x, drawY, w, h, "清空", hovered, true, cascadeT));
    }

    /** "返回"幽灵按钮：与"清空"同款级联淡入 + 按压回弹，点击后延迟到按压播完才 onClose。 */
    private void renderBackButton(GuiGraphics gg, int mouseX, int mouseY, long now) {
        int w = 80;
        int h = 20;
        int x = left + panelW / 2 + 4;
        int y = top + panelH - 28;
        float cascadeT = backCascade.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        boolean hovered = pendingCellIndex < 0 && !pendingClear && !pendingBack
                && MenuChrome.inRect(mouseX, mouseY, x, drawY, w, h);
        float scale = MenuChrome.pressScale(backPress, now);
        int cx = x + w / 2;
        int cy = drawY + h / 2;
        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> MenuChrome.drawGhostButton(gg, font, x, drawY, w, h, "返回", hovered, true, cascadeT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingCellIndex >= 0 || pendingClear || pendingBack) {
            // 上一次点击的按压反馈还没播完，忽略这一帧的新点击，避免连击打断反馈。
            return true;
        }

        int footerY = top + panelH - 28;
        int clearX = left + panelW / 2 - 84;
        if (MenuChrome.inRect(mouseX, mouseY, clearX, footerY, 80, 20)) {
            pendingClear = true;
            clearPress.start(MenuTween.now());
            playClick();
            return true;
        }
        int backX = left + panelW / 2 + 4;
        if (MenuChrome.inRect(mouseX, mouseY, backX, footerY, 80, 20)) {
            pendingBack = true;
            backPress.start(MenuTween.now());
            playClick();
            return true;
        }

        for (int i = 0; i < options.size(); i++) {
            ApparelItem item = options.get(i);
            int x = gridX + (i % cols) * (CELL + GAP);
            int y = gridY + (i / cols) * (CELL + GAP);
            if (y + CELL > top + panelH - 34) {
                continue;
            }
            if (MenuChrome.inRect(mouseX, mouseY, x, y, CELL, CELL)) {
                cellPress.get(i).start(MenuTween.now());
                playClick();
                if (!item.isDefault() && !ClientUnlocks.isUnlocked(item.key())) {
                    // 购买请求不关闭本屏，仅播放按压反馈，不进入延迟导航队列。
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.connection.sendCommand("arcade buy " + item.key());
                    }
                    return true;
                }
                pendingCellIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
