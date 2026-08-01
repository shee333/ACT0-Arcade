package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientBuyFeedback;
import org.shee33.act0.arcade.client.ClientCatalog;
import org.shee33.act0.arcade.client.ClientUnlocks;
import org.shee33.act0.arcade.economy.BuyOutcome;
import org.shee33.act0.arcade.integration.TaczBridge;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级武器选择界面（客户端）：列出某槽位（可选再细分到某武器分类）下、对当前职业可用的武器，支持滚动浏览。
 *
 * <p>解锁状态由客户端 {@link ClientUnlocks} 缓存提供：
 * <ul>
 *   <li>已解锁 / 默认武器：正常显示，点击即选中并返回配装界面。</li>
 *   <li>未解锁武器：覆盖灰色蒙版 + 红字「未解锁」，点击弹出购买确认（弹层内显示价格）。</li>
 * </ul>
 *
 * <p>武器数量超过可视行数时，网格区域可用鼠标滚轮上下滚动，界面尺寸保持固定，不会随武器数量膨胀。
 *
 * <p>渲染基于 {@link GuiGraphics} + {@link FlatTheme} 程序化扁平面板，不依赖外部贴图。
 */
public final class WeaponSelectScreen extends Screen {

    private static final int COLS = 4;
    private static final int CELL = 60;
    private static final int CELL_GAP = 6;
    private static final int HEADER_H = 34;
    private static final int VISIBLE_ROWS = 3;
    private static final int LOCK_OVERLAY = 0xB0101010;

    private final LoadoutScreen parent;
    private final Screen backScreen;
    private final Loadout working;
    private final LoadoutSlot slot;
    private final WeaponCategory category;
    private final LoadoutRegistry registry;
    private final List<LoadoutItem> options = new ArrayList<>();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int gridX;
    private int gridY;
    private int viewportH;
    private final long openedAtMs = System.currentTimeMillis();

    /** 当前滚动偏移（以行为单位）。 */
    private int scrollRow;

    /** 待确认购买的武器（非空时显示确认弹层）。 */
    private LoadoutItem pendingPurchase;
    /** 待选择/改装的已解锁枪械（非空时显示动作弹层）。 */
    private LoadoutItem pendingAction;
    /** 最近一次已展示过的购买反馈时间戳，用于只提示一次。 */
    private long shownFeedbackTs;
    /** 界面内提示文本与颜色（来自购买反馈）。 */
    private String message;
    private int messageColor = FlatTheme.TEXT_HEADER;
    private long messageUntilMs;

    /**
     * @param parent     配装主界面（选中武器后返回到此）
     * @param backScreen 「返回」/Esc 时回到的界面（分类选择界面或配装主界面）
     * @param slot       目标槽位
     * @param category   细分武器分类；为 {@code null} 表示展示该槽位全部分类
     */
    public WeaponSelectScreen(LoadoutScreen parent, Screen backScreen, Loadout working,
                              LoadoutSlot slot, WeaponCategory category) {
        super(Component.literal("选择武器"));
        this.parent = parent;
        this.backScreen = backScreen;
        this.working = working;
        this.slot = slot;
        this.category = category;
        this.registry = ClientCatalog.registry();
    }

    private int totalRows() {
        return Math.max(1, (options.size() + COLS - 1) / COLS);
    }

