package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shee33.act0.arcade.client.ClientAttachmentCatalog;
import org.shee33.act0.arcade.client.ClientModification;
import org.shee33.act0.arcade.client.ClientUnlocks;
import org.shee33.act0.arcade.loadout.ArcadeAttachment;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.BuyAttachmentPacket;
import org.shee33.act0.arcade.network.ModificationDto;
import org.shee33.act0.arcade.network.ModifyActionPacket;
import org.shee33.act0.arcade.network.RequestModificationPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 枪械"改装"界面（客户端）：左列为该枪允许的配件槽位，右侧为所选槽位下与该枪兼容的配件。
 *
 * <ul>
 *   <li>已安装的配件高亮，点击可卸下；</li>
 *   <li>已解锁未安装：点击即安装；</li>
 *   <li>未解锁：灰显 + 价格，点击弹出购买确认（弹层内显示价格）。</li>
 * </ul>
 *
 * <p>兼容/已安装信息来自服务端下发的 {@link ModificationDto}（{@link ClientModification}），
 * 配件名称/价格/图标来自 {@link ClientAttachmentCatalog}，解锁状态来自 {@link ClientUnlocks}。
 */
public final class GunModScreen extends Screen {

    private static final String REMOVE_SENTINEL = "\u0000remove";
    private static final int LOCK_OVERLAY = 0xB0101010;

    private final Screen backScreen;
    private final String weaponKey;
    private final String weaponName;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private ModificationDto data;
    private String selectedSlot;

    /** 右侧配件列表的滚动偏移（像素）。 */
    private int attScroll;
    /** 上一帧配件列表可滚动的最大偏移。 */
    private int attMaxScroll;
    /** 右侧配件列表可视视口（用于滚轮命中判定）。 */
    private int listViewX;
    private int listViewY;
    private int listViewW;
    private int listViewH;

    /** 当前帧的可点击区域（槽位）。 */
    private final List<int[]> slotRects = new ArrayList<>();
    private final List<String> slotKeys = new ArrayList<>();
    /** 当前帧的可点击区域（配件，含卸下哨兵）。 */
    private final List<int[]> attRects = new ArrayList<>();
    private final List<String> attKeys = new ArrayList<>();

    /** 待确认购买的配件 key（非空时显示确认弹层）。 */
    private String pendingBuy;

    public GunModScreen(Screen backScreen, String weaponKey, String weaponName) {
        super(Component.literal("改装"));
        this.backScreen = backScreen;
        this.weaponKey = weaponKey;
        this.weaponName = weaponName != null ? weaponName : weaponKey;
    }

    /** 由网络层在收到新改装数据时回调刷新。 */
    public void onData(ModificationDto dto) {
        if (dto != null && weaponKey.equals(dto.weaponKey)) {
            this.data = dto;
            if (selectedSlot == null && !dto.slots.isEmpty()) {
                selectedSlot = dto.slots.get(0).slot;
            }
        }
    }

