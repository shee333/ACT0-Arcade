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

    private static final int PANEL_W = 560;
    private static final int PANEL_H = 420;
    private static final int CARD_W = 156;
    private static final int CARD_H = 62;
    private static final int CARD_GAP = 8;
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
        return PANEL_H;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        int rowX = editorX();
        int rowW = editorW();
        int btnW = 18;
        int y = top + 72;

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

        // 底部：保存 / 下次复活使用 / 默认 / 关闭
        int footY = top + PANEL_H - 30;
        addRenderableWidget(Button.builder(Component.literal("§a保存修改"), b -> saveOnly())
            .bounds(rowX, footY, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下次复活使用"), b -> selectForNextRespawn())
            .bounds(rowX + 88, footY, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("设为默认"), b -> setDefaultLoadout())
            .bounds(rowX + 202, footY, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
            .bounds(rowX + rowW - 54, footY, 54, 20).build());
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

        PixelTheme.panel(gg, left, top, PANEL_W, PANEL_H);

        // 标题
        gg.drawCenteredString(font, "§l配装", left + PANEL_W / 2, top + 10, PixelTheme.ACCENT);
        gg.drawString(font, "§7点击左侧方案卡片进行修改", left + 18, top + 30, PixelTheme.TEXT_DIM, false);
        gg.drawString(font, "§7当前编辑：§f第 " + (activeIndex + 1) + " 套", editorX(), top + 46, PixelTheme.TEXT_DIM, false);

        renderProfileCards(gg, mouseX, mouseY);

        int rowX = editorX();
        int rowW = editorW();
        int y = top + 72;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int x = cardX();
            int y = cardY(i);
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                activeIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int cardX() {
        return left + 18;
    }

    private int cardY(int index) {
        return top + 56 + index * (CARD_H + CARD_GAP);
    }

    private int editorX() {
        return left + CARD_W + 44;
    }

    private int editorW() {
        return PANEL_W - CARD_W - 62;
    }

    private void renderProfileCards(GuiGraphics gg, int mouseX, int mouseY) {
        for (int i = 0; i < LoadoutSet.MAX_SLOTS; i++) {
            int x = cardX();
            int y = cardY(i);
            boolean selected = i == activeIndex;
            boolean active = i == loadoutSet.activeIndex();
            boolean def = i == loadoutSet.defaultIndex();
            boolean hovered = mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H;
            PixelTheme.row(gg, x, y, CARD_W, CARD_H, selected || hovered);
            int border = selected ? PixelTheme.ACCENT : (active ? 0xFF9ACD68 : 0x55395B2F);
            gg.fill(x, y, x + CARD_W, y + 1, border);
            gg.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, border);
            gg.fill(x, y, x + 1, y + CARD_H, border);
            gg.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, border);

            Loadout loadout = loadoutSet.get(i);
            String tags = (def ? " §e默认" : "") + (active ? " §a已选" : "");
            gg.drawString(font, "§f第 " + (i + 1) + " 套" + tags, x + 6, y + 5, PixelTheme.TEXT, false);
            gg.drawString(font, "§7" + classLabel(loadout.classType()), x + 6, y + 17, PixelTheme.TEXT_DIM, false);

            renderCardWeapon(gg, loadout, LoadoutSlot.PRIMARY_WEAPON, x + 8, y + 34);
            renderCardWeapon(gg, loadout, LoadoutSlot.SECONDARY_WEAPON, x + 58, y + 34);
            renderCardWeapon(gg, loadout, LoadoutSlot.MELEE, x + 108, y + 34);
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
