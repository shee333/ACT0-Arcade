package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p>视觉/动效层已对照 {@code WeaponSelectScreen} 同批接入 {@link MenuChrome}/{@link MenuTween}：
 * 左列槽位选中改为 {@link MenuTween.Anim} 驱动的滑动指示器（lerp + {@link MenuTween#pulse(float)}
 * 纵向拉伸，镜像 {@code CreateRoomScreen#drawIndicator}）；右列配件行叠加开场级联与
 * {@link MenuChrome#pressScale} 按压回弹；购买确认弹层改用 {@link MenuChrome#drawPrimaryButton}/
 * {@link MenuChrome#drawGhostButton}；滚动条拇指改为金色强调。业务逻辑（安装/卸下/购买/解锁判定）
 * 未发生任何改动。
 */
public final class GunModScreen extends Screen {

    private static final String REMOVE_SENTINEL = "\u0000remove";

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
    private final long openedAtMs = System.currentTimeMillis();

    // ============================================================
    // 动效状态（MenuChrome / MenuTween 驱动）
    // ============================================================

    /** 左列槽位选中指示器：旧/新槽位行 Y，配合 {@link #slotIndicatorAnim} 做 outCubic lerp。 */
    private int slotIndicatorFromY;
    private int slotIndicatorToY;
    private final MenuTween.Anim slotIndicatorAnim = new MenuTween.Anim(220L, 0L);

    /** 右列各行（含"卸下当前配件"固定行）的按压回弹，按 key 懒加载，220ms outBack。 */
    private final Map<String, MenuTween.Anim> rowPressAnims = new HashMap<>();

    /** 购买确认弹层"确认"/"取消"按钮的按压回弹。 */
    private final MenuTween.Anim confirmPressAnim = new MenuTween.Anim(220L, 0L);
    private final MenuTween.Anim cancelPressAnim = new MenuTween.Anim(220L, 0L);

    /** 右列行开场级联：每行相对 {@link #openedAtMs} 的错峰延迟（ms/行）与单行时长。 */
    private static final long ROW_CASCADE_STAGGER_MS = 30L;
    private static final long ROW_CASCADE_DURATION_MS = 200L;
    /** 级联期间行内容的最大上移像素（与 FlatTheme 面板 3px 上移同量级但略强调）。 */
    private static final int ROW_CASCADE_OFFSET_PX = 6;

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

    private int slotIndexOf(String slotName) {
        if (data == null || slotName == null) {
            return -1;
        }
        for (int i = 0; i < data.slots.size(); i++) {
            if (slotName.equals(data.slots.get(i).slot)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, panelW, panelH, fade.alpha());
        gg.drawCenteredString(font, "改装 · " + trim(weaponName, panelW - 40),
                left + panelW / 2, top + 10, FlatTheme.TEXT_HEADER);

        slotRects.clear();
        slotKeys.clear();
        attRects.clear();
        attKeys.clear();

        int contentY = top + 28;
        if (data == null || data.slots.isEmpty()) {
            gg.drawCenteredString(font, "§7该武器暂无可用配件槽", left + panelW / 2, top + panelH / 2 - 4, FlatTheme.TEXT_DIM);
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        // 左列：槽位。三段式绘制——Pass1 基础背景 + 命中区注册，Pass2 滑动选中指示器（叠加在
        // 背景之上、文字之下），Pass3 文字与已安装标记（叠加在指示器之上，保持可读性）。
        int slotColX = left + 10;
        int slotColW = 86;
        int rowH = 18;
        int rowGap = 2;
        int sy = contentY;
        for (ModificationDto.SlotDto s : data.slots) {
            FlatTheme.row(gg, slotColX, sy, slotColW, rowH, false);
            slotRects.add(new int[]{slotColX, sy, slotColW, rowH});
            slotKeys.add(s.slot);
            sy += rowH + rowGap;
        }

        drawSlotIndicator(gg, slotColX, slotColW, rowH, rowGap, contentY, now);

        sy = contentY;
        for (ModificationDto.SlotDto s : data.slots) {
            AttachmentSlotType type = AttachmentSlotType.byName(s.slot);
            String label = type != null ? type.displayName() : s.slot;
            boolean selected = s.slot.equals(selectedSlot);
            boolean installed = s.installedKey != null && !s.installedKey.isEmpty();
            gg.drawString(font, (selected ? "§f" : "§7") + label, slotColX + 5, sy + 5,
                    selected ? FlatTheme.TEXT : FlatTheme.TEXT_DIM, false);
            if (installed) {
                gg.drawString(font, "●", slotColX + slotColW - 10, sy + 5, FlatTheme.SUCCESS, false);
            }
            sy += rowH + rowGap;
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
        // 卸下当前配件行（固定在列表顶部，不随滚动；级联行号固定为 0，最先到达）
        if (hasInstalled) {
            int rh = 16;
            drawRemoveRow(gg, listX, ly, listW, rh, now);
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
            gg.drawString(font, "§7无兼容配件", listX + 4, ly + 4, FlatTheme.TEXT_DIM, false);
            super.render(gg, mouseX, mouseY, partialTick);
            if (pendingBuy != null) {
                renderConfirm(gg, mouseX, mouseY, now);
            }
            return;
        }

        // 可滚动视口：从列表当前 y 到返回按钮上方
        int attRowH = 22;
        int rowGap2 = 2;
        int step = attRowH + rowGap2;
        this.listViewX = listX;
        this.listViewY = ly;
        this.listViewW = listW;
        this.listViewH = (top + panelH - 30) - ly;
        int contentH = visible.size() * step - rowGap2;
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
            int rowIndex = hasInstalled ? i + 1 : i;

            drawAttachmentRow(gg, listX, ry, listW, attRowH, att, key, installed, locked, rowIndex, now);

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
            renderConfirm(gg, mouseX, mouseY, now);
        } else if (hoverName != null) {
            gg.renderTooltip(font, Component.literal(hoverName), mouseX, mouseY);
        }
    }

    /**
     * 左列槽位的滑动选中指示器：镜像 {@code CreateRoomScreen#drawIndicator}——用
     * {@link MenuTween.Anim} 在旧/新槽位行 Y 之间做 outCubic lerp，途中叠加
     * {@link MenuTween#pulse(float)} 纵向拉伸，取代原来"仅静态高亮当前行"的做法。
     */
    private void drawSlotIndicator(GuiGraphics gg, int x, int w, int rowH, int rowGap, int contentY, long now) {
        int selIndex = slotIndexOf(selectedSlot);
        if (selIndex < 0) {
            return;
        }
        int yTop;
        float stretch;
        if (slotIndicatorAnim.isRunning() && !slotIndicatorAnim.isDone(now)) {
            float e = slotIndicatorAnim.easedT(now, MenuTween.Ease.OUT_CUBIC);
            yTop = Math.round(MenuChrome.lerp(slotIndicatorFromY, slotIndicatorToY, e));
            stretch = MenuTween.pulse(e);
        } else {
            yTop = contentY + selIndex * (rowH + rowGap);
            stretch = 1f;
        }
        int cx = x + w / 2;
        int cy = yTop + rowH / 2;
        MenuChrome.drawScaledAxis(gg, cx, cy, 1f, stretch, () -> {
            gg.fill(x, yTop, x + w, yTop + rowH, FlatTheme.SURFACE_HOVER);
            gg.fill(x, yTop, x + w, yTop + 1, FlatTheme.ACCENT);
        });
    }

    /** 右列行开场级联的 easedT 采样：以 {@link #openedAtMs} 为基线，按行号错峰。 */
    private float rowCascadeEase(int rowIndex, long now) {
        long delay = rowIndex * ROW_CASCADE_STAGGER_MS;
        long age = now - openedAtMs - delay;
        if (age <= 0L) {
            return 0f;
        }
        float t = Math.min(1f, age / (float) ROW_CASCADE_DURATION_MS);
        return FlatTheme.easeOut(t);
    }

    /** 与 {@link FlatTheme#row} 效果一致，但支持级联淡入的 alpha 调制（FlatTheme.row 本身不接受 alpha）。 */
    private void drawAnimatedRow(GuiGraphics gg, int x, int y, int w, int h, boolean highlight, float alpha) {
        gg.fill(x, y, x + w, y + h, FlatTheme.withAlpha(highlight ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE, alpha));
        gg.fill(x, y, x + w, y + 1, FlatTheme.withAlpha(highlight ? FlatTheme.ACCENT : FlatTheme.DIVIDER, alpha));
    }

    private MenuTween.Anim rowPressAnim(String key) {
        return rowPressAnims.computeIfAbsent(key, k -> new MenuTween.Anim(220L, 0L));
    }

    /** "卸下当前配件"固定行：级联行号恒为 0，叠加按压回弹。 */
    private void drawRemoveRow(GuiGraphics gg, int x, int y, int w, int h, long now) {
        float e = rowCascadeEase(0, now);
        int yOff = Math.round(ROW_CASCADE_OFFSET_PX * (1f - e));
        int drawY = y - yOff;
        float scale = MenuChrome.pressScale(rowPressAnim(REMOVE_SENTINEL), now);
        int cx = x + w / 2;
        int cy = drawY + h / 2;
        MenuChrome.drawScaled(gg, cx, cy, scale, () -> {
            drawAnimatedRow(gg, x, drawY, w, h, false, e);
            gg.drawString(font, "卸下当前配件", x + 4, drawY + 4, FlatTheme.withAlpha(FlatTheme.DANGER, e), false);
        });
    }

    /** 兼容配件行：级联入场 + 按压回弹，未解锁遮罩使用共享 {@link MenuChrome#LOCK_OVERLAY}。 */
    private void drawAttachmentRow(GuiGraphics gg, int listX, int ry, int listW, int attRowH, ArcadeAttachment att,
                                    String key, boolean installed, boolean locked, int rowIndex, long now) {
        float e = rowCascadeEase(rowIndex, now);
        int yOff = Math.round(ROW_CASCADE_OFFSET_PX * (1f - e));
        int drawRy = ry - yOff;
        float pressScale = MenuChrome.pressScale(rowPressAnim(key), now);
        int cx = listX + listW / 2;
        int cy = drawRy + attRowH / 2;
        MenuChrome.drawScaled(gg, cx, cy, pressScale, () -> {
            drawAnimatedRow(gg, listX, drawRy, listW, attRowH, installed, e);
            ItemStack icon = LoadoutApplier.fromSnbt(att.itemSnbt());
            if (!icon.isEmpty()) {
                gg.renderItem(icon, listX + 3, drawRy + 3);
            }
            String name = trim(att.displayName(), listW - 60);
            gg.drawString(font, name, listX + 24, drawRy + 3,
                    FlatTheme.withAlpha(locked ? FlatTheme.TEXT_DIM : FlatTheme.TEXT, e), false);
            String status = installed ? "已装备" : (locked ? String.valueOf(att.price()) : "已解锁");
            gg.drawString(font, status, listX + 24, drawRy + 12,
                    FlatTheme.withAlpha(installed ? FlatTheme.SUCCESS : (locked ? FlatTheme.DANGER : FlatTheme.TEXT_DIM), e), false);

            if (locked) {
                gg.fill(listX, drawRy, listX + listW, drawRy + attRowH, MenuChrome.LOCK_OVERLAY);
                gg.drawString(font, "未解锁", listX + listW - 38, drawRy + 8, FlatTheme.withAlpha(FlatTheme.DANGER, e), false);
            }
        });
    }

    /** 在配件列表右侧绘制滚动条（内容超出视口时）；拇指改为金色强调，滚动功能不变。 */
    private void renderListScrollbar(GuiGraphics gg) {
        if (attMaxScroll <= 0 || listViewH <= 0) {
            return;
        }
        int trackX = listViewX + listViewW - 3;
        int trackY = listViewY;
        int trackH = listViewH;
        gg.fill(trackX, trackY, trackX + 3, trackY + trackH, FlatTheme.SURFACE_DISABLED);
        int contentH = attMaxScroll + listViewH;
        int thumbH = Math.max(12, trackH * listViewH / contentH);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * attScroll / attMaxScroll);
        gg.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, MenuChrome.GOLD);
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
        long now = MenuTween.now();
        if (button == 0 && pendingBuy != null) {
            int[] confirm = confirmButtonRect();
            int[] cancel = cancelButtonRect();
            if (inRect(mouseX, mouseY, confirm)) {
                confirmPressAnim.start(now);
                ArcadeNetwork.CHANNEL.sendToServer(new BuyAttachmentPacket(weaponKey, pendingBuy));
                pendingBuy = null;
                playClick();
                return true;
            }
            if (inRect(mouseX, mouseY, cancel)) {
                cancelPressAnim.start(now);
                pendingBuy = null;
                playClick();
                return true;
            }
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < slotRects.size(); i++) {
                if (inRect(mouseX, mouseY, slotRects.get(i))) {
                    if (!slotKeys.get(i).equals(selectedSlot)) {
                        int oldIndex = slotKeys.indexOf(selectedSlot);
                        slotIndicatorFromY = oldIndex >= 0 ? slotRects.get(oldIndex)[1] : slotRects.get(i)[1];
                        slotIndicatorToY = slotRects.get(i)[1];
                        slotIndicatorAnim.start(now);
                        selectedSlot = slotKeys.get(i);
                        attScroll = 0;
                        playClick();
                    }
                    return true;
                }
            }
            for (int i = 0; i < attRects.size(); i++) {
                if (inRect(mouseX, mouseY, attRects.get(i))) {
                    rowPressAnim(attKeys.get(i)).start(now);
                    playClick();
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

    /** 购买确认弹层：确认/取消改用 {@link MenuChrome#drawPrimaryButton}/{@link MenuChrome#drawGhostButton}。 */
    private void renderConfirm(GuiGraphics gg, int mouseX, int mouseY, long now) {
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);
        gg.fill(0, 0, this.width, this.height, FlatTheme.MODAL_OVERLAY);

        ArcadeAttachment att = ClientAttachmentCatalog.registry().find(pendingBuy).orElse(null);
        String name = att != null ? att.displayName() : pendingBuy;
        int price = att != null ? att.price() : 0;

        int boxW = 200;
        int boxH = 80;
        int bx = left + (panelW - boxW) / 2;
        int by = top + (panelH - boxH) / 2;
        FlatTheme.panel(gg, bx, by, boxW, boxH);

        gg.drawCenteredString(font, "购买确认", left + panelW / 2, by + 8, FlatTheme.TEXT_HEADER);
        gg.drawCenteredString(font, name, left + panelW / 2, by + 22, FlatTheme.TEXT);
        gg.drawCenteredString(font, "花费 " + price, left + panelW / 2, by + 34, FlatTheme.DANGER);

        int[] confirm = confirmButtonRect();
        int[] cancel = cancelButtonRect();
        boolean confirmHovered = inRect(mouseX, mouseY, confirm);
        boolean cancelHovered = inRect(mouseX, mouseY, cancel);

        float confirmScale = MenuChrome.pressScale(confirmPressAnim, now);
        MenuChrome.drawPrimaryButton(gg, font, confirm[0], confirm[1], confirm[2], confirm[3], "确认",
                confirmHovered, true, false, 0f, 0f, confirmScale);

        float cancelScale = MenuChrome.pressScale(cancelPressAnim, now);
        MenuChrome.drawScaled(gg, cancel[0] + cancel[2] / 2, cancel[1] + cancel[3] / 2, cancelScale, () ->
                MenuChrome.drawGhostButton(gg, font, cancel[0], cancel[1], cancel[2], cancel[3], "取消", cancelHovered, true, 1f));

        gg.pose().popPose();
    }

    /** 手动命中检测取代原版 Button 后的点击音效反馈。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