    @Override
    protected void init() {
        this.panelW = 300;
        this.panelH = 188;
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;

        // 进入时请求最新改装上下文
        this.data = ClientModification.current();
        if (this.data == null || !weaponKey.equals(this.data.weaponKey)) {
            this.data = null;
        } else if (selectedSlot == null && !data.slots.isEmpty()) {
            selectedSlot = data.slots.get(0).slot;
        }
        ArcadeNetwork.CHANNEL.sendToServer(new RequestModificationPacket(weaponKey));

        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(left + panelW / 2 - 40, top + panelH - 24, 80, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(backScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ModificationDto.SlotDto slotData(String slotName) {
        if (data == null || slotName == null) {
            return null;
        }
        for (ModificationDto.SlotDto s : data.slots) {
            if (slotName.equals(s.slot)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, panelW, panelH);
        gg.drawCenteredString(font, "§l改装 · " + trim(weaponName, panelW - 40),
                left + panelW / 2, top + 10, PixelTheme.ACCENT);

        slotRects.clear();
        slotKeys.clear();
        attRects.clear();
        attKeys.clear();

        int contentY = top + 28;
        if (data == null || data.slots.isEmpty()) {
            gg.drawCenteredString(font, "§7该武器暂无可用配件槽", left + panelW / 2, top + panelH / 2 - 4, PixelTheme.TEXT_DIM);
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        // 左列：槽位
        int slotColX = left + 10;
        int slotColW = 86;
        int rowH = 18;
        int sy = contentY;
        for (ModificationDto.SlotDto s : data.slots) {
            AttachmentSlotType type = AttachmentSlotType.byName(s.slot);
            String label = type != null ? type.displayName() : s.slot;
            boolean selected = s.slot.equals(selectedSlot);
            PixelTheme.row(gg, slotColX, sy, slotColW, rowH, selected);
            boolean installed = s.installedKey != null && !s.installedKey.isEmpty();
            gg.drawString(font, (selected ? "§f" : "§7") + label, slotColX + 5, sy + 5,
                    selected ? PixelTheme.TEXT : PixelTheme.TEXT_DIM, false);
            if (installed) {
                gg.drawString(font, "§a●", slotColX + slotColW - 10, sy + 5, 0xFF8AE66B, false);
            }
            slotRects.add(new int[]{slotColX, sy, slotColW, rowH});
            slotKeys.add(s.slot);
            sy += rowH + 2;
        }

        // 右侧：所选槽位的兼容配件
        int listX = slotColX + slotColW + 10;
        int listW = left + panelW - 10 - listX;
        int ly = contentY;
        ModificationDto.SlotDto sel = slotData(selectedSlot);
        if (sel == null) {
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        boolean hasInstalled = sel.installedKey != null && !sel.installedKey.isEmpty();
        // 卸下当前配件行（固定在列表顶部，不随滚动）
        if (hasInstalled) {
            int rh = 16;
            gg.fill(listX, ly, listX + listW, ly + rh, 0x40301010);
            gg.drawString(font, "§c卸下当前配件", listX + 4, ly + 4, 0xFFFF7777, false);
            attRects.add(new int[]{listX, ly, listW, rh});
            attKeys.add(REMOVE_SENTINEL);
            ly += rh + 3;
        }

        // 收集该槽位的可展示配件
        List<ArcadeAttachment> visible = new ArrayList<>();
        for (String key : sel.compatibleKeys) {
            ArcadeAttachment att = ClientAttachmentCatalog.registry().find(key).orElse(null);
            if (att != null) {
                visible.add(att);
            }
        }

        if (visible.isEmpty()) {
            gg.drawString(font, "§7无兼容配件", listX + 4, ly + 4, PixelTheme.TEXT_DIM, false);
            super.render(gg, mouseX, mouseY, partialTick);
            if (pendingBuy != null) {
                renderConfirm(gg, mouseX, mouseY);
            }
            return;
        }

        // 可滚动视口：从列表当前 y 到返回按钮上方
        int attRowH = 22;
        int rowGap = 2;
        int step = attRowH + rowGap;
        this.listViewX = listX;
        this.listViewY = ly;
        this.listViewW = listW;
        this.listViewH = (top + panelH - 30) - ly;
        int contentH = visible.size() * step - rowGap;
        this.attMaxScroll = Math.max(0, contentH - listViewH);
        this.attScroll = Math.max(0, Math.min(attScroll, attMaxScroll));

        String hoverName = null;
        gg.enableScissor(listViewX, listViewY, listViewX + listViewW, listViewY + listViewH);
        int baseY = listViewY - attScroll;
        for (int i = 0; i < visible.size(); i++) {
            ArcadeAttachment att = visible.get(i);
            String key = att.key();
            int ry = baseY + i * step;
            // 视口外跳过（仍不登记点击区）
            if (ry + attRowH <= listViewY || ry >= listViewY + listViewH) {
                continue;
            }
            boolean installed = key.equals(sel.installedKey);
            boolean locked = !att.isDefault() && !ClientUnlocks.isUnlocked(key);

            PixelTheme.row(gg, listX, ry, listW, attRowH, installed);
            ItemStack icon = LoadoutApplier.fromSnbt(att.itemSnbt());
            if (!icon.isEmpty()) {
                gg.renderItem(icon, listX + 3, ry + 3);
            }
            String name = trim(att.displayName(), listW - 60);
            gg.drawString(font, name, listX + 24, ry + 3, locked ? PixelTheme.TEXT_DIM : PixelTheme.TEXT, false);
            String status = installed ? "§a已装备" : (locked ? "§e" + att.price() : "§7已解锁");
            gg.drawString(font, status, listX + 24, ry + 12, installed ? 0xFF8AE66B : (locked ? 0xFFE6C84B : PixelTheme.TEXT_DIM), false);

            if (locked) {
                gg.fill(listX, ry, listX + listW, ry + attRowH, LOCK_OVERLAY);
                gg.drawString(font, "§c未解锁", listX + listW - 38, ry + 8, 0xFFFF5555, false);
            }

            if (mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + attRowH) {
                hoverName = att.displayName();
            }
            attRects.add(new int[]{listX, ry, listW, attRowH});
            attKeys.add(key);
        }
        gg.disableScissor();

        // 滚动条（内容超出视口时）
        renderListScrollbar(gg);

        super.render(gg, mouseX, mouseY, partialTick);

        if (pendingBuy != null) {
            renderConfirm(gg, mouseX, mouseY);
        } else if (hoverName != null) {
            gg.renderTooltip(font, Component.literal(hoverName), mouseX, mouseY);
        }
    }

    /** 在配件列表右侧绘制滚动条（内容超出视口时）。 */
    private void renderListScrollbar(GuiGraphics gg) {
        if (attMaxScroll <= 0 || listViewH <= 0) {
            return;
        }
        int trackX = listViewX + listViewW - 3;
        int trackY = listViewY;
        int trackH = listViewH;
        gg.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x40000000);
        int contentH = attMaxScroll + listViewH;
        int thumbH = Math.max(12, trackH * listViewH / contentH);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * attScroll / attMaxScroll);
        gg.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, PixelTheme.BEVEL_LIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pendingBuy == null && attMaxScroll > 0
                && mouseX >= listViewX && mouseX < listViewX + listViewW
                && mouseY >= listViewY && mouseY < listViewY + listViewH) {
            int next = attScroll - (int) Math.signum(delta) * 12;
            attScroll = Math.max(0, Math.min(next, attMaxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingBuy != null) {
            int[] confirm = confirmButtonRect();
            int[] cancel = cancelButtonRect();
            if (inRect(mouseX, mouseY, confirm)) {
                ArcadeNetwork.CHANNEL.sendToServer(new BuyAttachmentPacket(weaponKey, pendingBuy));
                pendingBuy = null;
                return true;
            }
            if (inRect(mouseX, mouseY, cancel)) {
                pendingBuy = null;
                return true;
            }
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < slotRects.size(); i++) {
                if (inRect(mouseX, mouseY, slotRects.get(i))) {
                    if (!slotKeys.get(i).equals(selectedSlot)) {
                        selectedSlot = slotKeys.get(i);
                        attScroll = 0;
                    }
                    return true;
                }
            }
            for (int i = 0; i < attRects.size(); i++) {
                if (inRect(mouseX, mouseY, attRects.get(i))) {
                    onAttachmentClicked(attKeys.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onAttachmentClicked(String key) {
        if (selectedSlot == null) {
            return;
        }
        if (REMOVE_SENTINEL.equals(key)) {
            ArcadeNetwork.CHANNEL.sendToServer(new ModifyActionPacket(weaponKey, selectedSlot, ""));
            return;
        }
        ModificationDto.SlotDto sel = slotData(selectedSlot);
        boolean installed = sel != null && key.equals(sel.installedKey);
        if (installed) {
            ArcadeNetwork.CHANNEL.sendToServer(new ModifyActionPacket(weaponKey, selectedSlot, ""));
            return;
        }
        ArcadeAttachment att = ClientAttachmentCatalog.registry().find(key).orElse(null);
        boolean locked = att != null && !att.isDefault() && !ClientUnlocks.isUnlocked(key);
        if (locked) {
            pendingBuy = key;
            return;
        }
        ArcadeNetwork.CHANNEL.sendToServer(new ModifyActionPacket(weaponKey, selectedSlot, key));
    }

    private void renderConfirm(GuiGraphics gg, int mouseX, int mouseY) {
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);
        gg.fill(0, 0, this.width, this.height, 0xCC000000);

        ArcadeAttachment att = ClientAttachmentCatalog.registry().find(pendingBuy).orElse(null);
        String name = att != null ? att.displayName() : pendingBuy;
        int price = att != null ? att.price() : 0;

        int boxW = 200;
        int boxH = 80;
        int bx = left + (panelW - boxW) / 2;
        int by = top + (panelH - boxH) / 2;
        gg.fill(bx, by, bx + boxW, by + boxH, 0xFF000000);
        PixelTheme.panel(gg, bx, by, boxW, boxH);

        gg.drawCenteredString(font, "§l购买确认", left + panelW / 2, by + 8, PixelTheme.ACCENT);
        gg.drawCenteredString(font, name, left + panelW / 2, by + 22, PixelTheme.TEXT);
        gg.drawCenteredString(font, "§e花费 " + price, left + panelW / 2, by + 34, 0xFFE6C84B);

        drawTextButton(gg, confirmButtonRect(), "§a确认", mouseX, mouseY);
        drawTextButton(gg, cancelButtonRect(), "取消", mouseX, mouseY);
        gg.pose().popPose();
    }

    private void drawTextButton(GuiGraphics gg, int[] r, String label, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, r);
        gg.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0xFF3A4A2A : 0xFF26301C);
        gg.fill(r[0], r[1], r[0] + r[2], r[1] + 1, PixelTheme.BEVEL_LIGHT);
        gg.drawCenteredString(font, label, r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, PixelTheme.TEXT);
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

    private boolean inRect(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i) + "…") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }
}
