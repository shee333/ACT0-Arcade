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

import java.util.List;

/**
 * 像素风配装编辑界面（客户端）。
 *
 * <p>展示多个配装方案、职业与六个槽位；玩家可切换方案并编辑当前方案，保存时把当前方案发回服务端持久化。
 *
 * <p>渲染基于原生 {@link GuiGraphics} + {@link PixelTheme} 程序化像素面板，不依赖外部贴图资源。
 * 兵种装置槽（{@link LoadoutSlot#isCoreCombatSlot()} 为 {@code false}）以"街机禁用"提示标注，
 * 直观呈现"街机模式无缝禁用兵种道具"的设计。
 */
public final class LoadoutScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int ROW_H = 20;
    private static final int HEADER_H = 38;

    private final LoadoutSet loadoutSet;
    private final LoadoutRegistry registry;
    private int activeIndex;
    private int left;
    private int top;

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

    private int panelHeight() {
        return HEADER_H + ROW_H * (LoadoutSlot.values().length + 2) + 36;
    }

    @Override
    protected void init() {
        int h = panelHeight();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - h) / 2;

        int rowX = left + 12;
        int rowW = PANEL_W - 24;
        int btnW = 18;
        int y = top + HEADER_H;

        // 方案切换行
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleProfile(-1))
            .bounds(rowX + rowW - btnW * 2 - 2, y, btnW, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleProfile(1))
            .bounds(rowX + rowW - btnW, y, btnW, 16).build());
        y += ROW_H;

        // 职业切换行
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleClass(-1))
                .bounds(rowX + rowW - btnW * 2 - 2, y, btnW, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleClass(1))
                .bounds(rowX + rowW - btnW, y, btnW, 16).build());
        y += ROW_H;

        // 六个槽位行：点击「选择」打开二级武器选择界面
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            final LoadoutSlot s = slot;
            addRenderableWidget(Button.builder(Component.literal("选择"), b -> openSelect(s))
                    .bounds(rowX + rowW - btnW * 2 - 2, y, btnW * 2 + 2, 16).build());
            y += ROW_H;
        }

        // 底部：保存 / 取消
        int footY = top + h - 26;
        addRenderableWidget(Button.builder(Component.literal("§a保存"), b -> saveAndClose())
                .bounds(left + PANEL_W / 2 - 84, footY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(left + PANEL_W / 2 + 4, footY, 80, 20).build());
    }

    private void cycleProfile(int dir) {
        activeIndex = Math.floorMod(activeIndex + dir, LoadoutSet.MAX_SLOTS);
    }

    private void cycleClass(int dir) {
        PlayerClassType[] all = PlayerClassType.values();
        int idx = working().classType().ordinal();
        working().setClassType(all[Math.floorMod(idx + dir, all.length)]);
        sanitizeSelections();
    }

    /** 打开某槽位的武器选择：多分类先进类别选择，单分类直接进武器网格。 */
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

    /** 切换职业后，清掉对新职业不再可用的选择。 */
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

    private void saveAndClose() {
        loadoutSet.setActiveIndex(activeIndex);
        ArcadeNetwork.CHANNEL.sendToServer(new SaveLoadoutPacket(activeIndex, working()));
        onClose();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);

        int h = panelHeight();
        PixelTheme.panel(gg, left, top, PANEL_W, h);

        // 标题
        gg.drawCenteredString(font, "§l配装", left + PANEL_W / 2, top + 10, PixelTheme.ACCENT);

        int rowX = left + 12;
        int rowW = PANEL_W - 24;
        int y = top + HEADER_H;

        // 方案行
        PixelTheme.row(gg, rowX, y - 2, rowW, 18, false);
        gg.drawString(font, "方案", rowX + 4, y + 4, PixelTheme.TEXT_DIM, false);
        gg.drawString(font, "第 " + (activeIndex + 1) + " 套", rowX + 60, y + 4, PixelTheme.TEXT, false);
        y += ROW_H;

        // 职业行
        PixelTheme.row(gg, rowX, y - 2, rowW, 18, false);
        gg.drawString(font, "职业", rowX + 4, y + 4, PixelTheme.TEXT_DIM, false);
        gg.drawString(font, classLabel(working().classType()), rowX + 60, y + 4, PixelTheme.TEXT, false);
        y += ROW_H;

        // 槽位行
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            PixelTheme.row(gg, rowX, y - 2, rowW, 18, false);
            gg.drawString(font, slotLabel(slot), rowX + 4, y + 4, PixelTheme.TEXT_DIM, false);

            String key = working().slotItemKey(slot).orElse(null);
            String name = key == null ? "§8— 未选 —"
                    : registry.find(key).map(LoadoutItem::displayName).orElse(key);
            int color = key == null ? PixelTheme.TEXT_DIM : PixelTheme.TEXT;

            // 物品图标（来自服务端目录的真实物品，含 TaCZ 枪械模型）
            int iconX = rowX + 56;
            int textX = iconX + 20;
            if (key != null) {
                ItemStack icon = ClientCatalog.iconFor(key);
                if (!icon.isEmpty()) {
                    gg.renderItem(icon, iconX, y);
                }
            }
            gg.drawString(font, name, textX, y + 4, color, false);
            y += ROW_H;
        }

        super.render(gg, mouseX, mouseY, partialTick);
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
