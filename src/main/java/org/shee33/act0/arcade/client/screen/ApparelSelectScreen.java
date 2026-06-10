package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientApparel;
import org.shee33.act0.arcade.client.ClientUnlocks;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSelection;
import org.shee33.act0.arcade.loadout.ApparelSlot;

import java.util.ArrayList;
import java.util.List;

/** 选择某个服饰槽位的服饰。 */
public final class ApparelSelectScreen extends Screen {
    private static final int W = 300;
    private static final int H = 190;
    private static final int CELL = 54;
    private static final int GAP = 8;
    private static final int COLS = 4;

    private final LoadoutScreen parent;
    private final ApparelSelection selection;
    private final ApparelSlot slot;
    private final List<ApparelItem> options = new ArrayList<>();
    private int left;
    private int top;
    private int gridX;
    private int gridY;

    public ApparelSelectScreen(LoadoutScreen parent, ApparelSelection selection, ApparelSlot slot) {
        super(Component.literal("选择服饰"));
        this.parent = parent;
        this.selection = selection;
        this.slot = slot;
    }

    @Override
    protected void init() {
        options.clear();
        options.addAll(ClientApparel.registry().itemsForSlot(slot));
        left = (width - W) / 2;
        top = (height - H) / 2;
        gridX = left + 16;
        gridY = top + 38;
        addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
            selection.set(slot, null);
            Minecraft.getInstance().setScreen(parent);
        }).bounds(left + W / 2 - 84, top + H - 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(left + W / 2 + 4, top + H - 28, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, W, H);
        gg.drawCenteredString(font, "§l" + slot.displayName(), left + W / 2, top + 10, PixelTheme.ACCENT);
        gg.drawCenteredString(font, "§7默认服饰免费，未解锁服饰点击购买", left + W / 2, top + 24, PixelTheme.TEXT_DIM);
        String selected = selection.selectedKey(slot).orElse(null);
        for (int i = 0; i < options.size(); i++) {
            ApparelItem item = options.get(i);
            int x = gridX + (i % COLS) * (CELL + GAP);
            int y = gridY + (i / COLS) * (CELL + GAP);
            boolean locked = !item.isDefault() && !ClientUnlocks.isUnlocked(item.key());
            boolean isSelected = item.key().equals(selected);
            PixelTheme.row(gg, x, y, CELL, CELL, isSelected);
            ItemStack icon = ClientApparel.iconFor(item);
            if (!icon.isEmpty()) {
                gg.renderItem(icon, x + (CELL - 16) / 2, y + 8);
            }
            gg.drawCenteredString(font, trim(item.displayName(), CELL - 4), x + CELL / 2, y + CELL - 18,
                    locked ? PixelTheme.TEXT_DIM : PixelTheme.TEXT);
            if (locked) {
                gg.fill(x, y, x + CELL, y + CELL, 0x88000000);
                gg.drawCenteredString(font, "§c" + item.price(), x + CELL / 2, y + CELL / 2 - 4, 0xFFFF5555);
            }
        }
        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < options.size(); i++) {
                ApparelItem item = options.get(i);
                int x = gridX + (i % COLS) * (CELL + GAP);
                int y = gridY + (i / COLS) * (CELL + GAP);
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    if (!item.isDefault() && !ClientUnlocks.isUnlocked(item.key())) {
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.connection.sendCommand("arcade buy " + item.key());
                        }
                        return true;
                    }
                    selection.set(slot, item.key());
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