    private int visibleRows() {
        return Math.min(totalRows(), VISIBLE_ROWS);
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - VISIBLE_ROWS);
    }

    @Override
    protected void init() {
        options.clear();
        if (category != null) {
            options.addAll(registry.availableItemsInCategory(category, working.classType()));
        } else {
            options.addAll(registry.availableItems(slot, working.classType()));
        }
        // 避免打开界面时展示上一次的陈旧购买反馈
        shownFeedbackTs = ClientBuyFeedback.timestampMs();
        scrollRow = Math.min(scrollRow, maxScrollRow());

        this.viewportH = visibleRows() * (CELL + CELL_GAP);
        this.panelW = COLS * CELL + (COLS - 1) * CELL_GAP + 24;
        this.panelH = HEADER_H + viewportH + 40;
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
        this.gridX = left + 12;
        this.gridY = top + HEADER_H;

        // 底部：清空该槽位 / 返回
        int footY = top + panelH - 26;
        addRenderableWidget(Button.builder(Component.literal("清空"), b -> clearAndBack())
                .bounds(left + panelW / 2 - 84, footY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(left + panelW / 2 + 4, footY, 80, 20).build());
    }

    private void clearAndBack() {
        working.setSlot(slot, null);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pendingPurchase == null && pendingAction == null && maxScrollRow() > 0) {
            int next = scrollRow - (int) Math.signum(delta);
            scrollRow = Math.max(0, Math.min(next, maxScrollRow()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isLocked(LoadoutItem item) {
        return !item.isDefault() && !ClientUnlocks.isUnlocked(item.key());
    }

    private int cellX(int index) {
        return gridX + (index % COLS) * (CELL + CELL_GAP);
    }

    private int cellY(int index) {
        int row = index / COLS;
        return gridY + (row - scrollRow) * (CELL + CELL_GAP);
    }

    /** 该单元当前是否落在可视视口内（用于点击命中与渲染）。 */
    private boolean isCellVisible(int index) {
        int cy = cellY(index);
        return cy >= gridY - 1 && cy + CELL <= gridY + viewportH + 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingAction != null) {
            // 动作弹层激活：改装 / 选择 / 取消
            int[] modify = actionModifyRect();
            int[] choose = actionChooseRect();
            int[] cancel = actionCancelRect();
            if (inRect(mouseX, mouseY, modify)) {
                LoadoutItem item = pendingAction;
                pendingAction = null;
                openModify(item);
                return true;
            }
            if (inRect(mouseX, mouseY, choose)) {
                LoadoutItem item = pendingAction;
                pendingAction = null;
                chooseAndBack(item);
                return true;
            }
            if (inRect(mouseX, mouseY, cancel)) {
                pendingAction = null;
                return true;
            }
            return true; // 弹层期间吞掉其它点击
        }
        if (button == 0 && pendingPurchase != null) {
            // 确认弹层激活：仅处理确认 / 取消区域
            int[] confirm = confirmButtonRect();
            int[] cancel = cancelButtonRect();
            if (inRect(mouseX, mouseY, confirm)) {
                doPurchase(pendingPurchase);
                pendingPurchase = null;
                return true;
            }
            if (inRect(mouseX, mouseY, cancel)) {
                pendingPurchase = null;
                return true;
            }
            return true; // 弹层期间吞掉其它点击
        }
        if (button == 0) {
            for (int i = 0; i < options.size(); i++) {
                if (!isCellVisible(i)) {
                    continue;
                }
                int cx = cellX(i);
                int cy = cellY(i);
                if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                    onPick(options.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onPick(LoadoutItem item) {
        if (isLocked(item)) {
            // 未解锁：弹出二次确认（此处显示价格，便于玩家决定）
            pendingPurchase = item;
            return;
        }
        // 已解锁：若为可改装的 TaCZ 枪械，先弹出“改装/选择”动作；否则直接选中返回。
        if (TaczBridge.isAvailable() && TaczBridge.isGun(ClientCatalog.iconFor(item))) {
            pendingAction = item;
            return;
        }
        working.setSlot(slot, item.key());
        Minecraft.getInstance().setScreen(parent);
    }

    private void chooseAndBack(LoadoutItem item) {
        working.setSlot(slot, item.key());
        Minecraft.getInstance().setScreen(parent);
    }

    private void openModify(LoadoutItem item) {
        working.setSlot(slot, item.key());
        Minecraft.getInstance().setScreen(new GunModScreen(this, item.key(), item.displayName()));
    }

    private void doPurchase(LoadoutItem item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // 服务端扣款成功后会回传 BuyResultPacket 与新的解锁集合，界面随之刷新
            mc.player.connection.sendCommand("arcade buy " + item.key());
        }
    }

    private boolean inRect(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private int[] confirmButtonRect() {
        int w = 70;
        int h = 20;
        int cx = left + panelW / 2;
        int y = top + panelH / 2 + 8;
        return new int[]{cx - w - 4, y, w, h};
    }

    private int[] cancelButtonRect() {
        int w = 70;
        int h = 20;
        int cx = left + panelW / 2;
        int y = top + panelH / 2 + 8;
        return new int[]{cx + 4, y, w, h};
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(backScreen);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        pollFeedback();
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());

        gg.drawCenteredString(font, headerLabel(), left + panelW / 2, top + 10, FlatTheme.TEXT_HEADER);

        String selectedKey = working.slotItemKey(slot).orElse(null);
        LoadoutItem hovered = null;

        // 裁剪到网格视口，避免滚动时溢出面板
        gg.enableScissor(gridX, gridY, gridX + (COLS * CELL + (COLS - 1) * CELL_GAP), gridY + viewportH);
        for (int i = 0; i < options.size(); i++) {
            if (!isCellVisible(i)) {
                continue;
            }
            LoadoutItem item = options.get(i);
            int cx = cellX(i);
            int cy = cellY(i);
            boolean locked = isLocked(item);
            boolean selected = item.key().equals(selectedKey);

            // 单元底纹（选中高亮）
            FlatTheme.row(gg, cx, cy, CELL, CELL, selected);

            // 物品图标居中
            ItemStack icon = ClientCatalog.iconFor(item);
            int iconX = cx + (CELL - 16) / 2;
            int iconY = cy + 8;
            if (!icon.isEmpty()) {
                gg.renderItem(icon, iconX, iconY);
            }

            // 武器名（截断）
            String name = trim(item.displayName(), CELL - 6);
            int nameColor = locked ? FlatTheme.TEXT_DIM : FlatTheme.TEXT;
            gg.drawCenteredString(font, name, cx + CELL / 2, cy + CELL - 24, nameColor);

            if (locked) {
                // 灰色蒙版 + 橙字「未解锁」（危险/警示色统一用橙，不用纯红）
                gg.fill(cx, cy, cx + CELL, cy + CELL, LOCK_OVERLAY);
                gg.drawCenteredString(font, "未解锁", cx + CELL / 2, cy + CELL / 2 - 4, FlatTheme.DANGER);
            }

            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                hovered = item;
            }
        }
        gg.disableScissor();

        // 滚动条（仅在内容超出视口时显示）
        renderScrollbar(gg);

        // 界面内提示（购买反馈）
        if (message != null && System.currentTimeMillis() < messageUntilMs) {
            gg.drawCenteredString(font, message, left + panelW / 2, top + panelH - 40, messageColor);
        }

        super.render(gg, mouseX, mouseY, partialTick);

        // 二次确认弹层
        if (pendingPurchase != null) {
            renderConfirm(gg, mouseX, mouseY);
        } else if (pendingAction != null) {
            renderActionDialog(gg, mouseX, mouseY);
        } else if (hovered != null) {
            gg.renderTooltip(font, Component.literal(hovered.displayName()), mouseX, mouseY);
        }
    }

    /** 检查是否有新的购买反馈需要展示。 */
    private void pollFeedback() {
        long ts = ClientBuyFeedback.timestampMs();
        if (ts == 0 || ts == shownFeedbackTs) {
            return;
        }
        shownFeedbackTs = ts;
        BuyOutcome outcome = ClientBuyFeedback.outcome();
        switch (outcome) {
            case SUCCESS -> setMessage("购买成功", FlatTheme.SUCCESS);
            case ALREADY_OWNED -> setMessage("已拥有", FlatTheme.SUCCESS);
            case INSUFFICIENT -> setMessage("余额不足", FlatTheme.DANGER);
            case UNAVAILABLE -> setMessage("当前无法购买", FlatTheme.DANGER);
            default -> setMessage("购买失败", FlatTheme.DANGER);
        }
    }

    private void setMessage(String text, int color) {
        this.message = text;
        this.messageColor = color;
        this.messageUntilMs = System.currentTimeMillis() + 2500;
    }

    private void renderActionDialog(GuiGraphics gg, int mouseX, int mouseY) {
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);
        gg.fill(0, 0, this.width, this.height, FlatTheme.MODAL_OVERLAY);

        int boxW = Math.min(panelW - 20, 220);
        int boxH = 86;
        int bx = left + (panelW - boxW) / 2;
        int by = top + (panelH - boxH) / 2;
        FlatTheme.panel(gg, bx, by, boxW, boxH);

        gg.drawCenteredString(font, pendingAction.displayName(), left + panelW / 2, by + 10, FlatTheme.TEXT_HEADER);
        gg.drawCenteredString(font, "§7选择此武器，或进入改装", left + panelW / 2, by + 24, FlatTheme.TEXT_DIM);

        drawTextButton(gg, actionModifyRect(), "§e改装", mouseX, mouseY);
        drawTextButton(gg, actionChooseRect(), "§a选择", mouseX, mouseY);
        drawTextButton(gg, actionCancelRect(), "取消", mouseX, mouseY);
        gg.pose().popPose();
    }

    private int[] actionModifyRect() {
        int w = 60;
        int h = 20;
        int cx = left + panelW / 2;
        int y = top + panelH / 2 + 6;
        return new int[]{cx - w - w / 2 - 8, y, w, h};
    }

    private int[] actionChooseRect() {
        int w = 60;
        int h = 20;
        int cx = left + panelW / 2;
        int y = top + panelH / 2 + 6;
        return new int[]{cx - w / 2, y, w, h};
    }

    private int[] actionCancelRect() {
        int w = 60;
        int h = 20;
        int cx = left + panelW / 2;
        int y = top + panelH / 2 + 6;
        return new int[]{cx + w / 2 + 8, y, w, h};
    }

    private void renderConfirm(GuiGraphics gg, int mouseX, int mouseY) {
        // 抬升到更高的 Z 层，确保弹层完全压住下层网格的文字/图标，避免穿透重合
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);

        // 整屏暗化（模态遮罩，深色系而非纯黑）
        gg.fill(0, 0, this.width, this.height, FlatTheme.MODAL_OVERLAY);

        int boxW = Math.min(panelW - 20, 200);
        int boxH = 80;
        int bx = left + (panelW - boxW) / 2;
        int by = top + (panelH - boxH) / 2;
        FlatTheme.panel(gg, bx, by, boxW, boxH);

        gg.drawCenteredString(font, "购买确认", left + panelW / 2, by + 8, FlatTheme.TEXT_HEADER);
        gg.drawCenteredString(font, pendingPurchase.displayName(), left + panelW / 2, by + 22, FlatTheme.TEXT);
        gg.drawCenteredString(font, "花费 " + pendingPurchase.price(), left + panelW / 2, by + 34, FlatTheme.DANGER);

        int[] confirm = confirmButtonRect();
        int[] cancel = cancelButtonRect();
        drawTextButton(gg, confirm, "§a确认", mouseX, mouseY);
        drawTextButton(gg, cancel, "取消", mouseX, mouseY);

        gg.pose().popPose();
    }

    private void drawTextButton(GuiGraphics gg, int[] r, String label, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, r);
        gg.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE);
        gg.fill(r[0], r[1], r[0] + r[2], r[1] + 1, hover ? FlatTheme.ACCENT : FlatTheme.BORDER);
        gg.drawCenteredString(font, label, r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, FlatTheme.TEXT);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i) + ellipsis) > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + ellipsis;
    }

    private String headerLabel() {
        return category != null ? category.displayName() : slotLabel(slot);
    }

    /** 在面板右侧绘制滚动条（内容超出视口时）。 */
    private void renderScrollbar(GuiGraphics gg) {
        if (maxScrollRow() <= 0) {
            return;
        }
        int trackX = left + panelW - 8;
        int trackY = gridY;
        int trackH = viewportH;
        gg.fill(trackX, trackY, trackX + 3, trackY + trackH, FlatTheme.SURFACE_DISABLED);

        int total = totalRows();
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / total);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * scrollRow / maxScrollRow());
        gg.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, FlatTheme.ACCENT);
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
