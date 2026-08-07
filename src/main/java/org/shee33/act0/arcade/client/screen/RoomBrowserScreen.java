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

import java.util.ArrayList;
import java.util.List;

/**
 * 「加入游戏 · 街机模式 · 房间浏览器」——按《游戏浏览器动效双版本规格文档》第 1 节（共享底盘）
 * 与第 2 节（浏览器 A）完整重做。
 *
 * <p>三层分工：本类只做布局/绘制/输入；补间时长与错峰在 {@link RoomBrowserAnimator}；
 * 状态派生、筛选判据、抽屉设定键值对、摇头位移公式、快照差分在 {@link RoomBrowserModel}（纯逻辑、可单测）。
 * 视觉语言沿用本仓库既定标准：面板外壳/卡片/进度条走 {@link FlatTheme}，金色强调 {@code #ffd76a}
 * 与幽灵/主按钮/按压回弹/滚轮换字走 {@link MenuChrome}，时间源 {@link MenuTween#now()}。
 *
 * <p><b>关于"实时跳动"</b>：规格原型每 2.6s 客户端自己造一个随机人数 ±1。本仓库有真实数据源
 * （服务端 {@code SyncRoomListPacket} 每 2s 推送），因此这里不造假随机数——{@link #onRoomsUpdated()}
 * 把新快照与上一帧缓存交给 {@link RoomBrowserModel#diff} 差分，只有人数/状态真的变了的行才播
 * 方向性滚轮换字（增上滚 / 减下滚）+ 600ms 蓝色行高亮衰减。
 *
 * <p><b>关于「好友」筛选页</b>：规格第四个标签依赖 {@code friend} 字段，而本仓库没有任何好友
 * 系统可派生该标记，故按同等价值替换为「我的」（{@code youAreMember}）——理由见
 * {@link RoomBrowserModel#TAB_MINE}。
 */
public final class RoomBrowserScreen extends Screen {

    // ============================================================
    // 布局骨架（规格 §1.2 的 860×540 舞台等比缩到 MC 面板尺寸）
    // ============================================================

    private static final int W = 420;
    private static final int H = 240;
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int VISIBLE_ROWS = 5;
    private static final int DRAWER_W = 172;
    private static final int REFRESH_BTN = 16;
    private static final int REFRESH_INTERVAL = 40; // ticks (2s)

    /** 行内左内边距：常态 6px，悬停/选中缩进到 11px（规格 12↔17px 等比缩放）。 */
    private static final int ROW_PAD = 6;

    /** 列宽百分比（规格 §2.1：32% / 20% / 20% / 14% / flex）。 */
    private static final float COL_ROOM = 0.32f;
    private static final float COL_MODE = 0.20f;
    private static final float COL_MAP = 0.20f;
    private static final float COL_COUNT = 0.14f;

    private static final int NAV_NONE = 0;
    private static final int NAV_CREATE = 1;
    private static final int NAV_CLOSE = 2;
    private static final int NAV_LOBBY = 3;

    /** 列表阶段机：0=空闲，1=标签切换退场，2=刷新淡出，3=刷新扫描。 */
    private static final int LIST_IDLE = 0;
    private static final int LIST_TAB_EXIT = 1;
    private static final int LIST_REFRESH_FADE = 2;
    private static final int LIST_REFRESH_SCAN = 3;

    /** 加入转场阶段机：0=无，1=按压交接，2=压暗+进度条，3=已连接停留，4=淡出。 */
    private static final int JOIN_NONE = 0;
    private static final int JOIN_PRESSED = 1;
    private static final int JOIN_BAR = 2;
    private static final int JOIN_HOLD = 3;
    private static final int JOIN_OUT = 4;

    // ============================================================
    // 布局锚点（init/render 推导一次，mouseClicked 复用，避免两处坐标漂移）
    // ============================================================

    private int left;
    private int top;
    private int listX;
    private int listY;
    private int listW;
    private int viewportH;
    private int refreshX;
    private int refreshY;
    private int tabsY;
    private int lineY;
    private int footY;
    private int createX;
    private int closeX;
    private final int[] tabX = new int[RoomBrowserModel.TAB_LABELS.length];
    private final int[] tabW = new int[RoomBrowserModel.TAB_LABELS.length];

    private int scrollRow;
    private int refreshTimer;
    private final long openedAtMs = System.currentTimeMillis();
    private RoomBrowserAnimator anim;
    private boolean initialized;

    // ============================================================
    // 交互状态
    // ============================================================

    /** 目标标签页；退场动画期间 {@link #renderTab} 仍是旧值，保证退场画的是旧列表。 */
    private int activeTab = RoomBrowserModel.TAB_ALL;
    private int renderTab = RoomBrowserModel.TAB_ALL;
    private int lineFromX;
    private int lineFromW;
    private int lineToX;
    private int lineToW;

    private int listStage = LIST_IDLE;
    private long listStageAtMs;

    private String selectedRoomId;
    private String fadingAccentRoomId;
    private boolean drawerVisible;
    private boolean drawerClosing;
    /** 换选期间正在压暗；压暗播完才真正换内容再回亮（规格 §2.2）。 */
    private boolean drawerDimming;
    private String pendingSelectRoomId;

    private int hoveredSlot = -1;
    private boolean hoverEntering;

    private int joinStage = JOIN_NONE;
    private long joinStageAtMs;
    private String joinRoomName = "";
    private String joinRoomId;

    private int pendingNav = NAV_NONE;

    /** 上一帧的房间快照，用于差分出"哪些行的人数/状态变了"。 */
    private List<RoomBrowserModel.Snapshot> lastSnapshots = List.of();

    // 每个可见槽位的滚轮/高亮状态：只有槽位当前渲染的房间与登记的 roomId 一致时才播，
    // 避免列表滚动后把动画错播到另一间房上。
    private final String[] rollRoomId = new String[VISIBLE_ROWS];
    private final String[] countOldText = new String[VISIBLE_ROWS];
    private final int[] countDir = new int[VISIBLE_ROWS];
    private final String[] statusOldText = new String[VISIBLE_ROWS];
    private final int[] statusOldColor = new int[VISIBLE_ROWS];
    private final String[] flashRoomId = new String[VISIBLE_ROWS];

