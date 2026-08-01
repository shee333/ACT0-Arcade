package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientApparel;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.SaveApparelPacket;

/** 单独的共享服饰编辑界面。 */
public final class ApparelScreen extends Screen {
    private static final int MAX_W = 360;
    private static final int MAX_H = 220;

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

    public ApparelScreen(Screen parent) {
        super(Component.literal("服饰"));
        this.parent = parent;
        this.working = ClientApparel.selection().copy();
    }

    @Override
    protected void init() {
        computeLayout();
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        rowX = left + 16;
        rowW = panelW - 32;
        rowY = top + 48;

        int btnW = 44;
        int y = rowY;
        for (ApparelSlot slot : ApparelSlot.values()) {
            final ApparelSlot s = slot;
            addRenderableWidget(Button.builder(Component.literal("选择"), b -> openSelect(s))
                    .bounds(rowX + rowW - btnW, y, btnW, 16).build());
            y += rowH;
        }

        int footY = top + panelH - 28;
        addRenderableWidget(Button.builder(Component.literal("§a保存服饰"), b -> save())
                .bounds(rowX, footY, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(rowX + rowW - 54, footY, 54, 20).build());
    }

    private void computeLayout() {
        panelW = Math.min(MAX_W, Math.max(260, width - 24));
        panelH = Math.min(MAX_H, Math.max(190, height - 24));
        rowH = panelH < 210 ? 28 : 32;
    }

    private void openSelect(ApparelSlot slot) {
        Minecraft.getInstance().setScreen(new ApparelSelectScreen(this, working, slot));
    }

    private void save() {
        ClientApparel.setSelection(working.copy());
        ArcadeNetwork.CHANNEL.sendToServer(new SaveApparelPacket(working));
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        FlatTheme.panel(gg, left, top, panelW, panelH);
        gg.drawCenteredString(font, "共享服饰", left + panelW / 2, top + 10, FlatTheme.TEXT_HEADER);
        gg.drawCenteredString(font, "§7所有配装共用这一套外观", left + panelW / 2, top + 26, FlatTheme.TEXT_DIM);

        int y = rowY;
        for (ApparelSlot slot : ApparelSlot.values()) {
            FlatTheme.row(gg, rowX, y - 2, rowW, 24, false);
            gg.drawString(font, slot.displayName(), rowX + 6, y + 4, FlatTheme.TEXT_DIM, false);
            String key = working.selectedKey(slot).orElse(null);
            String name = key == null ? "§8— 未选 —" : ClientApparel.registry().find(key).map(ApparelItem::displayName).orElse(key);
            if (key != null) {
                ItemStack icon = ClientApparel.iconFor(key);
                if (!icon.isEmpty()) {
                    gg.renderItem(icon, rowX + 58, y);
                }
            }
            gg.drawString(font, trim(name, rowW - 128), rowX + 78, y + 4,
                    key == null ? FlatTheme.TEXT_DIM : FlatTheme.TEXT, false);
            y += rowH;
        }
        super.render(gg, mouseX, mouseY, partialTick);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
