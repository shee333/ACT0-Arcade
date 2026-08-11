package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
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
 * 当前玩家所在房间的大厅界面：槽位、人数、快速开始/退出和基础人数调整。
 *
 * <p>视觉/动效对照 MenuChrome 统一改造：面板外壳与模式/进度信息卡沿用 {@link FlatTheme}
 * （HUD 层冷灰蓝），CTA/幽灵按钮/灯态控件/滑动指示器/数字滚轮换成 {@link MenuChrome}
 * 金色强调语言，由 {@link MenuTween} 驱动三类动效：12 槽位开场对角级联
 * （见 {@link RoomLobbyAnimator} 类头注释）、队伍切换唯一滑动指示器、全部可点击控件的
 * 按压回弹反馈 + UI 点击音效。次级导航（退出/浏览器）延迟到按压回弹播完才真正跳转，
 * 理由与 {@code CreateRoomScreen} 的 {@code pendingNav} 机制一致——不然玩家看不到反馈。
 *
 * <p>AI 士兵席位的可视化与三个房主控件（撤走/添加/难度循环）拆在
 * {@link RoomLobbyBotPanel}；本类只负责调用它并在点击时发指令。
 */
public final class RoomLobbyScreen extends Screen {
    private static final int W = 330;
    /**
     * 面板高度 220 → 240：新增的 AI 士兵行需要一整条 20px 控件行加分隔线，原高度里塞不下。
     *
     * <p><b>240 是硬上限，不可再增</b>：{@code Window.calculateScale} 只在
     * {@code 高/(缩放+1) >= 240} 时才继续提升缩放，因此缩放后的 GUI 高度下限恒为 240。
     * 1280×720 配自动缩放正好取到 240，面板一旦超过就会被上下切掉——720p 是极常见的
     * 笔记本分辨率，切掉面板边框远比少显示两个槽位严重。布局不变式由
     * {@code RoomLobbyBotPanelTest} 锁死。
     */
    static final int H = 240;
    private static final int REFRESH_INTERVAL = 30;

    private static final int NAV_NONE = -1;

    /**
     * 各行相对 {@code top} 的偏移，全部落在 8px 网格上。
     *
     * <p>队伍选择行从改动前的 154 上移到 136：{@link #H} 被 240 的 GUI 高度下限卡死后，
     * 槽位网格、队伍行、AI 士兵行、底部控件行四者必须共享同一段纵向预算，容不下原来的间距。
     * 代价是团队模式的可见槽位从 3 行降到 2 行（4 格），仍足够覆盖 2v2 与 4 人团队房的满员
     * 情形，超出部分由既有的"…还有 N 个槽位"承接。
     */
    static final int SLOT_TOP_DY = 75;
    static final int TEAM_ROW_DY = 136;
    static final int BOT_DIVIDER_DY = 168;
    static final int BOT_ROW_DY = 176;

    private int left;
    private int top;
    private int refreshTimer;
    private final long openedAtMs = System.currentTimeMillis();
    private boolean initialized;
    private RoomLobbyAnimator anim;

    private int startX, startY, startW, startH;
    private int leaveX, leaveY, leaveW, leaveH;
    private int minusX, plusX, adjustY, adjustW, adjustH;
    private int browserX, browserY, browserW, browserH;
    private int blueX, redX, teamY, teamW, teamH;

    /** 待跳转到 {@link RoomBrowserScreen} 前要等待的按压反馈索引；{@link #NAV_NONE} 表示无待跳转。 */
    private int pendingNavPress = NAV_NONE;

    // 人数数值滚轮的过渡状态（哪个旧值在滑出、方向）
    private int lastCapacityValue = -1;
    private String capacityOldText = "";
    private int capacityDir = 1;

    // 队伍切换：本地乐观状态（服务端 RoomDto 目前不回传"你的队伍"，故用客户端最近一次
    // 点击结果作为唯一状态源，与 CreateRoomScreen 的模型状态同一套哲学）
    private int selectedTeam;
    private int teamIndicatorFromX;
    private int teamIndicatorToX;

    private final RoomLobbyBotPanel botPanel = new RoomLobbyBotPanel();

