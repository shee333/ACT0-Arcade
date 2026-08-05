package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.shee33.act0.arcade.client.ClientRoomList;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.RequestRoomListPacket;
import org.shee33.act0.arcade.network.RoomDto;

import java.util.List;

/**
 * FlatTheme 战地 2042 风格"游戏浏览器"界面（客户端）：浏览当前可加入的房间，一键加入/离开；玩家可创建房间。
 *
 * <p>房间数据来自 {@link ClientRoomList}（由 {@code SyncRoomListPacket} 下发），界面打开后每 2 秒
 * 自动向服务端请求一次最新列表。列表区支持滚轮滚动，避免房间过多撑大界面。
 *
 * <p>视觉/动效分层与 {@code CreateRoomScreen} 一致：面板外壳、标题栏、分割线、卡片、进度条、
 * 状态色继续使用 {@link FlatTheme}（HUD 层冷灰蓝语言）；三个底部按钮改用 {@link MenuChrome} 的
 * 金色强调语言（幽灵按钮 + 主按钮），开场级联/点击回弹改由 {@link RoomBrowserAnimator} 持有的
 * {@link MenuTween.Anim} 驱动，时间源 {@link MenuTween#now()}。
 *
 * <p><b>关于唯一滑动指示器（规格 §4.1 的类比）</b>：本界面<b>刻意不实现</b>该动效类别。
 * {@code CreateRoomScreen} 的模式列表有一个跨帧持续存在的"当前选中模式"，因此值得为它做
 * 一个在列表内滑动的高亮实体。而本界面是一个纯滚动、逐行独立操作的房间列表——每行的
 * 加入/离开按钮代表该行自己的即时动作，不存在"当前选中的房间"这个持久概念（多个房间
 * 可以同时是"你在里面"的），也就没有一个合理的单一实体可以在行之间滑动表示选中。
 * 因此本类只做悬停高亮（逐行 {@link FlatTheme#card}/{@link #drawFadedCard} 静态双态）+
 * 开场级联 + 点击回弹，不引入滑动指示器类别。
 */
public final class RoomBrowserScreen extends Screen {

    private static final int W = 320;
    private static final int H = 200;
    private static final int ROW_H = 36;
    private static final int VISIBLE_ROWS = 4;
    private static final int ACTION_W = 52;
    private static final int REFRESH_INTERVAL = 40; // ticks (2s)

    // 按压回弹数组索引：前 3 位固定给底部按钮（对应 RoomBrowserAnimator.STATIC_PRESS_COUNT），
    // 其余 VISIBLE_ROWS 位按"可见槽位"（index - scrollRow）分配给各行的加入/离开动作按钮。
    private static final int P_REFRESH = 0;
    private static final int P_CREATE = 1;
    private static final int P_CLOSE = 2;
    private static final int P_ROW_BASE = RoomBrowserAnimator.STATIC_PRESS_COUNT;

    private static final int NAV_NONE = 0;
    private static final int NAV_CREATE = 1;
    private static final int NAV_CLOSE = 2;

    private int left;
    private int top;
    private int listX;
    private int listY;
    private int listW;
    private int viewportH;
    private int scrollRow;
    private int refreshTimer;
    private final long openedAtMs = System.currentTimeMillis();

    // 底部按钮布局锚点（供 render() 与 mouseClicked() 共用，避免坐标在两处重复推导出偏差）。
    private int footY;
    private int refreshX;
    private int createX;
    private int closeX;

    private RoomBrowserAnimator anim;
    /** 是否已完整 init() 过一次：resize/GUI Scale 变更会重跑 init()，此标记确保开场级联
     *  动画实例只在真正首次打开时构造一次，避免每次 resize 都重播开场动效。 */
    private boolean initialized;

    // 次级操作（创建房间/关闭）：按压回弹播完才真正触发导航，回弹才有机会被玩家看到，
    // 与 CreateRoomScreen 的 onSettingsClicked/onBackClicked + pendingNav 模式完全一致。
    private int pendingNav = NAV_NONE;

    // 房间行动作（加入/离开/快速加入战场/突破）同理延迟到按压回弹播完再执行；
    // 用槽位而非全局下标记录，因为按压动画实例是按"可见槽位"分配的。
    private RoomDto pendingRoomAction;
    private int pendingRoomSlot = -1;

    public RoomBrowserScreen() {
        super(Component.literal("游戏浏览器"));
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.listX = left + 10;
        this.listY = top + 44;
        this.listW = W - 20;
        this.viewportH = VISIBLE_ROWS * ROW_H;

        this.footY = top + H - 26;
        this.refreshX = left + 10;
        this.createX = refreshX + 56;
        this.closeX = left + W - 58;

        if (!initialized) {
            initialized = true;
            anim = new RoomBrowserAnimator(openedAtMs, VISIBLE_ROWS);
        }

        requestRefresh();
    }

