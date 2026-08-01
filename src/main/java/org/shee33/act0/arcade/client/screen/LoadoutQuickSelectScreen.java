package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutSet;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SelectLoadoutPacket;

/** 对局中 B 键打开的简化配装选择界面：只显示 5 个方案卡片，点击即设为下次复活使用。 */
public final class LoadoutQuickSelectScreen extends Screen {

    private static final int CARD_W = 156;
    private static final int CARD_H = 62;
    private static final int GAP = 8;

    private final LoadoutSet loadoutSet;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private final long openedAtMs = System.currentTimeMillis();

    public LoadoutQuickSelectScreen(LoadoutSet loadoutSet) {
        super(Component.literal("选择配装"));
        this.loadoutSet = loadoutSet;
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
        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());
        int cardX = left + (panelW - CARD_W) / 2;
        int startY = top + 12;
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            renderCard(gg, i, cardX, startY + i * (CARD_H + GAP), mouseX, mouseY);
        }
        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cardX = left + (panelW - CARD_W) / 2;
        int startY = top + 12;
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int y = startY + i * (CARD_H + GAP);
            if (mouseX >= cardX && mouseX <= cardX + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                loadoutSet.setActiveIndex(i);
                ArcadeNetwork.CHANNEL.sendToServer(new SelectLoadoutPacket(i,
                        SelectLoadoutPacket.Action.SELECT_NEXT, true));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderCard(GuiGraphics gg, int index, int x, int y, int mouseX, int mouseY) {
        boolean active = index == loadoutSet.activeIndex();
        boolean def = index == loadoutSet.defaultIndex();
        boolean hovered = mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H;
        FlatTheme.row(gg, x, y, CARD_W, CARD_H, active || hovered);
        int border = active ? FlatTheme.ACCENT : FlatTheme.BORDER;
        gg.fill(x, y, x + CARD_W, y + 1, border);
        gg.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, border);
        gg.fill(x, y, x + 1, y + CARD_H, border);
        gg.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, border);

        Loadout loadout = loadoutSet.get(index);
        String tags = (def ? " §e默认" : "") + (active ? " §a已选" : "");
        gg.drawString(font, "§f第 " + (index + 1) + " 套" + tags, x + 6, y + 5, FlatTheme.TEXT, false);
        gg.drawString(font, "§7" + classLabel(loadout.classType()), x + 6, y + 17, FlatTheme.TEXT_DIM, false);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