    public RoomLobbyScreen() {
        super(Component.literal("房间大厅"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (!initialized) {
            initialized = true;
            anim = new RoomLobbyAnimator(MenuTween.now());
        }
        requestRefresh();
    }

    public void onRoomsUpdated() {
        if (currentRoom() == null && minecraft != null) {
            minecraft.setScreen(new RoomBrowserScreen());
        }
    }

    @Override
    public void tick() {
        if (++refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0;
            requestRefresh();
        }
    }

    private void requestRefresh() {
        ArcadeNetwork.CHANNEL.sendToServer(new RequestRoomListPacket());
    }

    private RoomDto currentRoom() {
        for (RoomDto room : ClientRoomList.rooms()) {
            if (room.youAreMember()) {
                return room;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        if (pendingNavPress != NAV_NONE && anim.press[pendingNavPress].isDone(now)) {
            pendingNavPress = NAV_NONE;
            if (minecraft != null) {
                minecraft.setScreen(new RoomBrowserScreen());
            }
            return;
        }

        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        FlatTheme.panel(gg, left, top, W, H, fade.alpha());
        FlatTheme.titleBar(gg, left, top, W, 24);
        RoomDto room = currentRoom();
        if (room == null) {
            gg.drawCenteredString(font, "§c房间不存在或已关闭", left + W / 2, top + 92, FlatTheme.DANGER);
            browserX = left + W / 2 - 40;
            browserY = top + 125;
            browserW = 80;
            browserH = 20;
            boolean hovered = pendingNavPress == NAV_NONE
                    && MenuChrome.inRect(mouseX, mouseY, browserX, browserY, browserW, browserH);
            float pressScale = MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_BROWSER], now);
            MenuChrome.drawPrimaryButton(gg, font, browserX, browserY, browserW, browserH, "返回浏览器",
                    hovered, true, false, 0f, 0f, pressScale);
            return;
        }

        gg.drawCenteredString(font, "房间大厅", left + W / 2, top + 9, FlatTheme.TEXT_HEADER);
        FlatTheme.card(gg, left + 12, top + 31, W - 24, 32, false);
        gg.drawString(font, "§7模式 §f" + room.modeName() + " §8/ §7地图 §f" + room.arenaId(), left + 18, top + 37, FlatTheme.TEXT, false);
        gg.drawString(font, "§7房主 §f" + room.hostName() + " §8/ §7目标 §f" + room.targetText(), left + 18, top + 49, FlatTheme.TEXT, false);
        int progressX = left + W - 82;
        gg.drawString(font, room.size() + "/" + room.capacity(), progressX + 52, top + 43, FlatTheme.SUCCESS, false);
        FlatTheme.progress(gg, progressX, top + 47, 46, 4,
                room.capacity() <= 0 ? 0f : room.size() / (float) room.capacity(), FlatTheme.SUCCESS);

        updateCapacityRoll(room, now);
        // 必须先采样 bot 名单：槽位绘制要靠它区分 AI 与真人。
        botPanel.update(room, now, anim);
        renderSlots(gg, room, mouseX, mouseY, now);
        botPanel.render(gg, font, room, isHost(room), mouseX, mouseY, now, anim,
                left, top, W, BOT_DIVIDER_DY, BOT_ROW_DY);
        renderControls(gg, room, mouseX, mouseY, now);
        super.render(gg, mouseX, mouseY, partialTick);
    }

    /** 检测人数变化（自身操作或其他客户端刷新回来的房间状态），据此驱动方向性数值滚轮。 */
    private void updateCapacityRoll(RoomDto room, long now) {
        if (lastCapacityValue >= 0 && room.capacity() != lastCapacityValue) {
            capacityOldText = lastCapacityValue + " 人";
            capacityDir = MenuChrome.scrollDir(lastCapacityValue, room.capacity());
            anim.capacityRoll.start(now);
        }
        lastCapacityValue = room.capacity();
    }

    private void renderSlots(GuiGraphics gg, RoomDto room, int mouseX, int mouseY, long now) {
        List<String> names = splitPlayers(room.playersText());
        int cols = RoomLobbyAnimator.SLOT_COLS;
        int slotW = (W - 34) / cols;
        int slotH = 18;
        int startY = top + SLOT_TOP_DY;
        boolean teamRow = supportsTeamChoice(room);
        int rows = RoomLobbyBotPanel.visibleSlotRows(SLOT_TOP_DY, slotH + 4,
                teamRow ? TEAM_ROW_DY : BOT_DIVIDER_DY, RoomLobbyAnimator.SLOT_COUNT / cols);
        int count = Math.min(Math.max(room.capacity(), names.size()), rows * cols);
        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = left + 14 + col * (slotW + 6);
            int y = startY + row * (slotH + 4);
            float e = anim.slotCascade[i].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int yOffset = Math.round(4 * (1 - e));
            boolean filled = i < names.size();
            boolean bot = filled && botPanel.isBot(names.get(i));
            // bot 槽位不吃高亮描边：把"这一格是活人"的强调色留给真人，是最省噪音的一层区分。
            drawSlotCard(gg, x, y + yOffset, slotW, slotH, filled && !bot, e);
            String text = filled
                    ? (bot ? RoomLobbyBotPanel.BOT_PREFIX : RoomLobbyBotPanel.HUMAN_PREFIX)
                            + trim(names.get(i), slotW - (bot ? 34 : 18))
                    : "§8空位 " + (i + 1);
            gg.drawString(font, text, x + 5, y + yOffset + 5,
                    FlatTheme.withAlpha(filled ? FlatTheme.TEXT : FlatTheme.TEXT_DIM, e), false);
            if (bot) {
                gg.drawString(font, RoomLobbyBotPanel.BOT_TAG,
                        x + slotW - 5 - font.width(RoomLobbyBotPanel.BOT_TAG), y + yOffset + 5,
                        FlatTheme.withAlpha(FlatTheme.TEXT_DIM, e), false);
            }
        }
        if (room.capacity() > count) {
            gg.drawString(font, "§8… 还有 " + (room.capacity() - count) + " 个槽位", left + 14,
                    startY + rows * (slotH + 4) + 4, FlatTheme.TEXT_DIM, false);
        }
        if (teamRow) {
            renderTeamRow(gg, mouseX, mouseY, now);
        }
    }

    /**
     * 复刻 {@link FlatTheme#card} 的视觉（表面 + 1px 描边），但叠加级联淡入的 {@code alpha}——
     * {@code FlatTheme.card} 本身无 alpha 形参，故不复用，就地画等价的四条边框 + 填充。
     */
    private void drawSlotCard(GuiGraphics gg, int x, int y, int w, int h, boolean highlight, float alpha) {
        int x2 = x + w;
        int y2 = y + h;
        gg.fill(x, y, x2, y2, FlatTheme.withAlpha(highlight ? FlatTheme.SURFACE_HOVER : FlatTheme.SURFACE, alpha));
        int border = FlatTheme.withAlpha(highlight ? FlatTheme.ACCENT : FlatTheme.BORDER, alpha);
        gg.fill(x, y, x2, y + 1, border);
        gg.fill(x, y2 - 1, x2, y2, border);
        gg.fill(x, y, x + 1, y2, border);
        gg.fill(x2 - 1, y, x2, y2, border);
    }

    /**
     * 队伍选择行：不再是"两个各自变色的按钮"，而是一个在两者之间滑动的唯一指示器
     * （220ms outCubic + pulse 横向拉伸）叠加两个幽灵按钮文字层。
     */
    private void renderTeamRow(GuiGraphics gg, int mouseX, int mouseY, long now) {
        teamY = top + 154;
        teamW = 72;
        teamH = 18;
        blueX = left + 14;
        redX = blueX + teamW + 8;

        drawTeamIndicator(gg, now);

        boolean blueHovered = MenuChrome.inRect(mouseX, mouseY, blueX, teamY, teamW, teamH);
        boolean redHovered = MenuChrome.inRect(mouseX, mouseY, redX, teamY, teamW, teamH);
        float bluePress = MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_TEAM_BLUE], now);
        float redPress = MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_TEAM_RED], now);
        MenuChrome.drawScaled(gg, blueX + teamW / 2, teamY + teamH / 2, bluePress, () ->
                MenuChrome.drawGhostButton(gg, font, blueX, teamY, teamW, teamH, "加入蓝队", blueHovered, true, 1f));
        MenuChrome.drawScaled(gg, redX + teamW / 2, teamY + teamH / 2, redPress, () ->
                MenuChrome.drawGhostButton(gg, font, redX, teamY, teamW, teamH, "加入红队", redHovered, true, 1f));
    }

    private void drawTeamIndicator(GuiGraphics gg, long now) {
        if (selectedTeam == 0 && !anim.teamIndicator.isRunning()) {
            return;
        }
        boolean sliding = teamIndicatorFromX != teamIndicatorToX;
        float e = anim.teamIndicator.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int x = sliding ? Math.round(MenuChrome.lerp(teamIndicatorFromX, teamIndicatorToX, e)) : teamIndicatorToX;
        // 首次出现（从未选过队，from==to）走淡入；在两队之间滑动时全程不透明，靠位移传达方向。
        float alpha = sliding ? 1f : e;
        float stretch = sliding ? MenuTween.pulse(e) : 1f;
        int cx = x + teamW / 2;
        int cy = teamY + teamH / 2;
        MenuChrome.drawScaledAxis(gg, cx, cy, stretch, 1f, () -> {
            gg.fill(x, teamY, x + teamW, teamY + teamH, FlatTheme.withAlpha(MenuChrome.WHITE, 0.10f * alpha));
            gg.fill(x, teamY, x + 2, teamY + teamH, FlatTheme.withAlpha(MenuChrome.GOLD, alpha));
        });
    }

    private void onTeamSelect(int team, int targetX) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        long now = MenuTween.now();
        int fromX = selectedTeam == 0 ? targetX : (selectedTeam == 1 ? blueX : redX);
        teamIndicatorFromX = fromX;
        teamIndicatorToX = targetX;
        anim.teamIndicator.start(now);
        selectedTeam = team;
        anim.press[team == 1 ? RoomLobbyAnimator.P_TEAM_BLUE : RoomLobbyAnimator.P_TEAM_RED].start(now);
        minecraft.player.connection.sendCommand("arcade room team " + (team == 1 ? "blue" : "red"));
        requestRefresh();
        playClick();
    }

    private void renderControls(GuiGraphics gg, RoomDto room, int mouseX, int mouseY, long now) {
        int y = top + H - 30;
        boolean host = isHost(room);
        startX = left + 12;
        startY = y;
        startW = 72;
        startH = 20;
        boolean startHovered = host && pendingNavPress == NAV_NONE
                && MenuChrome.inRect(mouseX, mouseY, startX, startY, startW, startH);
        MenuChrome.drawPrimaryButton(gg, font, startX, startY, startW, startH, host ? "立即开始" : "房主开始",
                startHovered, host, false, 0f, 0f, MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_START], now));

        minusX = startX + startW + 8;
        adjustY = y;
        adjustW = 24;
        adjustH = 20;
        plusX = minusX + adjustW + 64;

        boolean minusEnabled = host && room.capacity() > room.size();
        boolean plusEnabled = host && room.capacity() < 32;
        boolean minusHovered = minusEnabled && MenuChrome.inRect(mouseX, mouseY, minusX, adjustY, adjustW, adjustH);
        boolean plusHovered = plusEnabled && MenuChrome.inRect(mouseX, mouseY, plusX, adjustY, adjustW, adjustH);
        MenuChrome.drawLightControl(gg, font, minusX, adjustY, adjustW, adjustH, "-", minusHovered, minusEnabled,
                MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_MINUS], now), 1f);

        int rollCx = minusX + adjustW + 32;
        int rollCy = adjustY + adjustH / 2;
        gg.enableScissor(minusX + adjustW, adjustY, plusX, adjustY + adjustH);
        MenuChrome.drawRollY(gg, font, rollCx, rollCy, capacityOldText, room.capacity() + " 人", capacityDir,
                anim.capacityRoll, now, FlatTheme.TEXT_DIM, adjustH);
        gg.disableScissor();

        MenuChrome.drawLightControl(gg, font, plusX, adjustY, adjustW, adjustH, "+", plusHovered, plusEnabled,
                MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_PLUS], now), 1f);

        leaveW = 54;
        leaveH = 20;
        leaveX = left + W - leaveW - 12;
        leaveY = y;
        boolean leaveHovered = pendingNavPress == NAV_NONE
                && MenuChrome.inRect(mouseX, mouseY, leaveX, leaveY, leaveW, leaveH);
        float leavePress = MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_LEAVE], now);
        MenuChrome.drawScaled(gg, leaveX + leaveW / 2, leaveY + leaveH / 2, leavePress, () ->
                MenuChrome.drawGhostButton(gg, font, leaveX, leaveY, leaveW, leaveH, "退出", leaveHovered, true, 1f));

        browserW = 66;
        browserH = 20;
        browserX = leaveX - browserW - 8;
        browserY = y;
        boolean browserHovered = pendingNavPress == NAV_NONE
                && MenuChrome.inRect(mouseX, mouseY, browserX, browserY, browserW, browserH);
        float browserPress = MenuChrome.pressScale(anim.press[RoomLobbyAnimator.P_BROWSER], now);
        MenuChrome.drawScaled(gg, browserX + browserW / 2, browserY + browserH / 2, browserPress, () ->
                MenuChrome.drawGhostButton(gg, font, browserX, browserY, browserW, browserH, "浏览器", browserHovered, true, 1f));
    }

    /**
     * AI 士兵行三个控件的共用落点：可用则按压回弹 + 点击音效 + 发指令 + 请求刷新。
     *
     * <p>不可用（非房主 / 没有 bot 可撤 / 席位已满 / 对局已开始）时完全静默，一帧动画都不播——
     * "抖了一下但数值没变"比毫无反应更让玩家困惑，这是项目动效规范里的边界静默要求。
     */
    private boolean onBotAction(boolean enabled, int pressIndex, String command, long now) {
        if (!enabled) {
            return false;
        }
        anim.press[pressIndex].start(now);
        minecraft.player.connection.sendCommand(command);
        requestRefresh();
        playClick();
        return true;
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈；边界静默的调整不经过这里。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || minecraft == null || minecraft.player == null || pendingNavPress != NAV_NONE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = MenuTween.now();
        RoomDto room = currentRoom();
        if (room == null) {
            if (MenuChrome.inRect(mouseX, mouseY, browserX, browserY, browserW, browserH)) {
                anim.press[RoomLobbyAnimator.P_BROWSER].start(now);
                pendingNavPress = RoomLobbyAnimator.P_BROWSER;
                playClick();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean host = isHost(room);
        if (host && MenuChrome.inRect(mouseX, mouseY, startX, startY, startW, startH)) {
            anim.press[RoomLobbyAnimator.P_START].start(now);
            minecraft.player.connection.sendCommand("arcade room start");
            playClick();
            return true;
        }
        if (host && MenuChrome.inRect(mouseX, mouseY, minusX, adjustY, adjustW, adjustH)) {
            anim.press[RoomLobbyAnimator.P_MINUS].start(now);
            minecraft.player.connection.sendCommand("arcade room size " + Math.max(room.size(), room.capacity() - capacityStep(room)));
            requestRefresh();
            playClick();
            return true;
        }
        if (host && MenuChrome.inRect(mouseX, mouseY, plusX, adjustY, adjustW, adjustH)) {
            anim.press[RoomLobbyAnimator.P_PLUS].start(now);
            minecraft.player.connection.sendCommand("arcade room size " + Math.min(32, room.capacity() + capacityStep(room)));
            requestRefresh();
            playClick();
            return true;
        }
        if (botPanel.hitRemove(mouseX, mouseY)) {
            return onBotAction(botPanel.canRemove(room, host),
                    RoomLobbyAnimator.P_BOT_REMOVE, "arcade room bot remove", now);
        }
        if (botPanel.hitAdd(mouseX, mouseY)) {
            return onBotAction(botPanel.canAdd(room, host),
                    RoomLobbyAnimator.P_BOT_ADD, "arcade room bot add", now);
        }
        if (botPanel.hitDifficulty(mouseX, mouseY)) {
            return onBotAction(botPanel.canSetDifficulty(room, host),
                    RoomLobbyAnimator.P_BOT_DIFF,
                    "arcade room bot difficulty " + botPanel.nextDifficultyArg(), now);
        }
        if (supportsTeamChoice(room) && MenuChrome.inRect(mouseX, mouseY, blueX, teamY, teamW, teamH)) {
            onTeamSelect(1, blueX);
            return true;
        }
        if (supportsTeamChoice(room) && MenuChrome.inRect(mouseX, mouseY, redX, teamY, teamW, teamH)) {
            onTeamSelect(2, redX);
            return true;
        }
        if (MenuChrome.inRect(mouseX, mouseY, leaveX, leaveY, leaveW, leaveH)) {
            anim.press[RoomLobbyAnimator.P_LEAVE].start(now);
            minecraft.player.connection.sendCommand("arcade room leave");
            pendingNavPress = RoomLobbyAnimator.P_LEAVE;
            playClick();
            return true;
        }
        if (MenuChrome.inRect(mouseX, mouseY, browserX, browserY, browserW, browserH)) {
            anim.press[RoomLobbyAnimator.P_BROWSER].start(now);
            pendingNavPress = RoomLobbyAnimator.P_BROWSER;
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHost(RoomDto room) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getGameProfile().getName().equals(room.hostName());
    }

    private static boolean supportsTeamChoice(RoomDto room) {
        return "团队死斗".equals(room.modeName()) || "热区".equals(room.modeName()) || "跳狙飞人".equals(room.modeName())
                || "军备竞赛 团队".equals(room.modeName());
    }

    private static int capacityStep(RoomDto room) {
        return supportsTeamChoice(room) ? 2 : 1;
    }

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

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && font.width(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
