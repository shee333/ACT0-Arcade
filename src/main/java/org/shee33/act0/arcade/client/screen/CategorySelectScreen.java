package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器分类选择界面（客户端）：当一个槽位对应多个武器分类（如主武器 = 步枪 / 冲锋枪 / 狙击枪 …）时，
 * 先让玩家选一个细分类别，再跳转到 {@link WeaponSelectScreen} 浏览该类别下的具体武器。
 *
 * <p>仅展示<b>当前职业下确有武器</b>的分类，避免出现空类别。渲染基于 {@link FlatTheme} 程序化扁平面板。
 *
 * <p>MenuChrome/MenuTween 化改造（11 屏统一动效工作的一部分）：本屏此前是零动效的重灾区
 * （面板/文字瞬间以全 alpha 出现，点击零反馈瞬间跳转），现补齐与 {@link LoadoutQuickSelectScreen}
 * 同款手法——{@link FlatTheme#fadeIn(long)} 做面板级淡入；每个分类行 + 底部返回按钮走
 * {@link MenuTween.Anim} 驱动的错峰级联淡入（{@code i*30ms}）；点击不再瞬间导航，先播放
 * {@link MenuChrome#pressScale(MenuTween.Anim, long)} 按压回弹，动画播完（{@link MenuTween.Anim#isDone(long)}）
 * 才真正执行 {@link #openCategory(WeaponCategory)} / {@link #onClose()}，与 {@code CreateRoomScreen} 的
 * {@code pendingNav} 延迟导航手法一致。分类行不再用原版 {@code Button.builder}，改为
 * {@link MenuChrome#drawGhostButton} 手绘 + 手动命中检测（同 {@link LoadoutQuickSelectScreen}）。
 */
public final class CategorySelectScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int ROW_GAP = 4;
    private static final int PANEL_W = 220;
    private static final int HEADER_H = 34;

    /** 单行开场淡入+上移时长（三次方缓出，与 FlatTheme.fadeIn 默认时长一致）。 */
    private static final long CASCADE_DURATION_MS = 220L;
    /** 相邻行错峰间隔。 */
    private static final long CASCADE_STAGGER_MS = 30L;
    /** 开场上移幅度（2-4px 区间）。 */
    private static final int CASCADE_OFFSET_Y = 3;
    /** 按压回弹时长，与 CreateRoomAnimator 的按压动画同款 220ms outBack。 */
    private static final long PRESS_DURATION_MS = 220L;

    private final LoadoutScreen parent;
    private final Loadout working;
    private final LoadoutSlot slot;
    private final LoadoutRegistry registry;
    private final List<WeaponCategory> categories = new ArrayList<>();

    private final long openedAtMs = MenuTween.now();

    /** 每个分类行的开场级联动画，下标即 {@link #categories} 下标。 */
    private final List<MenuTween.Anim> rowCascade = new ArrayList<>();
    /** 每个分类行的点击按压回弹动画，下标即 {@link #categories} 下标。 */
    private final List<MenuTween.Anim> rowPress = new ArrayList<>();
    /** 底部返回按钮的开场级联动画（紧随最后一行之后错峰）。 */
    private MenuTween.Anim backCascade;
    /** 底部返回按钮的按压回弹动画。 */
    private MenuTween.Anim backPress;
    /** 空状态提示文字的开场级联动画（与第一行同拍，无额外错峰）。 */
    private MenuTween.Anim emptyCascade;

    /** 已点击、等待按压动画播完再真正跳转的分类行下标；-1 = 无待处理选择。 */
    private int pendingCategoryIndex = -1;
    /** 返回按钮是否已点击、等待按压动画播完再真正 {@link #onClose()}。 */
    private boolean pendingBack;

    private int left;
    private int top;
    private int panelH;

    public CategorySelectScreen(LoadoutScreen parent, Loadout working, LoadoutSlot slot) {
        super(Component.literal("选择类别"));
        this.parent = parent;
        this.working = working;
        this.slot = slot;
        this.registry = ClientCatalog.registry();
    }

    @Override
    protected void init() {
        categories.clear();
        for (WeaponCategory cat : WeaponCategory.forSlot(slot)) {
            if (!registry.availableItemsInCategory(cat, working.classType()).isEmpty()) {
                categories.add(cat);
            }
        }

        this.panelH = HEADER_H + Math.max(1, categories.size()) * (ROW_H + ROW_GAP) + 36;
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - panelH) / 2;

        rowCascade.clear();
        rowPress.clear();
        for (int i = 0; i < categories.size(); i++) {
            MenuTween.Anim cascade = new MenuTween.Anim(CASCADE_DURATION_MS, i * CASCADE_STAGGER_MS);
            cascade.start(openedAtMs);
            rowCascade.add(cascade);
            rowPress.add(new MenuTween.Anim(PRESS_DURATION_MS, 0L));
        }

        backCascade = new MenuTween.Anim(CASCADE_DURATION_MS, categories.size() * CASCADE_STAGGER_MS);
        backCascade.start(openedAtMs);
        backPress = new MenuTween.Anim(PRESS_DURATION_MS, 0L);

        emptyCascade = new MenuTween.Anim(CASCADE_DURATION_MS, 0L);
        emptyCascade.start(openedAtMs);

        pendingCategoryIndex = -1;
        pendingBack = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingCategoryIndex >= 0 || pendingBack) {
            // 上一次点击的按压反馈还没播完，忽略这一帧的新点击，避免连击打断反馈。
            return true;
        }

        int rowX = left + 16;
        int rowW = PANEL_W - 32;
        for (int i = 0; i < categories.size(); i++) {
            int rowY = top + HEADER_H + i * (ROW_H + ROW_GAP);
            if (MenuChrome.inRect(mouseX, mouseY, rowX, rowY, rowW, ROW_H)) {
                pendingCategoryIndex = i;
                rowPress.get(i).start(MenuTween.now());
                playClick();
                return true;
            }
        }

        int backX = left + PANEL_W / 2 - 40;
        int backY = top + panelH - 26;
        if (MenuChrome.inRect(mouseX, mouseY, backX, backY, 80, 20)) {
            pendingBack = true;
            backPress.start(MenuTween.now());
            playClick();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openCategory(WeaponCategory category) {
        Minecraft.getInstance().setScreen(
                new WeaponSelectScreen(parent, this, working, slot, category));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingCategoryIndex >= 0 && rowPress.get(pendingCategoryIndex).isDone(now)) {
            int index = pendingCategoryIndex;
            pendingCategoryIndex = -1;
            openCategory(categories.get(index));
            return;
        }
        if (pendingBack && backPress.isDone(now)) {
            pendingBack = false;
            onClose();
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, PANEL_W, panelH, fade.alpha());
        gg.drawCenteredString(font, slotLabel(slot), left + PANEL_W / 2, top + 10,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, fade.alpha()));

        if (categories.isEmpty()) {
            float emptyT = emptyCascade.easedT(now, MenuTween.Ease.OUT_CUBIC);
            gg.drawCenteredString(font, "§7暂无可选武器", left + PANEL_W / 2, top + HEADER_H + 8,
                    FlatTheme.withAlpha(FlatTheme.TEXT_DIM, emptyT));
        }

        for (int i = 0; i < categories.size(); i++) {
            renderRow(gg, i, mouseX, mouseY, now);
        }
        renderBackButton(gg, mouseX, mouseY, now);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    /** 分类行：{@link MenuChrome#drawGhostButton} 全宽样式 + 级联淡入上移 + 按压回弹缩放。 */
    private void renderRow(GuiGraphics gg, int index, int mouseX, int mouseY, long now) {
        WeaponCategory cat = categories.get(index);
        int count = registry.availableItemsInCategory(cat, working.classType()).size();
        String label = cat.displayName() + " §7(" + count + ")";

        int x = left + 16;
        int w = PANEL_W - 32;
        int y = top + HEADER_H + index * (ROW_H + ROW_GAP);

        float cascadeT = rowCascade.get(index).easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        boolean hovered = pendingCategoryIndex < 0 && !pendingBack
                && MenuChrome.inRect(mouseX, mouseY, x, drawY, w, ROW_H);
        float scale = MenuChrome.pressScale(rowPress.get(index), now);
        int cx = x + w / 2;
        int cy = drawY + ROW_H / 2;

        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> MenuChrome.drawGhostButton(gg, font, x, drawY, w, ROW_H, label, hovered, true, cascadeT));
    }

    /** 底部返回按钮：与分类行同款 ghost button 风格 + 独立级联/按压动画。 */
    private void renderBackButton(GuiGraphics gg, int mouseX, int mouseY, long now) {
        int x = left + PANEL_W / 2 - 40;
        int y = top + panelH - 26;
        int w = 80;
        int h = 20;

        float cascadeT = backCascade.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        boolean hovered = pendingCategoryIndex < 0 && !pendingBack
                && MenuChrome.inRect(mouseX, mouseY, x, drawY, w, h);
        float scale = MenuChrome.pressScale(backPress, now);
        int cx = x + w / 2;
        int cy = drawY + h / 2;

        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> MenuChrome.drawGhostButton(gg, font, x, drawY, w, h, "返回", hovered, true, cascadeT));
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static String slotLabel(LoadoutSlot slot) {
        return switch (slot) {
            case PRIMARY_WEAPON -> "主武器";
            case SECONDARY_WEAPON -> "副武器";
            case MELEE -> "近战";
            case GADGET_1 -> "装置 1";
            case GADGET_2 -> "装置 2";
            case THROWABLE -> "投掷物";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