    /** 收到新房间列表后调用：夹紧滚动位置。 */
    public void onRoomsUpdated() {
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow()));
    }

    private void openCreate() {
        if (minecraft != null) {
            minecraft.setScreen(new CreateRoomScreen(this));
        }
    }

    private void requestRefresh() {
        ArcadeNetwork.CHANNEL.sendToServer(new RequestRoomListPacket());
    }

    @Override
    public void tick() {
        if (++refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0;
            requestRefresh();
        }
    }

    private List<RoomDto> rooms() {
        return ClientRoomList.rooms();
    }

    private int totalRows() {
        return rooms().size();
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - VISIBLE_ROWS);
    }

    private int rowY(int index) {
        return listY + (index - scrollRow) * ROW_H;
    }

    private boolean isRowVisible(int index) {
        int y = rowY(index);
        return index >= scrollRow && y + ROW_H - 2 <= listY + viewportH && y >= listY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + viewportH) {
            int max = maxScrollRow();
            if (max > 0) {
                scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        List<RoomDto> list = rooms();
        for (int i = 0; i < list.size(); i++) {
            if (!isRowVisible(i)) {
                continue;
            }
            RoomDto room = list.get(i);
            int ax = listX + listW - ACTION_W - 4;
            int ay = rowY(i) + 4;
            int ah = ROW_H - 8;
            boolean actionable = room.youAreMember() || !room.isFull();
            if (actionable && MenuChrome.inRect(mouseX, mouseY, ax, ay, ACTION_W, ah)) {
                onRoomActionClicked(room, i - scrollRow);
                return true;
            }
        }

        if (pendingNav == NAV_NONE) {
            if (MenuChrome.inRect(mouseX, mouseY, refreshX, footY, 50, 18)) {
                onRefreshClicked();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, createX, footY, 80, 18)) {
                onCreateFooterClicked();
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, closeX, footY, 48, 18)) {
                onCloseFooterClicked();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onRoomActionClicked(RoomDto room, int slot) {
        if (pendingRoomAction != null) {
            return;
        }
        anim.press[P_ROW_BASE + slot].start(MenuTween.now());
        pendingRoomAction = room;
        pendingRoomSlot = slot;
        playClick();
    }

    /** 刷新不涉及导航/界面切换，回弹只是视觉反馈，动作可以立即发出，无需 pendingNav 延迟。 */
    private void onRefreshClicked() {
        anim.press[P_REFRESH].start(MenuTween.now());
        requestRefresh();
        playClick();
    }

    private void onCreateFooterClicked() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        anim.press[P_CREATE].start(MenuTween.now());
        pendingNav = NAV_CREATE;
        playClick();
    }

    private void onCloseFooterClicked() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        anim.press[P_CLOSE].start(MenuTween.now());
        pendingNav = NAV_CLOSE;
        playClick();
    }

    private void doRoomAction(RoomDto room) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (room.roomId().startsWith("bf@")) {
            minecraft.player.connection.sendCommand("battlefield quickjoin \"" + room.roomId() + "\"");
            onClose();
            return;
        }
        if (room.roomId().startsWith("bt@")) {
            minecraft.player.connection.sendCommand("breakthrough quickjoin \"" + room.roomId() + "\"");
            onClose();
            return;
        }
        if (room.youAreMember()) {
            minecraft.player.connection.sendCommand("arcade room leave");
        } else {
            minecraft.player.connection.sendCommand("arcade room join " + room.roomId());
            minecraft.setScreen(new RoomLobbyScreen());
        }
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈，与 CreateRoomScreen.playClick() 同款。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingNav != NAV_NONE) {
            int pressIdx = pendingNav == NAV_CREATE ? P_CREATE : P_CLOSE;
            if (anim.press[pressIdx].isDone(now)) {
                int nav = pendingNav;
                pendingNav = NAV_NONE;
                if (nav == NAV_CREATE) {
                    openCreate();
                } else {
                    onClose();
                }
                return;
            }
        }
        if (pendingRoomAction != null && anim.press[P_ROW_BASE + pendingRoomSlot].isDone(now)) {
            RoomDto action = pendingRoomAction;
            pendingRoomAction = null;
            pendingRoomSlot = -1;
            doRoomAction(action);
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        int panelTop = top + fade.offsetY();
        FlatTheme.panel(gg, left, panelTop, W, H, fade.alpha());
        FlatTheme.titleBar(gg, left, panelTop, W, 24);

        // 标题栏最先出现（0ms 延迟），随后列表行、底部按钮依次错峰到达。
        float titleE = anim.titleBar.easedT(now, MenuTween.Ease.OUT_CUBIC);
        float titleAlpha = fade.alpha() * titleE;
        gg.drawCenteredString(font, "游戏浏览器", left + W / 2, panelTop + 9, FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, titleAlpha));
        gg.drawString(font, "§7房间与进行中的对局", left + 12, panelTop + 29, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, titleAlpha), false);

        List<RoomDto> list = rooms();
        if (list.isEmpty()) {
            gg.drawCenteredString(font, "§7暂无房间，点击下方创建",
                    left + W / 2, listY + viewportH / 2 - 4, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, fade.alpha()));
        } else {
            gg.enableScissor(listX, listY, listX + listW, listY + viewportH);
            for (int i = 0; i < list.size(); i++) {
                if (!isRowVisible(i)) {
                    continue;
                }
                renderRoomRow(gg, list.get(i), rowY(i), mouseX, mouseY, i - scrollRow, now, fade.alpha());
            }
            gg.disableScissor();
            renderScrollbar(gg, now, fade.alpha());
        }

        renderFooter(gg, mouseX, mouseY, now, fade.alpha());

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void renderRoomRow(GuiGraphics gg, RoomDto room, int y, int mouseX, int mouseY, int slot, long now, float panelAlpha) {
        // 逐行错峰入场：本行的 easedT 决定它自己的 alpha/位移，35ms/行的错峰节奏在
        // RoomBrowserAnimator 里配置（ROW_BASE_DELAY + slot * ROW_STAGGER_MS）。
        float rowE = anim.rows[slot].easedT(now, MenuTween.Ease.OUT_CUBIC);
        float rowAlpha = rowE * panelAlpha;
        int xOffset = Math.round(-12 * (1 - rowE));

        int ax = listX + listW - ACTION_W - 4;
        int ay = y + 8;
        int ah = ROW_H - 8;
        boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + ROW_H - 2;
        drawFadedCard(gg, listX, y, listW, ROW_H - 2, room.youAreMember() || hovered, rowAlpha);

        int tx = listX + 4 + xOffset;
        String status = room.inProgress() ? " §a[进行中 " + formatClock(room.elapsedSeconds()) + "]" : "";
        gg.drawString(font, "§f" + room.modeName() + " §8· §7" + room.targetText() + status,
                tx, y + 4, FlatTheme.withAlpha(FlatTheme.TEXT, rowAlpha), false);
        gg.drawString(font, "§8战场 §7" + room.arenaId() + "  §8房主 §7" + room.hostName(),
                tx, y + 14, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, rowAlpha), false);
        String players = room.playersText().isBlank() ? "-" : trim(room.playersText(), listW - ACTION_W - 76);
        gg.drawString(font, "§8玩家 §7" + players, tx, y + 24, FlatTheme.withAlpha(FlatTheme.TEXT_DIM, rowAlpha), false);

        String count = room.size() + "/" + room.capacity();
        int countColor = room.isFull() ? FlatTheme.DANGER : FlatTheme.SUCCESS;
        int countX = ax - font.width(count) - 8 + xOffset;
        FlatTheme.progress(gg, countX - 30, y + 25, 48, 3,
                room.capacity() <= 0 ? 0f : room.size() / (float) room.capacity(), FlatTheme.withAlpha(countColor, rowAlpha));
        gg.drawString(font, count, countX, y + 13, FlatTheme.withAlpha(countColor, rowAlpha), false);

        if (room.inProgress()) {
            FlatTheme.chip(gg, listX + listW - ACTION_W - 72 + xOffset, y + 4, 44, 10, FlatTheme.withAlpha(FlatTheme.SUCCESS, rowAlpha));
            gg.drawCenteredString(font, "进行中", listX + listW - ACTION_W - 50 + xOffset, y + 5, FlatTheme.withAlpha(FlatTheme.TEXT, rowAlpha));
        }

        // 操作按钮：加入=强调蓝，离开=警示橙，已满/已在=中性灰（禁用态）
        String label;
        int color;
        boolean buttonHovered = mouseX >= ax && mouseX <= ax + ACTION_W && mouseY >= ay && mouseY <= ay + ah;
        if ((room.roomId().startsWith("bf@") || room.roomId().startsWith("bt@")) && room.youAreMember()) {
            label = "已在";
            color = FlatTheme.TEXT_DIM;
        } else if (room.youAreMember()) {
            label = "离开";
            color = FlatTheme.DANGER;
        } else if (room.isFull()) {
            label = "已满";
            color = FlatTheme.TEXT_DIM;
        } else {
            label = "加入";
            color = FlatTheme.ACCENT;
        }
        boolean enabled = room.youAreMember() || !room.isFull();
        float pressScale = MenuChrome.pressScale(anim.press[P_ROW_BASE + slot], now);
        drawActionButton(gg, ax, ay, ACTION_W, ah, label, color, enabled && buttonHovered, rowAlpha, pressScale);
    }

    /**
     * {@link FlatTheme#card} 的等价形状，但接受一个额外的 {@code alpha} 乘子——
     * FlatTheme.card 本身不支持透明度参数，无法用于本行的开场级联淡入，因此在此
     * 就地内联同款描边+底色逻辑（非"非平凡纯逻辑"，只是绘制形状，不额外拆类/写测试）。
     */
    private void drawFadedCard(GuiGraphics gg, int x, int y, int w, int h, boolean highlight, float alpha) {
        int bg = highlight ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE;
        int border = highlight ? FlatTheme.ACCENT : FlatTheme.BORDER;
        int x2 = x + w;
        int y2 = y + h;
        gg.fill(x, y, x2, y2, FlatTheme.withAlpha(bg, alpha));
        int borderA = FlatTheme.withAlpha(border, alpha);
        gg.fill(x, y, x2, y + 1, borderA);
        gg.fill(x, y2 - 1, x2, y2, borderA);
        gg.fill(x, y, x + 1, y2, borderA);
        gg.fill(x2 - 1, y, x2, y2, borderA);
    }

    private void drawActionButton(GuiGraphics gg, int x, int y, int w, int h, String label, int color,
                                   boolean hovered, float alpha, float pressScale) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        MenuChrome.drawScaled(gg, cx, cy, pressScale, () -> {
            FlatTheme.chip(gg, x, y, w, h, FlatTheme.withAlpha(hovered ? FlatTheme.ACCENT : color, alpha));
            gg.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, FlatTheme.withAlpha(color, alpha));
        });
    }

    private void renderScrollbar(GuiGraphics gg, long now, float panelAlpha) {
        if (maxScrollRow() <= 0) {
            return;
        }
        int trackX = listX + listW - 2;
        int trackY = listY;
        int trackH = viewportH;
        // 滚动条随列表最后一行的级联进度一起淡入，避免它比自己"负责的"内容更早显得突兀。
        float listE = anim.rows.length > 0 ? anim.rows[anim.rows.length - 1].easedT(now, MenuTween.Ease.OUT_CUBIC) : 1f;
        float alpha = listE * panelAlpha;
        gg.fill(trackX, trackY, trackX + 2, trackY + trackH, FlatTheme.withAlpha(FlatTheme.SURFACE_DISABLED, alpha));

        int total = totalRows();
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / total);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * scrollRow / maxScrollRow());
        // 滚动条属于菜单层交互控件，采用 MenuChrome.GOLD 而非 FlatTheme.ACCENT，与刷新/
        // 创建/关闭按钮的金色语言保持一致——FlatTheme.ACCENT 留给内容区（阵营强调）使用。
        gg.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, FlatTheme.withAlpha(MenuChrome.GOLD, alpha));
    }

    private void renderFooter(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float footerE = anim.footer.easedT(now, MenuTween.Ease.OUT_CUBIC);
        float alpha = footerE * panelAlpha;

        boolean refreshHovered = pendingNav == NAV_NONE && MenuChrome.inRect(mouseX, mouseY, refreshX, footY, 50, 18);
        float refreshPress = MenuChrome.pressScale(anim.press[P_REFRESH], now);
        MenuChrome.drawScaled(gg, refreshX + 25, footY + 9, refreshPress, () ->
                MenuChrome.drawGhostButton(gg, font, refreshX, footY, 50, 18, "刷新", refreshHovered, true, alpha));

        // 主按钮（金色 CTA）：drawPrimaryButton 内部自带按压缩放包裹，无需外层再 drawScaled。
        boolean createHovered = pendingNav == NAV_NONE && MenuChrome.inRect(mouseX, mouseY, createX, footY, 80, 18);
        float createPress = MenuChrome.pressScale(anim.press[P_CREATE], now);
        MenuChrome.drawPrimaryButton(gg, font, createX, footY, 80, 18, "创建房间",
                createHovered, true, false, 0f, 0f, createPress);

        boolean closeHovered = pendingNav == NAV_NONE && MenuChrome.inRect(mouseX, mouseY, closeX, footY, 48, 18);
        float closePress = MenuChrome.pressScale(anim.press[P_CLOSE], now);
        MenuChrome.drawScaled(gg, closeX + 24, footY + 9, closePress, () ->
                MenuChrome.drawGhostButton(gg, font, closeX, footY, 48, 18, "关闭", closeHovered, true, alpha));
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

    private static String formatClock(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
