package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SaveLoadoutPacket;
import org.shee33.act0.arcade.network.SelectLoadoutPacket;

/**
 * 像素风配装编辑界面（客户端）。
 *
 * <p>左侧固定 5 个方案卡片，右侧编辑当前方案。布局会按 Minecraft GUI 逻辑尺寸自适应，避免高 UI
 * 缩放或小窗口时面板溢出屏幕。
 *
 * <p>视觉层已升级为 {@link MenuChrome} 金色强调语言 + {@link MenuTween} 驱动的动效：
 * 方案卡片的选中态由原先的静态边框颜色切换改为唯一滑动指示器（{@link #drawSelectionIndicator}），
 * 职业切换、按钮按压回弹、开场级联均由 {@link LoadoutScreenAnimator} 持有的
 * {@link MenuTween.Anim} 驱动。原有的 {@link #openedAtMs} + {@link FlatTheme#fadeIn(long)}
 * 外层面板淡入基线保持不变，新动效是叠加在其上的，不是替代。
 */
public final class LoadoutScreen extends Screen {

    private static final int MAX_PANEL_W = 560;
    private static final int MAX_PANEL_H = 420;
    private static final int MAX_CARD_W = 156;
    private static final int MAX_CARD_H = 62;

    private static final int NAV_NONE = 0;
    private static final int NAV_SLOT = 1;
    private static final int NAV_APPAREL = 2;
    private static final int NAV_CLOSE = 3;

    private final LoadoutSet loadoutSet;
    private final LoadoutRegistry registry;

    private int activeIndex;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int cardW;
    private int cardH;
    private int cardGap;
    private int rowH;
    private final long openedAtMs = System.currentTimeMillis();
    private final LoadoutScreenAnimator anim = new LoadoutScreenAnimator(openedAtMs);

    // 方案卡片唯一滑动指示器的起止 Y（配合 anim.indicatorY 做 lerp）
    private int indicatorFromY;
    private int indicatorToY;

    // 职业标签横向切换过渡状态
    private String classOldLabel = "";
    private int classDir = 1;

    // 次级导航（选择武器/服饰/关闭）：按压回弹播完才真正跳转，回弹才有机会被看到
    private int pendingNav = NAV_NONE;
    private LoadoutSlot pendingSlot;

    public LoadoutScreen(Loadout loadout) {
        this(LoadoutSet.single(loadout));
    }

    public LoadoutScreen(LoadoutSet loadoutSet) {
        super(Component.literal("配装"));
        this.loadoutSet = loadoutSet;
        this.activeIndex = loadoutSet.activeIndex();
        this.registry = ClientCatalog.registry();
    }

    private Loadout working() {
        return loadoutSet.get(activeIndex);
    }

    @Override
    protected void init() {
        computeLayout();
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
    }

    private void computeLayout() {
        panelW = Math.min(MAX_PANEL_W, Math.max(260, width - 24));
        panelH = Math.min(MAX_PANEL_H, Math.max(220, height - 24));
        rowH = panelH < 350 ? 18 : 20;
        cardGap = panelH < 350 ? 5 : 8;
        cardW = clamp(panelW / 3, 118, MAX_CARD_W);
        int cardAreaH = panelH - (panelH < 350 ? 58 : 70);
        cardH = clamp((cardAreaH - cardGap * (LoadoutSet.MAX_SLOTS - 1)) / LoadoutSet.MAX_SLOTS,
                34, MAX_CARD_H);
    }

    // ============================================================
    // 交互：方案卡片选择 / 职业切换 / 次级导航（均手动命中检测，见 mouseClicked）
    // ============================================================

    private void selectCard(int index) {
        if (index == activeIndex) {
            return;
        }
        long now = MenuTween.now();
        indicatorFromY = cardY(activeIndex);
        indicatorToY = cardY(index);
        anim.indicatorY.start(now);
        activeIndex = index;
        playClick();
    }

    private void cycleClass(int dir) {
        long now = MenuTween.now();
        PlayerClassType[] all = PlayerClassType.values();
        int idx = working().classType().ordinal();
        classOldLabel = classLabel(working().classType());
        working().setClassType(all[MenuChrome.wrapIndex(idx, dir, all.length)]);
        classDir = dir;
        anim.classSlide.start(now);
        anim.press[dir > 0 ? LoadoutScreenAnimator.PRESS_CLASS_RIGHT : LoadoutScreenAnimator.PRESS_CLASS_LEFT]
                .start(now);
        sanitizeSelections();
        playClick();
    }

