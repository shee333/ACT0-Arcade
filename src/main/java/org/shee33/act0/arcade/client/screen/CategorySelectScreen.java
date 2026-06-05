package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
 * <p>仅展示<b>当前职业下确有武器</b>的分类，避免出现空类别。渲染基于 {@link PixelTheme} 程序化像素面板。
 */
public final class CategorySelectScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int ROW_GAP = 4;
    private static final int PANEL_W = 220;
    private static final int HEADER_H = 34;

    private final LoadoutScreen parent;
    private final Loadout working;
    private final LoadoutSlot slot;
    private final LoadoutRegistry registry;
    private final List<WeaponCategory> categories = new ArrayList<>();

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

        int y = top + HEADER_H;
        for (WeaponCategory cat : categories) {
            final WeaponCategory c = cat;
            int count = registry.availableItemsInCategory(cat, working.classType()).size();
            addRenderableWidget(Button.builder(
                            Component.literal(cat.displayName() + " §7(" + count + ")"),
                            b -> openCategory(c))
                    .bounds(left + 16, y, PANEL_W - 32, ROW_H).build());
            y += ROW_H + ROW_GAP;
        }

        int footY = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(left + PANEL_W / 2 - 40, footY, 80, 20).build());
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
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, PANEL_W, panelH);
        gg.drawCenteredString(font, "§l" + slotLabel(slot), left + PANEL_W / 2, top + 10, PixelTheme.ACCENT);

        if (categories.isEmpty()) {
            gg.drawCenteredString(font, "§7暂无可选武器", left + PANEL_W / 2, top + HEADER_H + 8, PixelTheme.TEXT_DIM);
        }

        super.render(gg, mouseX, mouseY, partialTick);
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
