package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
 */
public final class LoadoutScreen extends Screen {

    private static final int MAX_PANEL_W = 560;
    private static final int MAX_PANEL_H = 420;
    private static final int MAX_CARD_W = 156;
    private static final int MAX_CARD_H = 62;

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

        int rowX = editorX();
        int rowW = editorW();
        int btnW = 18;
        int y = editorTop();

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleClass(-1))
                .bounds(rowX + rowW - btnW * 2 - 2, y, btnW, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleClass(1))
                .bounds(rowX + rowW - btnW, y, btnW, 16).build());
        y += rowH;

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            final LoadoutSlot s = slot;
            addRenderableWidget(Button.builder(Component.literal("选择"), b -> openSelect(s))
                    .bounds(rowX + rowW - btnW * 2 - 2, y, btnW * 2 + 2, 16).build());
            y += rowH;
        }

        int footY = top + panelH - 30;
        if (rowW >= 330) {
            addRenderableWidget(Button.builder(Component.literal("§a保存修改"), b -> saveOnly())
                    .bounds(rowX, footY, 82, 20).build());
            addRenderableWidget(Button.builder(Component.literal("下次复活使用"), b -> selectForNextRespawn())
                    .bounds(rowX + 88, footY, 108, 20).build());
            addRenderableWidget(Button.builder(Component.literal("设为默认"), b -> setDefaultLoadout())
                    .bounds(rowX + 202, footY, 82, 20).build());
            addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                    .bounds(rowX + rowW - 54, footY, 54, 20).build());
        } else {
            int half = (rowW - 6) / 2;
            addRenderableWidget(Button.builder(Component.literal("§a保存修改"), b -> saveOnly())
                    .bounds(rowX, footY - 22, half, 18).build());
            addRenderableWidget(Button.builder(Component.literal("下次复活使用"), b -> selectForNextRespawn())
                    .bounds(rowX + half + 6, footY - 22, half, 18).build());
            addRenderableWidget(Button.builder(Component.literal("设为默认"), b -> setDefaultLoadout())
                    .bounds(rowX, footY, half, 18).build());
            addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                    .bounds(rowX + half + 6, footY, half, 18).build());
        }
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

    private void cycleClass(int dir) {
        PlayerClassType[] all = PlayerClassType.values();
        int idx = working().classType().ordinal();
        working().setClassType(all[Math.floorMod(idx + dir, all.length)]);
        sanitizeSelections();
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

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, panelW, panelH);

        gg.drawCenteredString(font, "§l配装", left + panelW / 2, top + 8, PixelTheme.ACCENT);
        if (panelH >= 330) {
            gg.drawString(font, "§7点击左侧方案卡片进行修改", left + 18, top + 28, PixelTheme.TEXT_DIM, false);
        }
        gg.drawString(font, "§7当前编辑：§f第 " + (activeIndex + 1) + " 套",
                editorX(), top + (panelH < 350 ? 36 : 46), PixelTheme.TEXT_DIM, false);

        renderProfileCards(gg, mouseX, mouseY);

        int rowX = editorX();
        int rowW = editorW();
        int y = editorTop();

        PixelTheme.row(gg, rowX, y - 2, rowW, 18, false);
        gg.drawString(font, "职业", rowX + 4, y + 4, PixelTheme.TEXT_DIM, false);
        gg.drawString(font, classLabel(working().classType()), rowX + 60, y + 4, PixelTheme.TEXT, false);
        y += rowH;

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            PixelTheme.row(gg, rowX, y - 2, rowW, 18, false);
            gg.drawString(font, slotLabel(slot), rowX + 4, y + 4, PixelTheme.TEXT_DIM, false);

            String key = working().slotItemKey(slot).orElse(null);
            String name = key == null ? "§8— 未选 —" : registry.find(key).map(LoadoutItem::displayName).orElse(key);
            int color = key == null ? PixelTheme.TEXT_DIM : PixelTheme.TEXT;
            int iconX = rowX + 56;
            int textX = iconX + 20;
            if (key != null) {
                ItemStack icon = ClientCatalog.iconFor(key);
                if (!icon.isEmpty()) {
                    gg.renderItem(icon, iconX, y);
                }
            }
            gg.drawString(font, trim(name, rowW - (textX - rowX) - 48), textX, y + 4, color, false);
            y += rowH;
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int x = cardX();
            int y = cardY(i);
            if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) {
                activeIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderProfileCards(GuiGraphics gg, int mouseX, int mouseY) {
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int x = cardX();
            int y = cardY(i);
            boolean selected = i == activeIndex;
            boolean active = i == loadoutSet.activeIndex();
            boolean def = i == loadoutSet.defaultIndex();
            boolean hovered = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH;
            PixelTheme.row(gg, x, y, cardW, cardH, selected || hovered);
            int border = selected ? PixelTheme.ACCENT : (active ? 0xFF9ACD68 : 0x55395B2F);
            gg.fill(x, y, x + cardW, y + 1, border);
            gg.fill(x, y + cardH - 1, x + cardW, y + cardH, border);
            gg.fill(x, y, x + 1, y + cardH, border);
            gg.fill(x + cardW - 1, y, x + cardW, y + cardH, border);

            Loadout loadout = loadoutSet.get(i);
            String tags = (def ? " §e默认" : "") + (active ? " §a已选" : "");
            gg.drawString(font, trim("§f第 " + (i + 1) + " 套" + tags, cardW - 12),
                    x + 6, y + 5, PixelTheme.TEXT, false);
            if (cardH >= 44) {
                gg.drawString(font, "§7" + classLabel(loadout.classType()), x + 6, y + 17, PixelTheme.TEXT_DIM, false);
            }

            int iconY = y + Math.max(cardH >= 44 ? 30 : 20, cardH - 24);
            int iconGap = Math.max(34, (cardW - 28) / 3);
            renderCardWeapon(gg, loadout, LoadoutSlot.PRIMARY_WEAPON, x + 8, iconY);
            renderCardWeapon(gg, loadout, LoadoutSlot.SECONDARY_WEAPON, x + 8 + iconGap, iconY);
            renderCardWeapon(gg, loadout, LoadoutSlot.MELEE, x + 8 + iconGap * 2, iconY);
        }
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
        gg.fill(x + 1, y + 1, x + 15, y + 15, 0x55000000);
        gg.drawCenteredString(font, "-", x + 8, y + 4, PixelTheme.TEXT_DIM);
    }

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