    private void requestOpenSelect(LoadoutSlot slot) {
        if (pendingNav != NAV_NONE) {
            return;
        }
        pendingNav = NAV_SLOT;
        pendingSlot = slot;
        anim.press[LoadoutScreenAnimator.slotPressIndex(slot)].start(MenuTween.now());
        playClick();
    }

    private void requestOpenApparel() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        pendingNav = NAV_APPAREL;
        anim.press[LoadoutScreenAnimator.PRESS_APPAREL].start(MenuTween.now());
        playClick();
    }

    private void requestClose() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        pendingNav = NAV_CLOSE;
        anim.press[LoadoutScreenAnimator.PRESS_CLOSE].start(MenuTween.now());
        playClick();
    }

    private void requestSave() {
        anim.press[LoadoutScreenAnimator.PRESS_SAVE].start(MenuTween.now());
        playClick();
        saveOnly();
    }

    private void requestRespawn() {
        anim.press[LoadoutScreenAnimator.PRESS_RESPAWN].start(MenuTween.now());
        playClick();
        selectForNextRespawn();
    }

    private void requestDefault() {
        anim.press[LoadoutScreenAnimator.PRESS_DEFAULT].start(MenuTween.now());
        playClick();
        setDefaultLoadout();
    }

    private void openSelect(LoadoutSlot slot) {
        if (minecraft == null) {
            return;
        }
        java.util.List<org.shee33.act0.arcade.loadout.WeaponCategory> cats =
                org.shee33.act0.arcade.loadout.WeaponCategory.forSlot(slot);
        if (cats.size() > 1) {
            minecraft.setScreen(new CategorySelectScreen(this, working(), slot));
        } else {
            org.shee33.act0.arcade.loadout.WeaponCategory only = cats.isEmpty() ? null : cats.get(0);
            minecraft.setScreen(new WeaponSelectScreen(this, this, working(), slot, only));
        }
    }

    private void sanitizeSelections() {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            String key = working().slotItemKey(slot).orElse(null);
            if (key == null) {
                continue;
            }
            boolean stillOk = registry.availableItems(slot, working().classType())
                    .stream().anyMatch(i -> i.key().equals(key));
            if (!stillOk) {
                working().setSlot(slot, null);
            }
        }
    }

    private void saveOnly() {
        ArcadeNetwork.CHANNEL.sendToServer(new SaveLoadoutPacket(activeIndex, working(), false, false));
    }

    private void selectForNextRespawn() {
        loadoutSet.setActiveIndex(activeIndex);
        ArcadeNetwork.CHANNEL.sendToServer(new SelectLoadoutPacket(activeIndex, SelectLoadoutPacket.Action.SELECT_NEXT));
    }

    private void setDefaultLoadout() {
        loadoutSet.setDefaultIndex(activeIndex);
        loadoutSet.setActiveIndex(activeIndex);
        ArcadeNetwork.CHANNEL.sendToServer(new SelectLoadoutPacket(activeIndex, SelectLoadoutPacket.Action.SET_DEFAULT));
    }

    private void openApparelScreen() {
        if (minecraft != null) {
            minecraft.setScreen(new ApparelScreen(this));
        }
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈；边界静默的调整不经过这里。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ============================================================
    // 鼠标输入：手动命中检测（镜像 CreateRoomScreen 的做法，不再使用 vanilla Button）
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            if (MenuChrome.inRect(mouseX, mouseY, cardX(), cardY(i), cardW, cardH)) {
                selectCard(i);
                return true;
            }
        }

        int rowX = editorX();
        int rowW = editorW();
        int btnW = 18;
        int classY = classRowY();

        if (MenuChrome.inRect(mouseX, mouseY, rowX + rowW - btnW * 2 - 2, classY, btnW, 16)) {
            cycleClass(-1);
            return true;
        }
        if (MenuChrome.inRect(mouseX, mouseY, rowX + rowW - btnW, classY, btnW, 16)) {
            cycleClass(1);
            return true;
        }

        LoadoutSlot[] slots = LoadoutSlot.values();
        for (int i = 0; i < slots.length; i++) {
            if (MenuChrome.inRect(mouseX, mouseY, rowX + rowW - btnW * 2 - 2, slotRowY(i), btnW * 2 + 2, 16)) {
                requestOpenSelect(slots[i]);
                return true;
            }
        }

        int apparelW = Math.min(120, rowW);
        if (MenuChrome.inRect(mouseX, mouseY, rowX, apparelY(), apparelW, 18)) {
            requestOpenApparel();
            return true;
        }

        int footY = top + panelH - 30;
        if (rowW >= 330) {
            if (MenuChrome.inRect(mouseX, mouseY, rowX, footY, 82, 20)) {
                requestSave();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX + 88, footY, 108, 20)) {
                requestRespawn();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX + 202, footY, 82, 20)) {
                requestDefault();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX + rowW - 54, footY, 54, 20)) {
                requestClose();
                return true;
            }
        } else {
            int half = (rowW - 6) / 2;
            if (MenuChrome.inRect(mouseX, mouseY, rowX, footY - 22, half, 18)) {
                requestSave();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX + half + 6, footY - 22, half, 18)) {
                requestRespawn();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX, footY, half, 18)) {
                requestDefault();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, rowX + half + 6, footY, half, 18)) {
                requestClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingNav != NAV_NONE) {
            boolean ready = switch (pendingNav) {
                case NAV_SLOT -> anim.press[LoadoutScreenAnimator.slotPressIndex(pendingSlot)].isDone(now);
                case NAV_APPAREL -> anim.press[LoadoutScreenAnimator.PRESS_APPAREL].isDone(now);
                case NAV_CLOSE -> anim.press[LoadoutScreenAnimator.PRESS_CLOSE].isDone(now);
                default -> true;
            };
            if (ready) {
                int nav = pendingNav;
                LoadoutSlot slot = pendingSlot;
                pendingNav = NAV_NONE;
                pendingSlot = null;
                switch (nav) {
                    case NAV_SLOT -> openSelect(slot);
                    case NAV_APPAREL -> openApparelScreen();
                    case NAV_CLOSE -> onClose();
                    default -> {
                    }
                }
                return;
            }
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        float panelAlpha = fade.alpha();
        FlatTheme.panel(gg, left, top, panelW, panelH, panelAlpha);

        gg.drawCenteredString(font, "配装", left + panelW / 2, top + 8, FlatTheme.TEXT_HEADER);
        if (panelH >= 330) {
            gg.drawString(font, "§7点击左侧方案卡片进行修改", left + 18, top + 28, FlatTheme.TEXT_DIM, false);
        }
        gg.drawString(font, "§7当前编辑：§f第 " + (activeIndex + 1) + " 套",
                editorX(), top + (panelH < 350 ? 36 : 46), FlatTheme.TEXT_DIM, false);

        renderProfileCards(gg, mouseX, mouseY, now, panelAlpha);

        int rowX = editorX();
        int rowW = editorW();
        int btnW = 18;

        renderClassRow(gg, mouseX, mouseY, now, panelAlpha, rowX, rowW, btnW);
        renderSlotRows(gg, mouseX, mouseY, now, panelAlpha, rowX, rowW, btnW);
        renderApparelLink(gg, mouseX, mouseY, now, panelAlpha, rowX, rowW);
        renderFooter(gg, mouseX, mouseY, now, panelAlpha, rowX, rowW);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------
    // 左栏：方案卡片 + 唯一滑动选中指示器
    // ------------------------------------------------------------

    private void renderProfileCards(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            float e = anim.leftColumn[i].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int xOffset = Math.round(-10 * (1 - e));
            float alpha = e * panelAlpha;

            int x = cardX() + xOffset;
            int y = cardY(i);
            boolean selected = i == activeIndex;
            boolean active = i == loadoutSet.activeIndex();
            boolean def = i == loadoutSet.defaultIndex();
            boolean hovered = MenuChrome.inRect(mouseX, mouseY, x, y, cardW, cardH);
            FlatTheme.row(gg, x, y, cardW, cardH, selected || hovered);

            if (!selected) {
                int border = FlatTheme.withAlpha(FlatTheme.BORDER, alpha);
                gg.fill(x, y, x + cardW, y + 1, border);
                gg.fill(x, y + cardH - 1, x + cardW, y + cardH, border);
                gg.fill(x, y, x + 1, y + cardH, border);
                gg.fill(x + cardW - 1, y, x + cardW, y + cardH, border);
            }

            Loadout loadout = loadoutSet.get(i);
            String tags = (def ? " §e默认" : "") + (active ? " §a已选" : "");
            gg.drawString(font, trim("§f第 " + (i + 1) + " 套" + tags, cardW - 12),
                    x + 6, y + 5, FlatTheme.withAlpha(FlatTheme.TEXT, alpha), false);
            if (cardH >= 44) {
                gg.drawString(font, "§7" + classLabel(loadout.classType()), x + 6, y + 17,
                        FlatTheme.withAlpha(FlatTheme.TEXT_DIM, alpha), false);
            }

            int iconY = y + Math.max(cardH >= 44 ? 30 : 20, cardH - 24);
            int iconGap = Math.max(34, (cardW - 28) / 3);
            renderCardWeapon(gg, loadout, LoadoutSlot.PRIMARY_WEAPON, x + 8, iconY);
            renderCardWeapon(gg, loadout, LoadoutSlot.SECONDARY_WEAPON, x + 8 + iconGap, iconY);
            renderCardWeapon(gg, loadout, LoadoutSlot.MELEE, x + 8 + iconGap * 2, iconY);
        }

        drawSelectionIndicator(gg, now, panelAlpha);
    }

    /**
     * 方案卡片唯一滑动选中指示器：取代原先的静态边框颜色切换。位置随
     * {@link LoadoutScreenAnimator#indicatorY} 在起止卡片间 lerp，途中叠加
     * {@link MenuTween#pulse(float)} 纵向拉伸，镜像 {@code CreateRoomScreen#drawIndicator}。
     */
    private void drawSelectionIndicator(GuiGraphics gg, long now, float panelAlpha) {
        int x = cardX();
        int w = cardW;
        int h = cardH;
        int yTop;
        float stretch;
        if (anim.indicatorY.isRunning() && !anim.indicatorY.isDone(now)) {
            float e = anim.indicatorY.easedT(now, MenuTween.Ease.OUT_CUBIC);
            yTop = Math.round(MenuChrome.lerp(indicatorFromY, indicatorToY, e));
            stretch = MenuTween.pulse(e);
        } else {
            yTop = cardY(activeIndex);
            stretch = 1f;
        }
        int cx = x + w / 2;
        int cy = yTop + h / 2;
        int finalYTop = yTop;
        MenuChrome.drawScaledAxis(gg, cx, cy, 1f, stretch, () -> {
            gg.fill(x, finalYTop, x + w, finalYTop + h, FlatTheme.withAlpha(MenuChrome.GOLD, 0.10f * panelAlpha));
            int gold = FlatTheme.withAlpha(MenuChrome.GOLD, panelAlpha);
            gg.fill(x, finalYTop, x + w, finalYTop + 1, gold);
            gg.fill(x, finalYTop + h - 1, x + w, finalYTop + h, gold);
            gg.fill(x, finalYTop, x + 1, finalYTop + h, gold);
            gg.fill(x + w - 1, finalYTop, x + w, finalYTop + h, gold);
        });
    }

    private void renderCardWeapon(GuiGraphics gg, Loadout loadout, LoadoutSlot slot, int x, int y) {
        String key = loadout.slotItemKey(slot).orElse(null);
        if (key != null) {
            ItemStack icon = ClientCatalog.iconFor(key);
            if (!icon.isEmpty()) {
                gg.renderItem(icon, x, y);
                return;
            }
        }
        gg.fill(x + 1, y + 1, x + 15, y + 15, FlatTheme.SURFACE_DISABLED);
        gg.drawCenteredString(font, "-", x + 8, y + 4, FlatTheme.TEXT_DIM);
    }

    // ------------------------------------------------------------
    // 右栏：职业行 + 槽位行 + 服饰/外观链接
    // ------------------------------------------------------------

    private void renderClassRow(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha,
                                 int rowX, int rowW, int btnW) {
        float e = anim.rightColumn[0].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int yOff = Math.round(8 * (1 - e));
        float alpha = e * panelAlpha;

        int y = classRowY() + yOff;
        FlatTheme.row(gg, rowX, y - 2, rowW, 18, false);
        gg.drawString(font, "职业", rowX + 4, y + 4, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, alpha), false);

        int arrowLeftX = rowX + rowW - btnW * 2 - 2;
        int arrowRightX = rowX + rowW - btnW;
        int labelX1 = rowX + 60;
        int labelX2 = arrowLeftX - 4;
        int labelW = Math.max(20, labelX2 - labelX1);
        int cx = labelX1 + labelW / 2;
        int cy = y + 8;
        gg.enableScissor(labelX1, y - 2, labelX1 + labelW, y + 16);
        MenuChrome.drawSlideX(gg, font, cx, cy, classOldLabel, classLabel(working().classType()), classDir,
                anim.classSlide, now, FlatTheme.withAlpha(FlatTheme.TEXT, alpha), labelW);
        gg.disableScissor();

        boolean leftHover = MenuChrome.inRect(mouseX, mouseY, arrowLeftX, y, btnW, 16);
        boolean rightHover = MenuChrome.inRect(mouseX, mouseY, arrowRightX, y, btnW, 16);
        MenuChrome.drawLightControl(gg, font, arrowLeftX, y, btnW, 16, "◀", leftHover, true,
                MenuChrome.pressScale(anim.press[LoadoutScreenAnimator.PRESS_CLASS_LEFT], now), alpha);
        MenuChrome.drawLightControl(gg, font, arrowRightX, y, btnW, 16, "▶", rightHover, true,
                MenuChrome.pressScale(anim.press[LoadoutScreenAnimator.PRESS_CLASS_RIGHT], now), alpha);
    }

    private void renderSlotRows(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha,
                                 int rowX, int rowW, int btnW) {
        LoadoutSlot[] slots = LoadoutSlot.values();
        for (int i = 0; i < slots.length; i++) {
            LoadoutSlot slot = slots[i];
            float e = anim.rightColumn[i + 1].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int yOff = Math.round(8 * (1 - e));
            float alpha = e * panelAlpha;

            int y = slotRowY(i) + yOff;
            FlatTheme.row(gg, rowX, y - 2, rowW, 18, false);
            gg.drawString(font, slotLabel(slot), rowX + 4, y + 4, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, alpha), false);

            String key = working().slotItemKey(slot).orElse(null);
            String name = key == null ? "§8— 未选 —" : registry.find(key).map(LoadoutItem::displayName).orElse(key);
            int color = FlatTheme.withAlpha(key == null ? FlatTheme.TEXT_DIM : FlatTheme.TEXT, alpha);
            int iconX = rowX + 56;
            int textX = iconX + 20;
            if (key != null) {
                ItemStack icon = ClientCatalog.iconFor(key);
                if (!icon.isEmpty()) {
                    gg.renderItem(icon, iconX, y);
                }
            }

            int selectBtnX = rowX + rowW - btnW * 2 - 2;
            int selectBtnW = btnW * 2 + 2;
            gg.drawString(font, trim(name, selectBtnX - textX - 6), textX, y + 4, color, false);

            boolean hovered = MenuChrome.inRect(mouseX, mouseY, selectBtnX, y, selectBtnW, 16);
            float press = MenuChrome.pressScale(anim.press[LoadoutScreenAnimator.slotPressIndex(slot)], now);
            int scx = selectBtnX + selectBtnW / 2;
            int scy = y + 8;
            int finalY = y;
            MenuChrome.drawScaled(gg, scx, scy, press, () ->
                    MenuChrome.drawGhostButton(gg, font, selectBtnX, finalY, selectBtnW, 16, "选择", hovered, true, alpha));
        }
    }

    private void renderApparelLink(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha,
                                    int rowX, int rowW) {
        int idx = LoadoutSlot.values().length + 1;
        float e = anim.rightColumn[idx].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int yOff = Math.round(8 * (1 - e));
        float alpha = e * panelAlpha;

        int y = apparelY() + yOff;
        int w = Math.min(120, rowW);
        boolean hovered = MenuChrome.inRect(mouseX, mouseY, rowX, y, w, 18);
        float press = MenuChrome.pressScale(anim.press[LoadoutScreenAnimator.PRESS_APPAREL], now);
        int cx = rowX + w / 2;
        int cy = y + 9;
        MenuChrome.drawScaled(gg, cx, cy, press, () ->
                MenuChrome.drawGhostButton(gg, font, rowX, y, w, 18, "服饰 / 外观", hovered, true, alpha));
    }

    // ------------------------------------------------------------
    // 底部：保存修改（主按钮）/ 下次复活使用 / 设为默认 / 关闭（幽灵按钮）
    // ------------------------------------------------------------

    private void renderFooter(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha,
                               int rowX, int rowW) {
        int footY = top + panelH - 30;
        if (rowW >= 330) {
            drawPrimarySaveButton(gg, mouseX, mouseY, now, panelAlpha, rowX, footY, 82, 20);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX + 88, footY, 108, 20,
                    "下次复活使用", LoadoutScreenAnimator.PRESS_RESPAWN);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX + 202, footY, 82, 20,
                    "设为默认", LoadoutScreenAnimator.PRESS_DEFAULT);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX + rowW - 54, footY, 54, 20,
                    "关闭", LoadoutScreenAnimator.PRESS_CLOSE);
        } else {
            int half = (rowW - 6) / 2;
            drawPrimarySaveButton(gg, mouseX, mouseY, now, panelAlpha, rowX, footY - 22, half, 18);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX + half + 6, footY - 22, half, 18,
                    "下次复活使用", LoadoutScreenAnimator.PRESS_RESPAWN);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX, footY, half, 18,
                    "设为默认", LoadoutScreenAnimator.PRESS_DEFAULT);
            drawGhostFooterButton(gg, mouseX, mouseY, now, panelAlpha, rowX + half + 6, footY, half, 18,
                    "关闭", LoadoutScreenAnimator.PRESS_CLOSE);
        }
    }

    private void drawPrimarySaveButton(GuiGraphics gg, int mouseX, int mouseY, long now, float alpha,
                                        int x, int y, int w, int h) {
        boolean hovered = MenuChrome.inRect(mouseX, mouseY, x, y, w, h);
        float press = MenuChrome.pressScale(anim.press[LoadoutScreenAnimator.PRESS_SAVE], now);
        MenuChrome.drawPrimaryButton(gg, font, x, y, w, h, "保存修改", hovered, true, false, 0f, 0f, press);
    }

    private void drawGhostFooterButton(GuiGraphics gg, int mouseX, int mouseY, long now, float alpha,
                                        int x, int y, int w, int h, String label, int pressIndex) {
        boolean hovered = MenuChrome.inRect(mouseX, mouseY, x, y, w, h);
        float press = MenuChrome.pressScale(anim.press[pressIndex], now);
        int cx = x + w / 2;
        int cy = y + h / 2;
        MenuChrome.drawScaled(gg, cx, cy, press, () ->
                MenuChrome.drawGhostButton(gg, font, x, y, w, h, label, hovered, true, alpha));
    }

    // ------------------------------------------------------------
    // 布局几何辅助
    // ------------------------------------------------------------

    private int cardX() {
        return left + 14;
    }

    private int cardY(int index) {
        return top + (panelH < 350 ? 42 : 56) + index * (cardH + cardGap);
    }

    private int editorX() {
        return left + cardW + 38;
    }

    private int editorW() {
        return panelW - cardW - 52;
    }

    private int editorTop() {
        return top + (panelH < 350 ? 54 : 72);
    }

    private int classRowY() {
        return editorTop();
    }

    private int slotRowY(int slotIndex) {
        return editorTop() + rowH * (1 + slotIndex);
    }

    private int apparelY() {
        return editorTop() + rowH * (1 + LoadoutSlot.values().length) + 2;
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String classLabel(PlayerClassType type) {
        return switch (type) {
            case ASSAULT -> "突击兵";
            case SUPPORT -> "支援兵";
            case ENGINEER -> "工程兵";
            case RECON -> "侦察兵";
        };
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