    public RoomBrowserScreen() {
        super(Component.literal("加入游戏"));
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.listX = left + PAD;
        this.listY = top + 70;
        this.listW = W - PAD * 2;
        this.viewportH = VISIBLE_ROWS * ROW_H;
        this.refreshX = left + W - PAD - REFRESH_BTN;
        this.refreshY = top + 9;
        this.tabsY = top + 36;
        this.lineY = top + 48;
        this.footY = top + H - 24;
        this.createX = left + PAD;
        this.closeX = left + W - PAD - 48;

        layoutTabs();

        if (!initialized) {
            initialized = true;
            anim = new RoomBrowserAnimator(openedAtMs, VISIBLE_ROWS);
            lineFromX = lineToX = tabX[activeTab];
            lineFromW = lineToW = tabW[activeTab];
        } else {
            // resize/GUI Scale 变更会重跑 init()：重算锚点但不重播开场动效，只把下划线钉回新坐标。
            lineFromX = lineToX = tabX[renderTab];
            lineFromW = lineToW = tabW[renderTab];
        }

        requestRefresh();
    }

    private void layoutTabs() {
        int x = left + PAD;
        for (int i = 0; i < tabX.length; i++) {
            tabW[i] = font.width(RoomBrowserModel.TAB_LABELS[i]) + 2;
            tabX[i] = x;
            x += tabW[i] + 14;
        }
    }

    // ============================================================
    // 数据
    // ============================================================

    private static List<RoomDto> allRooms() {
        return ClientRoomList.rooms();
    }

    /** 当前渲染中的标签页筛选结果。 */
    private List<RoomDto> filtered() {
        return filtered(renderTab);
    }

    private static List<RoomDto> filtered(int tab) {
        List<RoomDto> out = new ArrayList<>();
        for (RoomDto r : allRooms()) {
            if (RoomBrowserModel.pass(tab, r.size(), r.capacity(), r.inProgress(), r.youAreMember())) {
                out.add(r);
            }
        }
        return out;
    }

    private RoomDto selectedRoom() {
        if (selectedRoomId == null) {
            return null;
        }
        for (RoomDto r : allRooms()) {
            if (r.roomId().equals(selectedRoomId)) {
                return r;
            }
        }
        return null;
    }

    private int maxScrollRow() {
        return Math.max(0, filtered().size() - VISIBLE_ROWS);
    }

    private int rowY(int index) {
        return listY + (index - scrollRow) * ROW_H;
    }

    private boolean isSlotVisible(int slot) {
        return slot >= 0 && slot < VISIBLE_ROWS;
    }

