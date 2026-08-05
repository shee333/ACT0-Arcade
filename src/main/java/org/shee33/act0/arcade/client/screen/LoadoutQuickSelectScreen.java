package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SelectLoadoutPacket;

/**
 * 对局中 B 键打开的简化配装选择界面：只显示 5 个方案卡片，点击即设为下次复活使用。
 *
 * <p>MenuChrome/MenuTween 化改造（11 屏统一动效工作的一部分）：卡片开场走
 * {@link MenuTween.Anim} 驱动的错峰级联淡入 + 轻微上移；点击不再零反馈瞬间关闭——
 * 先播放 {@link MenuChrome#pressScale(MenuTween.Anim, long)} 按压回弹，动画播完
 * （{@link MenuTween.Anim#isDone(long)}）才真正发包+{@link #onClose()}，与
 * {@code CreateRoomScreen} 的 {@code pendingNav} 延迟导航手法一致。
 *
 * <p>选中态边框刻意只做颜色三级分层（金=已选 / 蓝=悬停 / 灰=空闲），不做滑动指示器——
 * 这是一个"点击即执行并关闭"的单次动作列表，选中态不会在界面内持续存在供用户来回切换，
 * 引入指示器滑动只会增加复杂度而无实际收益，属于刻意的范围裁剪而非遗漏。
 */
public final class LoadoutQuickSelectScreen extends Screen {

    private static final int CARD_W = 156;
    private static final int CARD_H = 62;
    private static final int GAP = 8;

    /** 单卡开场淡入+上移时长（三次方缓出，与 FlatTheme.fadeIn 默认时长一致）。 */
    private static final long CASCADE_DURATION_MS = 220L;
    /** 相邻卡片错峰间隔——5 张卡规模小，简单的 i*stagger 足够，无需分组错峰。 */
    private static final long CASCADE_STAGGER_MS = 35L;
    /** 开场上移幅度（2-4px 区间取上限）。 */
    private static final int CASCADE_OFFSET_Y = 4;
    /** 按压回弹时长，与 CreateRoomAnimator 的按压动画同款 220ms outBack。 */
    private static final long PRESS_DURATION_MS = 220L;

    private final LoadoutSet loadoutSet;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private final long openedAtMs = System.currentTimeMillis();

    /** 每张卡的开场级联动画，下标即卡片索引。 */
    private final MenuTween.Anim[] cascadeAnim = new MenuTween.Anim[LoadoutSet.MAX_SLOTS];
    /** 每张卡的点击按压回弹动画，下标即卡片索引。 */
    private final MenuTween.Anim[] pressAnim = new MenuTween.Anim[LoadoutSet.MAX_SLOTS];
    /** 已点击、等待按压动画播完再真正提交的卡片索引；-1 = 无待处理选择。 */
    private int pendingIndex = -1;

    public LoadoutQuickSelectScreen(LoadoutSet loadoutSet) {
        super(Component.literal("选择配装"));
        this.loadoutSet = loadoutSet;
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            cascadeAnim[i] = new MenuTween.Anim(CASCADE_DURATION_MS, i * CASCADE_STAGGER_MS);
            cascadeAnim[i].start(openedAtMs);
            pressAnim[i] = new MenuTween.Anim(PRESS_DURATION_MS, 0L);
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 20, CARD_W + 28);
        panelH = Math.min(height - 20, LoadoutSet.MAX_SLOTS * (CARD_H + GAP) - GAP + 24);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        if (pendingIndex >= 0 && pressAnim[pendingIndex].isDone(now)) {
            int index = pendingIndex;
            pendingIndex = -1;
            loadoutSet.setActiveIndex(index);
            ArcadeNetwork.CHANNEL.sendToServer(new SelectLoadoutPacket(index,
                    SelectLoadoutPacket.Action.SELECT_NEXT, true));
            onClose();
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());
        int cardX = left + (panelW - CARD_W) / 2;
        int startY = top + 12;
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            renderCard(gg, i, cardX, startY + i * (CARD_H + GAP), mouseX, mouseY, now);
        }
        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingIndex >= 0) {
            // 上一次点击的按压反馈还没播完，忽略这一帧的新点击，避免连击打断反馈。
            return true;
        }
        int cardX = left + (panelW - CARD_W) / 2;
        int startY = top + 12;
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int y = startY + i * (CARD_H + GAP);
            if (mouseX >= cardX && mouseX <= cardX + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                pendingIndex = i;
                pressAnim[i].start(MenuTween.now());
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderCard(GuiGraphics gg, int index, int x, int y, int mouseX, int mouseY, long now) {
        boolean active = index == loadoutSet.activeIndex();
        boolean def = index == loadoutSet.defaultIndex();
        boolean hovered = pendingIndex < 0
                && mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H;

        float cascadeT = cascadeAnim[index].easedT(now, MenuTween.Ease.OUT_CUBIC);
        float alpha = cascadeT;
        int drawY = y + Math.round(CASCADE_OFFSET_Y * (1f - cascadeT));
        float scale = MenuChrome.pressScale(pressAnim[index], now);
        int cx = x + CARD_W / 2;
        int cy = drawY + CARD_H / 2;

        MenuChrome.drawScaled(gg, cx, cy, scale,
                () -> drawCardContent(gg, index, x, drawY, active, def, hovered, alpha));
    }

    /** 卡片正文：背景+边框跟随 {@code alpha} 淡入，边框颜色走金/蓝/灰三级层级。 */
    private void drawCardContent(GuiGraphics gg, int index, int x, int y, boolean active, boolean def,
                                  boolean hovered, float alpha) {
        int bg = (active || hovered) ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE;
        gg.fill(x, y, x + CARD_W, y + CARD_H, FlatTheme.withAlpha(bg, alpha));
        // 三级边框层级：金=当前已选（最高优先级）＞ 蓝=悬停中 ＞ 灰=空闲。
        int border = active ? MenuChrome.GOLD : (hovered ? FlatTheme.ACCENT : FlatTheme.BORDER);
        int borderA = FlatTheme.withAlpha(border, alpha);
        gg.fill(x, y, x + CARD_W, y + 1, borderA);
        gg.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, borderA);
        gg.fill(x, y, x + 1, y + CARD_H, borderA);
        gg.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, borderA);

        Loadout loadout = loadoutSet.get(index);
        String tags = (def ? " §e默认" : "") + (active ? " §a已选" : "");
        gg.drawString(font, "§f第 " + (index + 1) + " 套" + tags, x + 6, y + 5,
                FlatTheme.withAlpha(FlatTheme.TEXT, alpha), false);
        gg.drawString(font, "§7" + classLabel(loadout.classType()), x + 6, y + 17,
                FlatTheme.withAlpha(FlatTheme.TEXT_DIM, alpha), false);
        renderWeapon(gg, loadout, LoadoutSlot.PRIMARY_WEAPON, x + 8, y + 34);
        renderWeapon(gg, loadout, LoadoutSlot.SECONDARY_WEAPON, x + 58, y + 34);
        renderWeapon(gg, loadout, LoadoutSlot.MELEE, x + 108, y + 34);
    }

    private void renderWeapon(GuiGraphics gg, Loadout loadout, LoadoutSlot slot, int x, int y) {
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

    private static String classLabel(PlayerClassType type) {
        return switch (type) {
            case ASSAULT -> "突击兵";
            case SUPPORT -> "支援兵";
            case ENGINEER -> "工程兵";
            case RECON -> "侦察兵";
        };
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