    /** 新快照到达：差分出变动行播滚轮 + 行高亮，并夹紧滚动位置 / 收敛失效的选中态。 */
    public void onRoomsUpdated() {
        long now = MenuTween.now();
        List<RoomBrowserModel.Snapshot> next = new ArrayList<>();
        for (RoomDto r : allRooms()) {
            next.add(new RoomBrowserModel.Snapshot(r.roomId(), r.size(), r.capacity(), r.inProgress()));
        }
        if (!lastSnapshots.isEmpty()) {
            applyDeltas(RoomBrowserModel.diff(lastSnapshots, next), now);
        }
        lastSnapshots = next;

        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow()));
        if (selectedRoomId != null && selectedRoom() == null) {
            closeDrawer(now);
        }
    }

    private void applyDeltas(List<RoomBrowserModel.RowDelta> deltas, long now) {
        if (deltas.isEmpty()) {
            return;
        }
        List<RoomDto> list = filtered();
        for (RoomBrowserModel.RowDelta delta : deltas) {
            int index = indexOf(list, delta.roomId());
            if (index < 0) {
                continue;
            }
            int slot = index - scrollRow;
            if (!isSlotVisible(slot)) {
                continue;
            }
            RoomBrowserModel.Snapshot before = snapshotOf(lastSnapshots, delta.roomId());
            if (before == null) {
                continue;
            }
            rollRoomId[slot] = delta.roomId();
            flashRoomId[slot] = delta.roomId();
            anim.rowFlash[slot].start(now);
            if (delta.countDir() != 0) {
                countOldText[slot] = before.cur() + " / " + before.max();
                countDir[slot] = delta.countDir();
                anim.countRoll[slot].start(now);
            }
            if (delta.statusChanged()) {
                RoomBrowserModel.Status old =
                        RoomBrowserModel.status(before.cur(), before.max(), before.run());
                statusOldText[slot] = old.label();
                statusOldColor[slot] = old.color();
                anim.statusRoll[slot].start(now);
            }
        }
    }

    private static int indexOf(List<RoomDto> list, String roomId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).roomId().equals(roomId)) {
                return i;
            }
        }
        return -1;
    }

    private static RoomBrowserModel.Snapshot snapshotOf(List<RoomBrowserModel.Snapshot> list, String roomId) {
        for (RoomBrowserModel.Snapshot s : list) {
            if (s.roomId().equals(roomId)) {
                return s;
            }
        }
        return null;
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

    /** busy 锁（规格 §1.3）：加入转场期间锁定其余全部交互。 */
    private boolean busy() {
        return joinStage != JOIN_NONE;
    }

    // ============================================================
    // 输入
    // ============================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!busy() && MenuChrome.inRect(mouseX, mouseY, listX, listY, listW, viewportH)) {
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
        if (button != 0 || busy() || pendingNav != NAV_NONE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = MenuTween.now();

        // 抽屉在最上层，且整块吞掉点击（规格：交互元素 stopPropagation，点抽屉不关抽屉）。
        if (drawerVisible && !drawerClosing) {
            int dx = left + W - DRAWER_W;
            if (MenuChrome.inRect(mouseX, mouseY, joinBtnX(dx), joinBtnY(), joinBtnW(), 18)) {
                onJoinClicked(now);
                return true;
            }
            if (MenuChrome.inRect(mouseX, mouseY, dx, top, DRAWER_W, H)) {
                return true;
            }
        }

        if (MenuChrome.inRect(mouseX, mouseY, refreshX, refreshY, REFRESH_BTN, REFRESH_BTN)) {
            onRefreshClicked(now);
            return true;
        }
        for (int i = 0; i < tabX.length; i++) {
            if (MenuChrome.inRect(mouseX, mouseY, tabX[i], tabsY - 2, tabW[i], 14)) {
                setTab(i, now);
                return true;
            }
        }
        if (MenuChrome.inRect(mouseX, mouseY, createX, footY, 80, 18)) {
            onFooterClicked(RoomBrowserAnimator.P_CREATE, NAV_CREATE, now);
            return true;
        }
        if (MenuChrome.inRect(mouseX, mouseY, closeX, footY, 48, 18)) {
            onFooterClicked(RoomBrowserAnimator.P_CLOSE, NAV_CLOSE, now);
            return true;
        }
        if (listStage == LIST_IDLE && MenuChrome.inRect(mouseX, mouseY, listX, listY, listW, viewportH)) {
            List<RoomDto> list = filtered();
            int slot = ((int) mouseY - listY) / ROW_H;
            int index = slot + scrollRow;
            if (isSlotVisible(slot) && index < list.size()) {
                selectRoom(list.get(index), now);
                return true;
            }
        }

        // 空白点击：关抽屉并取消选中（规格 §1.3）。
        closeDrawer(now);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onFooterClicked(int pressIndex, int nav, long now) {
        anim.press[pressIndex].start(now);
        pendingNav = nav;
        playClick();
    }

    private void onRefreshClicked(long now) {
        if (listStage != LIST_IDLE) {
            return;
        }
        anim.press[RoomBrowserAnimator.P_REFRESH].start(now);
        anim.refreshSpin.start(now);
        anim.startRowFade(now);
        closeDrawer(now);
        listStage = LIST_REFRESH_FADE;
        listStageAtMs = now;
        playClick();
    }

    private void setTab(int tab, long now) {
        if (tab == activeTab || listStage != LIST_IDLE) {
            return;
        }
        activeTab = tab;
        lineFromX = lineToX;
        lineFromW = lineToW;
        lineToX = tabX[tab];
        lineToW = tabW[tab];
        anim.tabSlide.start(now);
        closeDrawer(now);
        anim.startRowExit(now);
        listStage = LIST_TAB_EXIT;
        listStageAtMs = now;
        playClick();
    }

    private void selectRoom(RoomDto room, long now) {
        if (room.roomId().equals(selectedRoomId) && drawerVisible && !drawerClosing) {
            return;
        }
        fadingAccentRoomId = selectedRoomId;
        if (fadingAccentRoomId != null) {
            anim.rowAccentOut.start(now);
        }
        if (drawerVisible && !drawerClosing) {
            // 换选：抽屉不关，内容压暗 → 换内容 → 回亮
            pendingSelectRoomId = room.roomId();
            drawerDimming = true;
            anim.drawerDim.start(now);
        } else {
            selectedRoomId = room.roomId();
            drawerVisible = true;
            drawerClosing = false;
            drawerDimming = false;
            anim.drawerOpen.start(now);
            anim.startPlayers(now);
        }
        anim.rowAccentIn.start(now);
        playClick();
    }

    private void closeDrawer(long now) {
        if (!drawerVisible || drawerClosing) {
            selectedRoomId = null;
            return;
        }
        drawerClosing = true;
        drawerDimming = false;
        pendingSelectRoomId = null;
        anim.drawerClose.start(now);
        fadingAccentRoomId = selectedRoomId;
        anim.rowAccentOut.start(now);
        selectedRoomId = null;
    }

    private void onJoinClicked(long now) {
        RoomDto room = selectedRoom();
        if (room == null) {
            return;
        }
        if (room.youAreMember()) {
            anim.joinPress.start(now);
            pendingNav = NAV_LOBBY;
            playClick();
            return;
        }
        if (room.isFull()) {
            // 满员：摇头拒绝，不改状态、不下发命令（规格 §2.2）
            anim.shake.start(now);
            return;
        }
        anim.joinPress.start(now);
        anim.joinSweep.start(now);
        joinStage = JOIN_PRESSED;
        joinStageAtMs = now;
        joinRoomName = room.modeName() + " @ " + room.arenaId();
        joinRoomId = room.roomId();
        playClick();
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ============================================================
    // 阶段机推进
    // ============================================================

    /** @return {@code true} 表示本帧已发生界面切换，调用方应立刻返回不再绘制。 */
    private boolean advance(long now) {
        if (pendingNav != NAV_NONE) {
            int pressIndex = switch (pendingNav) {
                case NAV_CREATE -> RoomBrowserAnimator.P_CREATE;
                case NAV_CLOSE -> RoomBrowserAnimator.P_CLOSE;
                default -> -1;
            };
            MenuTween.Anim gate = pressIndex >= 0 ? anim.press[pressIndex] : anim.joinPress;
            if (gate.isDone(now)) {
                int nav = pendingNav;
                pendingNav = NAV_NONE;
                doNavigate(nav);
                return true;
            }
        }

        switch (listStage) {
            case LIST_TAB_EXIT -> {
                if (now - listStageAtMs >= anim.rowExitTotalMs()) {
                    renderTab = activeTab;
                    scrollRow = 0;
                    clearRollState();
                    anim.startRows(now);
                    anim.empty.start(now);
                    listStage = LIST_IDLE;
                }
            }
            case LIST_REFRESH_FADE -> {
                if (now - listStageAtMs >= anim.rowFadeTotalMs()) {
                    anim.scan.start(now);
                    listStage = LIST_REFRESH_SCAN;
                    listStageAtMs = now;
                }
            }
            case LIST_REFRESH_SCAN -> {
                if (anim.scan.isDone(now)) {
                    requestRefresh();
                    clearRollState();
                    anim.startRows(now);
                    anim.empty.start(now);
                    listStage = LIST_IDLE;
                }
            }
            default -> {
            }
        }

        if (drawerDimming && anim.drawerDim.isDone(now)) {
            drawerDimming = false;
            selectedRoomId = pendingSelectRoomId;
            pendingSelectRoomId = null;
            anim.drawerLit.start(now);
            anim.startPlayers(now);
        }
        if (drawerClosing && anim.drawerClose.isDone(now)) {
            drawerClosing = false;
            drawerVisible = false;
        }

        return advanceJoin(now);
    }

    private boolean advanceJoin(long now) {
        switch (joinStage) {
            case JOIN_PRESSED -> {
                if (now - joinStageAtMs >= RoomBrowserAnimator.JOIN_HANDOFF_MS) {
                    anim.overlayIn.start(now);
                    anim.joinBar.start(now);
                    joinStage = JOIN_BAR;
                    joinStageAtMs = now;
                    if (minecraft != null && minecraft.player != null && joinRoomId != null) {
                        minecraft.player.connection.sendCommand("arcade room join " + joinRoomId);
                    }
                }
            }
            case JOIN_BAR -> {
                if (anim.joinBar.isDone(now)) {
                    joinStage = JOIN_HOLD;
                    joinStageAtMs = now;
                }
            }
            case JOIN_HOLD -> {
                if (now - joinStageAtMs >= RoomBrowserAnimator.JOIN_HOLD_MS) {
                    anim.overlayOut.start(now);
                    joinStage = JOIN_OUT;
                    joinStageAtMs = now;
                }
            }
            case JOIN_OUT -> {
                if (anim.overlayOut.isDone(now)) {
                    joinStage = JOIN_NONE;
                    closeDrawer(now);
                    doNavigate(NAV_LOBBY);
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    private void doNavigate(int nav) {
        if (minecraft == null) {
            return;
        }
        switch (nav) {
            case NAV_CREATE -> minecraft.setScreen(new CreateRoomScreen(this));
            case NAV_LOBBY -> minecraft.setScreen(new RoomLobbyScreen());
            default -> onClose();
        }
    }

    private void clearRollState() {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            rollRoomId[i] = null;
            flashRoomId[i] = null;
        }
    }

    // ============================================================
    // 绘制
    // ============================================================

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        if (advance(now)) {
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        int panelTop = top + fade.offsetY();
        float pa = fade.alpha();
        FlatTheme.panel(gg, left, panelTop, W, H, pa);

        renderHeader(gg, mouseX, mouseY, now, pa);
        renderTabs(gg, mouseX, mouseY, now, pa);
        renderColumnHeader(gg, now, pa);
        renderList(gg, mouseX, mouseY, now, pa);
        renderFooter(gg, mouseX, mouseY, now, pa);
        renderDrawer(gg, mouseX, mouseY, now, pa);

        super.render(gg, mouseX, mouseY, partialTick);
        renderJoinOverlay(gg, now);
    }

    private void renderHeader(GuiGraphics gg, int mouseX, int mouseY, long now, float pa) {
        float titleA = pa * anim.chrome[RoomBrowserAnimator.CH_TITLE].easedT(now, MenuTween.Ease.OUT_CUBIC);
        gg.drawString(font, "加入游戏", left + PAD, top + 8,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, titleA), false);
        gg.drawString(font, "街机模式 · 房间浏览器", left + PAD, top + 19,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.4f * titleA), false);

        float rightA = pa * anim.chrome[RoomBrowserAnimator.CH_TOP_RIGHT].easedT(now, MenuTween.Ease.OUT_CUBIC);
        String count = filtered().size() + " 个房间";
        gg.drawString(font, count, refreshX - 8 - font.width(count), top + 12,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.45f * rightA), false);

        boolean hovered = !busy() && MenuChrome.inRect(mouseX, mouseY, refreshX, refreshY, REFRESH_BTN, REFRESH_BTN);
        float press = MenuChrome.pressScale(anim.press[RoomBrowserAnimator.P_REFRESH], now);
        MenuChrome.drawScaled(gg, refreshX + REFRESH_BTN / 2, refreshY + REFRESH_BTN / 2, press, () -> {
            float borderA = (hovered ? MenuChrome.GHOST_BORDER_ALPHA_HOVER : MenuChrome.GHOST_BORDER_ALPHA_IDLE) * rightA;
            int border = FlatTheme.withAlpha(MenuChrome.WHITE, borderA);
            int x2 = refreshX + REFRESH_BTN;
            int y2 = refreshY + REFRESH_BTN;
            gg.fill(refreshX, refreshY, x2, refreshY + 1, border);
            gg.fill(refreshX, y2 - 1, x2, y2, border);
            gg.fill(refreshX, refreshY, refreshX + 1, y2, border);
            gg.fill(x2 - 1, refreshY, x2, y2, border);
            drawRefreshGlyph(gg, refreshX + REFRESH_BTN / 2, refreshY + REFRESH_BTN / 2,
                    anim.refreshSpin.easedT(now, MenuTween.Ease.OUT_CUBIC),
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, (hovered ? 1f : 0.7f) * rightA));
        });
    }

    /**
     * 刷新图标的"自转 360°"（规格 §1.3）：{@link GuiGraphics#fill} 无法旋转矩形，
     * 且本仓库没有引入 {@code PoseStack} 旋转的先例，因此用等价的几何化表达——
     * 8 段方环上"缺口"绕环行走一圈，读起来同样是一次完整旋转，且完全符合
     * AGENTS.md「图标几何化：三角形、菱形、短横线」的形状语言。
     */
    private static void drawRefreshGlyph(GuiGraphics gg, int cx, int cy, float spinE, int color) {
        int gap = (int) (MenuChrome.clamp01(spinE) * 8f) % 8;
        int[][] segments = {
                {cx - 5, cy - 5, cx - 1, cy - 4},
                {cx + 1, cy - 5, cx + 5, cy - 4},
                {cx + 4, cy - 4, cx + 5, cy},
                {cx + 4, cy + 1, cx + 5, cy + 5},
                {cx + 1, cy + 4, cx + 5, cy + 5},
                {cx - 5, cy + 4, cx - 1, cy + 5},
                {cx - 5, cy + 1, cx - 4, cy + 5},
                {cx - 5, cy - 4, cx - 4, cy},
        };
        for (int i = 0; i < segments.length; i++) {
            if (i == gap) {
                continue;
            }
            int[] s = segments[i];
            gg.fill(s[0], s[1], s[2], s[3], color);
        }
    }

    private void renderTabs(GuiGraphics gg, int mouseX, int mouseY, long now, float pa) {
        float tabsA = pa * anim.chrome[RoomBrowserAnimator.CH_TABS].easedT(now, MenuTween.Ease.OUT_CUBIC);
        for (int i = 0; i < tabX.length; i++) {
            boolean active = i == activeTab;
            boolean hovered = !busy() && MenuChrome.inRect(mouseX, mouseY, tabX[i], tabsY - 2, tabW[i], 14);
            float a = active ? 1f : (hovered ? 0.75f : 0.45f);
            gg.drawString(font, RoomBrowserModel.TAB_LABELS[i], tabX[i] + 1, tabsY,
                    FlatTheme.withAlpha(active ? MenuChrome.WHITE : MenuChrome.TEXT_BASE, a * tabsA), false);
        }

        // 唯一滑动指示器：金色 2px 下划线。开场时宽度 0→标签宽（outExpo），切换时 left+width 同补（outCubic）。
        boolean sliding = anim.tabSlide.isRunning() && !anim.tabSlide.isDone(now);
        int x;
        int w;
        if (sliding) {
            float e = anim.tabSlide.easedT(now, MenuTween.Ease.OUT_CUBIC);
            x = Math.round(MenuChrome.lerp(lineFromX, lineToX, e));
            w = Math.round(MenuChrome.lerp(lineFromW, lineToW, e));
        } else {
            x = lineToX;
            w = Math.round(lineToW * anim.tabLine.easedT(now, MenuTween.Ease.OUT_EXPO));
        }
        if (w > 0) {
            gg.fill(x, lineY, x + w, lineY + 2, FlatTheme.withAlpha(MenuChrome.GOLD, pa));
        }
    }

    private void renderColumnHeader(GuiGraphics gg, long now, float pa) {
        float headA = pa * anim.chrome[RoomBrowserAnimator.CH_HEAD].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int color = FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.35f * headA);
        gg.drawString(font, "房间", listX + ROW_PAD, top + 57, color, false);
        gg.drawString(font, "模式", colX(COL_ROOM), top + 57, color, false);
        gg.drawString(font, "地图", colX(COL_ROOM + COL_MODE), top + 57, color, false);
        // 人数/状态两列的值是居中滚轮换字（drawRollY 只支持居中），表头随之居中以保持列对齐。
        int countX = colX(COL_ROOM + COL_MODE + COL_MAP);
        gg.drawCenteredString(font, "人数", countX + colW(COL_COUNT) / 2, top + 57, color);
        int statusX = colX(COL_ROOM + COL_MODE + COL_MAP + COL_COUNT);
        gg.drawCenteredString(font, "状态", statusX + statusCellW(statusX) / 2, top + 57, color);

        float divA = pa * anim.chrome[RoomBrowserAnimator.CH_DIVIDER].easedT(now, MenuTween.Ease.OUT_CUBIC);
        gg.fill(listX, top + 67, listX + listW, top + 68, FlatTheme.withAlpha(MenuChrome.WHITE, 0.08f * divA));
    }

    /** 列起始 x（按规格的百分比列宽换算，加上行内左内边距）。 */
    private int colX(float fraction) {
        return listX + ROW_PAD + Math.round(listW * fraction);
    }

    private int colW(float fraction) {
        return Math.round(listW * fraction);
    }

    /** 状态列是最后一列（规格的 {@code flex:1}），宽度吃掉剩余空间。 */
    private int statusCellW(int statusX) {
        return listW - (statusX - listX) - 4;
    }

    private void renderList(GuiGraphics gg, int mouseX, int mouseY, long now, float pa) {
        List<RoomDto> list = filtered();
        if (list.isEmpty() && listStage == LIST_IDLE) {
            hoveredSlot = -1;
            float a = pa * anim.empty.easedT(now, MenuTween.Ease.OUT_CUBIC);
            gg.drawCenteredString(font, "没有符合条件的房间", left + W / 2, listY + viewportH / 2 - 4,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.3f * a));
            return;
        }

        updateHover(mouseX, mouseY, list, now);

        gg.enableScissor(listX, listY, listX + listW, listY + viewportH);
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int index = slot + scrollRow;
            if (index >= list.size()) {
                break;
            }
            renderRow(gg, list.get(index), rowY(index), slot, now, pa);
        }
        gg.disableScissor();

        renderScrollbar(gg, list.size(), now, pa);
        renderScanLine(gg, now, pa);
    }

    /** 悬停进入/离开都走对称补间（规格 §1.3）；选中行不响应悬停。 */
    private void updateHover(int mouseX, int mouseY, List<RoomDto> list, long now) {
        int slot = -1;
        if (!busy() && listStage == LIST_IDLE
                && MenuChrome.inRect(mouseX, mouseY, listX, listY, listW, viewportH)) {
            int candidate = (mouseY - listY) / ROW_H;
            int index = candidate + scrollRow;
            if (isSlotVisible(candidate) && index < list.size()
                    && !list.get(index).roomId().equals(selectedRoomId)) {
                slot = candidate;
            }
        }
        if (slot == hoveredSlot) {
            return;
        }
        if (hoveredSlot >= 0) {
            hoverEntering = false;
            anim.rowHover[hoveredSlot].start(now);
        }
        hoveredSlot = slot;
        if (slot >= 0) {
            hoverEntering = true;
            anim.rowHover[slot].start(now);
        }
    }

    private void renderRow(GuiGraphics gg, RoomDto room, int y, int slot, long now, float pa) {
        float enter = anim.rows[slot].easedT(now, MenuTween.Ease.OUT_CUBIC);
        float exit = switch (listStage) {
            case LIST_TAB_EXIT -> anim.rowExit[slot].easedT(now, MenuTween.Ease.IN_CUBIC);
            case LIST_REFRESH_FADE, LIST_REFRESH_SCAN -> anim.rowFade[slot].easedT(now, MenuTween.Ease.IN_CUBIC);
            default -> 0f;
        };
        float alpha = pa * enter * (1f - exit);
        if (alpha <= 0.004f) {
            return;
        }
        int yOffset = Math.round(RoomBrowserAnimator.ROW_RISE_PX * (1 - enter))
                + Math.round(RoomBrowserAnimator.ROW_SINK_PX * exit);
        int ry = y + yOffset;
        int rh = ROW_H - 2;

        boolean selected = room.roomId().equals(selectedRoomId);
        int pad = ROW_PAD;
        if (selected) {
            pad = 11;
        } else if (slot == hoveredSlot || anim.rowHover[slot].isRunning()) {
            boolean entering = slot == hoveredSlot && hoverEntering;
            float e = anim.rowHover[slot].easedT(now, MenuTween.Ease.OUT_CUBIC);
            pad = Math.round(MenuChrome.lerp(entering ? ROW_PAD : 11, entering ? 11 : ROW_PAD, e));
        }

        // 底色：选中 0.06、悬停 0.04（规格 §1.3）
        float bgAlpha = selected ? 0.06f : (slot == hoveredSlot ? 0.04f : 0f);
        if (bgAlpha > 0f) {
            gg.fill(listX, ry, listX + listW, ry + rh, FlatTheme.withAlpha(MenuChrome.WHITE, bgAlpha * alpha));
        }

        // 实时变动行的蓝色高亮闪（600ms 衰减）
        if (room.roomId().equals(flashRoomId[slot]) && !anim.rowFlash[slot].isDone(now)) {
            float fe = anim.rowFlash[slot].easedT(now, MenuTween.Ease.OUT_CUBIC);
            gg.fill(listX, ry, listX + listW, ry + rh, FlatTheme.withAlpha(RoomBrowserModel.ROW_FLASH,
                    RoomBrowserModel.ROW_FLASH_MAX_ALPHA * (1f - fe) * alpha));
        }

        renderRowAccent(gg, room, ry, rh, now, alpha, selected);

        int tx = listX + pad;
        int nameW = colW(COL_ROOM) - pad - 4;
        gg.drawString(font, trim(room.modeName().isBlank() ? room.roomId() : roomTitle(room), nameW), tx, ry + 4,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, alpha), false);
        gg.drawString(font, trim("房主:" + room.hostName(), nameW), tx, ry + 14,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.35f * alpha), false);

        int cellY = ry + 9;
        int dim = FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.6f * alpha);
        gg.drawString(font, trim(room.modeName(), colW(COL_MODE) - 6), colX(COL_ROOM), cellY, dim, false);
        gg.drawString(font, trim(room.arenaId(), colW(COL_MAP) - 6), colX(COL_ROOM + COL_MODE), cellY, dim, false);

        renderCountCell(gg, room, slot, ry, now, alpha);
        renderStatusCell(gg, room, slot, ry, now, alpha);
    }

    private static String roomTitle(RoomDto room) {
        return room.modeName() + " · " + room.targetText();
    }

    /** 选中行左侧 2px 金条：选中时 scaleY 0→1（200ms outCubic），取消时收回（140ms inCubic）。 */
    private void renderRowAccent(GuiGraphics gg, RoomDto room, int ry, int rh, long now, float alpha, boolean selected) {
        float scale;
        if (selected) {
            scale = anim.rowAccentIn.easedT(now, MenuTween.Ease.OUT_CUBIC);
        } else if (room.roomId().equals(fadingAccentRoomId) && !anim.rowAccentOut.isDone(now)) {
            scale = 1f - anim.rowAccentOut.easedT(now, MenuTween.Ease.IN_CUBIC);
        } else {
            return;
        }
        int h = Math.max(0, Math.round(rh * MenuChrome.clamp01(scale)));
        if (h <= 0) {
            return;
        }
        int cy = ry + rh / 2;
        gg.fill(listX, cy - h / 2, listX + 2, cy - h / 2 + h, FlatTheme.withAlpha(MenuChrome.GOLD, alpha));
    }

    private void renderCountCell(GuiGraphics gg, RoomDto room, int slot, int ry, long now, float alpha) {
        int cellX = colX(COL_ROOM + COL_MODE + COL_MAP);
        int cellW = colW(COL_COUNT);
        RoomBrowserModel.Status st = RoomBrowserModel.status(room.size(), room.capacity(), room.inProgress());
        int color = FlatTheme.withAlpha(st == RoomBrowserModel.Status.FULL
                ? RoomBrowserModel.STATUS_FULL : MenuChrome.TEXT_BASE, alpha);
        drawRollCell(gg, cellX, cellW, ry, room.size() + " / " + room.capacity(),
                room.roomId().equals(rollRoomId[slot]) ? countOldText[slot] : null,
                countDir[slot], anim.countRoll[slot], now, color);
    }

    private void renderStatusCell(GuiGraphics gg, RoomDto room, int slot, int ry, long now, float alpha) {
        int cellX = colX(COL_ROOM + COL_MODE + COL_MAP + COL_COUNT);
        int cellW = statusCellW(cellX);
        RoomBrowserModel.Status st = RoomBrowserModel.status(room.size(), room.capacity(), room.inProgress());
        boolean rolling = room.roomId().equals(rollRoomId[slot]) && !anim.statusRoll[slot].isDone(now);
        int newColor = FlatTheme.withAlpha(st.color(), alpha);
        if (rolling) {
            // 换字期间旧值用旧配色、新值用新配色，滚轮结束后自然只剩新色（规格：滚轮换字并换色）
            float e = anim.statusRoll[slot].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int dir = countDir[slot] != 0 ? countDir[slot] : 1;
            int cx = cellX + cellW / 2;
            int cy = ry + 13;
            gg.enableScissor(cellX, ry + 4, cellX + cellW, ry + 22);
            if (statusOldText[slot] != null) {
                int oy = cy + Math.round(MenuChrome.rollOffset(dir, e, 16, true));
                gg.drawCenteredString(font, statusOldText[slot], cx, oy - 4,
                        FlatTheme.withAlpha(statusOldColor[slot], alpha));
            }
            int ny = cy + Math.round(MenuChrome.rollOffset(dir, e, 16, false));
            gg.drawCenteredString(font, st.label(), cx, ny - 4, newColor);
            gg.disableScissor();
            return;
        }
        gg.drawCenteredString(font, st.label(), cellX + cellW / 2, ry + 9, newColor);
    }

    /** 单元格内的纵向滚轮换字：裁剪区 + {@link MenuChrome#drawRollY}（方向=语义）。 */
    private void drawRollCell(GuiGraphics gg, int cellX, int cellW, int ry, String newText, String oldText,
                              int dir, MenuTween.Anim rollAnim, long now, int color) {
        gg.enableScissor(cellX, ry + 4, cellX + cellW, ry + 22);
        MenuChrome.drawRollY(gg, font, cellX + cellW / 2, ry + 13,
                oldText, newText, dir != 0 ? dir : 1, rollAnim, now, color, 16);
        gg.disableScissor();
    }

    private void renderScrollbar(GuiGraphics gg, int total, long now, float pa) {
        if (total <= VISIBLE_ROWS) {
            return;
        }
        int trackX = listX + listW - 2;
        float listE = anim.rows.length > 0
                ? anim.rows[anim.rows.length - 1].easedT(now, MenuTween.Ease.OUT_CUBIC) : 1f;
        float alpha = pa * listE;
        gg.fill(trackX, listY, trackX + 2, listY + viewportH, FlatTheme.withAlpha(FlatTheme.SURFACE_DISABLED, alpha));
        int thumbH = Math.max(12, viewportH * VISIBLE_ROWS / total);
        int thumbY = listY + (viewportH - thumbH) * scrollRow / Math.max(1, maxScrollRow());
        gg.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, FlatTheme.withAlpha(MenuChrome.GOLD, alpha));
    }

    /** 刷新扫描线：蓝色细横条从列表顶扫到底（450ms outCubic）。 */
    private void renderScanLine(GuiGraphics gg, long now, float pa) {
        if (listStage != LIST_REFRESH_SCAN) {
            return;
        }
        float e = anim.scan.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int y = listY + Math.round((viewportH - 2) * e);
        // 中段实、两端淡，用三段填充近似规格的横向渐变（gg.fill 无渐变能力）
        int cx = listX + listW / 2;
        gg.fill(listX, y, listX + listW, y + 2, FlatTheme.withAlpha(RoomBrowserModel.SCAN_LINE, 0.25f * pa));
        gg.fill(cx - listW / 4, y, cx + listW / 4, y + 2, FlatTheme.withAlpha(RoomBrowserModel.SCAN_LINE, 0.8f * pa));
    }

    private void renderFooter(GuiGraphics gg, int mouseX, int mouseY, long now, float pa) {
        float a = pa * anim.chrome[RoomBrowserAnimator.CH_FOOTER].easedT(now, MenuTween.Ease.OUT_CUBIC);
        boolean createHovered = !busy() && pendingNav == NAV_NONE
                && MenuChrome.inRect(mouseX, mouseY, createX, footY, 80, 18);
        MenuChrome.drawPrimaryButton(gg, font, createX, footY, 80, 18, "创建房间",
                createHovered, true, false, 0f, 0f,
                MenuChrome.pressScale(anim.press[RoomBrowserAnimator.P_CREATE], now));

        boolean closeHovered = !busy() && pendingNav == NAV_NONE
                && MenuChrome.inRect(mouseX, mouseY, closeX, footY, 48, 18);
        float closePress = MenuChrome.pressScale(anim.press[RoomBrowserAnimator.P_CLOSE], now);
        MenuChrome.drawScaled(gg, closeX + 24, footY + 9, closePress, () ->
                MenuChrome.drawGhostButton(gg, font, closeX, footY, 48, 18, "关闭", closeHovered, true, a));
    }

    // ============================================================
    // 右侧详情抽屉（规格 §2.2）
    // ============================================================

    private int joinBtnX(int drawerX) {
        return drawerX + 12;
    }

    private int joinBtnW() {
        return DRAWER_W - 24;
    }

    private int joinBtnY() {
        return top + H - 26;
    }

    private void renderDrawer(GuiGraphics gg, int mouseX, int mouseY, long now, float pa) {
        if (!drawerVisible) {
            return;
        }
        int slideOff;
        if (drawerClosing) {
            slideOff = Math.round(DRAWER_W * anim.drawerClose.easedT(now, MenuTween.Ease.IN_CUBIC));
        } else {
            slideOff = Math.round(DRAWER_W * (1f - anim.drawerOpen.easedT(now, MenuTween.Ease.OUT_EXPO)));
        }
        int dx = left + W - DRAWER_W + slideOff;
        int dx2 = Math.min(left + W, dx + DRAWER_W);
        if (dx >= left + W) {
            return;
        }

        // 面板底 rgba(13,17,22,0.97) + 左描边
        gg.fill(dx, top, dx2, top + H, FlatTheme.withAlpha(0xF70D1116, pa));
        gg.fill(dx, top, dx + 1, top + H, FlatTheme.withAlpha(MenuChrome.WHITE, 0.10f * pa));

        // 金色标题条
        gg.fill(dx, top, dx2, top + 16, FlatTheme.withAlpha(MenuChrome.GOLD, pa));
        gg.drawString(font, "房间详情", dx + 10, top + 4, MenuChrome.GOLD_TEXT, false);

        RoomDto room = selectedRoom();
        float body = pa * drawerBodyAlpha(now);
        if (room != null) {
            renderDrawerBody(gg, room, dx, now, body);
        }
        renderJoinButton(gg, room, dx, mouseX, mouseY, now, pa);
    }

    /** 换选时内容压暗到 0.3（120ms inCubic）→ 换内容 → 回 1（160ms outCubic）。 */
    private float drawerBodyAlpha(long now) {
        if (drawerDimming) {
            return 1f - 0.7f * anim.drawerDim.easedT(now, MenuTween.Ease.IN_CUBIC);
        }
        if (anim.drawerLit.isRunning() && !anim.drawerLit.isDone(now)) {
            return 0.3f + 0.7f * anim.drawerLit.easedT(now, MenuTween.Ease.OUT_CUBIC);
        }
        return 1f;
    }

    private void renderDrawerBody(GuiGraphics gg, RoomDto room, int dx, long now, float a) {
        int x = dx + 12;
        int innerW = DRAWER_W - 24;
        gg.drawString(font, trim(room.modeName(), innerW), x, top + 24,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, a), false);
        gg.drawString(font, trim("房主:" + room.hostName(), innerW), x, top + 36,
                FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.4f * a), false);
        drawDrawerDivider(gg, x, top + 48, innerW, a);

        List<RoomBrowserModel.Setting> settings = RoomBrowserModel.settings(
                room.modeId(), room.modeName(), room.arenaId(), room.targetText(),
                room.timeLimitSeconds(), room.inProgress(), room.elapsedSeconds(),
                room.randomWeapons(), room.friendlyFire());
        int y = top + 54;
        for (RoomBrowserModel.Setting s : settings) {
            gg.drawString(font, s.key(), x, y, FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.4f * a), false);
            String value = trim(s.value(), innerW - font.width(s.key()) - 8);
            gg.drawString(font, value, x + innerW - font.width(value), y,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, a), false);
            y += 11;
        }

        drawDrawerDivider(gg, x, y + 2, innerW, a);
        gg.drawString(font, "玩家", x, y + 9, FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.35f * a), false);
        renderPlayers(gg, room, x, y + 22, innerW, now, a);
    }

    private static void drawDrawerDivider(GuiGraphics gg, int x, int y, int w, float a) {
        gg.fill(x, y, x + w, y + 1, FlatTheme.withAlpha(MenuChrome.WHITE, 0.08f * a));
    }

    /** 玩家列表：彩点 + 名字，房主金点，逐行右移 5→0 + 淡入，40ms/行错峰。 */
    private void renderPlayers(GuiGraphics gg, RoomDto room, int x, int y, int innerW, long now, float a) {
        List<String> names = splitPlayers(room.playersText());
        if (names.isEmpty()) {
            gg.drawString(font, "暂无玩家", x, y, FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.3f * a), false);
            return;
        }
        int shown = Math.min(names.size(), RoomBrowserAnimator.PLAYER_SLOTS);
        for (int i = 0; i < shown; i++) {
            float e = anim.players[i].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int px = x + Math.round(RoomBrowserAnimator.PLAYER_SHIFT_PX * (1 - e));
            int py = y + i * 11;
            float pAlpha = a * e;
            boolean host = names.get(i).equals(room.hostName());
            gg.fill(px, py + 2, px + 3, py + 5,
                    FlatTheme.withAlpha(host ? MenuChrome.GOLD : FlatTheme.ALPHA, pAlpha));
            String label = names.get(i) + (host ? " (房主)" : "");
            gg.drawString(font, trim(label, innerW - 10), px + 8, py,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.7f * pAlpha), false);
        }
        if (names.size() > shown) {
            gg.drawString(font, "… 还有 " + (names.size() - shown) + " 人", x, y + shown * 11,
                    FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.3f * a), false);
        }
    }

    /**
     * 抽屉底部加入按钮：满员变灰 +「房间已满」+ 点击摇头拒绝；人数恢复时按钮自动复原
     * （状态每帧从最新 {@link RoomDto} 派生，无需额外复原逻辑）。
     */
    private void renderJoinButton(GuiGraphics gg, RoomDto room, int dx, int mouseX, int mouseY, long now, float pa) {
        int bx = joinBtnX(dx);
        int by = joinBtnY();
        int bw = joinBtnW();
        boolean member = room != null && room.youAreMember();
        boolean full = room != null && room.isFull() && !member;
        String label = room == null ? "—" : (member ? "进入大厅" : (full ? "房间已满" : "加 入"));

        int shakeDx = 0;
        if (anim.shake.isRunning() && !anim.shake.isDone(now)) {
            shakeDx = Math.round(RoomBrowserModel.shakeOffset(anim.shake.easedT(now, MenuTween.Ease.LINEAR)));
        }
        int fx = bx + shakeDx;
        boolean hovered = !busy() && MenuChrome.inRect(mouseX, mouseY, bx, by, bw, 18);
        float press = MenuChrome.pressScale(anim.joinPress, now);
        float sweep = anim.joinSweep.isRunning() ? anim.joinSweep.easedT(now, MenuTween.Ease.OUT_CUBIC) : 0f;

        MenuChrome.drawScaled(gg, fx + bw / 2, by + 9, press, () -> {
            int bg = full ? FlatTheme.withAlpha(MenuChrome.WHITE, 0.12f * pa) : FlatTheme.withAlpha(MenuChrome.GOLD, pa);
            gg.fill(fx, by, fx + bw, by + 18, bg);
            if (hovered && !full) {
                gg.fill(fx, by, fx + bw, by + 1, FlatTheme.withAlpha(MenuChrome.WHITE, MenuChrome.PRIMARY_HOVER_HIGHLIGHT_ALPHA));
            }
            int textColor = full ? FlatTheme.withAlpha(MenuChrome.TEXT_BASE, 0.4f * pa) : MenuChrome.GOLD_TEXT;
            gg.drawCenteredString(font, label, fx + bw / 2, by + 5, textColor);
            MenuChrome.drawSweep(gg, fx, by, bw, 18, sweep, MenuChrome.PRIMARY_SWEEP_MAX_ALPHA);
        });
    }

    // ============================================================
    // 加入转场（规格 §2.4）
    // ============================================================

    private void renderJoinOverlay(GuiGraphics gg, long now) {
        if (joinStage < JOIN_BAR) {
            return;
        }
        float alpha = joinStage == JOIN_OUT
                ? 1f - anim.overlayOut.easedT(now, MenuTween.Ease.IN_CUBIC)
                : anim.overlayIn.easedT(now, MenuTween.Ease.OUT_CUBIC);
        gg.fill(0, 0, width, height, FlatTheme.withAlpha(0xD9080A0D, alpha));

        boolean connected = joinStage >= JOIN_HOLD;
        String text = connected ? "已连接" : "正在加入 · " + joinRoomName;
        int cy = Math.round(height * 0.44f);
        gg.drawCenteredString(font, text, width / 2, cy, FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, alpha));

        int barW = 160;
        int barX = (width - barW) / 2;
        int barY = cy + 16;
        gg.fill(barX, barY, barX + barW, barY + 2, FlatTheme.withAlpha(MenuChrome.WHITE, 0.12f * alpha));
        float progress = connected ? 1f : anim.joinBar.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int fill = Math.round(barW * progress);
        if (fill > 0) {
            int color = connected ? RoomBrowserModel.STATUS_WAITING : MenuChrome.GOLD;
            gg.fill(barX, barY, barX + fill, barY + 2, FlatTheme.withAlpha(color, alpha));
        }
    }

    // ============================================================
    // 文本工具
    // ============================================================

    private static List<String> splitPlayers(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null || raw.isBlank() || raw.equals("-")) {
            return names;
        }
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private String trim(String text, int maxW) {
        if (text == null) {
            return "";
        }
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
    public boolean isPauseScreen() {
        return false;
    }
}
