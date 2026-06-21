package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.shee33.act0.arcade.client.ClientRoomList;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.network.RequestRoomListPacket;
import org.shee33.act0.arcade.network.RoomDto;

import java.util.ArrayList;
import java.util.List;

/** 当前玩家所在房间的大厅界面：槽位、人数、快速开始/退出和基础人数调整。 */
public final class RoomLobbyScreen extends Screen {
    private static final int W = 330;
    private static final int H = 220;
    private static final int REFRESH_INTERVAL = 30;

    private int left;
    private int top;
    private int refreshTimer;

    private int startX, startY, startW, startH;
    private int leaveX, leaveY, leaveW, leaveH;
    private int minusX, plusX, adjustY, adjustW, adjustH;
    private int browserX, browserY, browserW, browserH;
    private int blueX, redX, teamY, teamW, teamH;

    public RoomLobbyScreen() {
        super(Component.literal("房间大厅"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
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
            if (room.youAreMember() && !room.roomId().startsWith("bf@")) {
                return room;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, W, H);
        RoomDto room = currentRoom();
        if (room == null) {
            gg.drawCenteredString(font, "§c房间不存在或已关闭", left + W / 2, top + 92, 0xFFFFFFFF);
            renderButton(gg, left + W / 2 - 40, top + 125, 80, 20, "返回浏览器", mouseX, mouseY, true);
            browserX = left + W / 2 - 40;
            browserY = top + 125;
            browserW = 80;
            browserH = 20;
            return;
        }

        gg.drawCenteredString(font, "§l房间大厅", left + W / 2, top + 10, PixelTheme.ACCENT);
        gg.drawString(font, "§7模式 §f" + room.modeName() + " §8/ §7地图 §f" + room.arenaId(), left + 14, top + 30, PixelTheme.TEXT, false);
        gg.drawString(font, "§7房主 §f" + room.hostName() + " §8/ §7目标 §f" + room.targetText(), left + 14, top + 42, PixelTheme.TEXT, false);
        gg.drawString(font, "§7人数 §f" + room.size() + "/" + room.capacity(), left + 14, top + 54, PixelTheme.TEXT, false);

        renderSlots(gg, room, mouseX, mouseY);
        renderControls(gg, room, mouseX, mouseY);
        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void renderSlots(GuiGraphics gg, RoomDto room, int mouseX, int mouseY) {
        List<String> names = splitPlayers(room.playersText());
        int cols = 2;
        int slotW = (W - 34) / cols;
        int slotH = 18;
        int startY = top + 75;
        int count = Math.min(Math.max(room.capacity(), names.size()), 12);
        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = left + 14 + col * (slotW + 6);
            int y = startY + row * (slotH + 4);
            boolean filled = i < names.size();
            PixelTheme.row(gg, x, y, slotW, slotH, filled);
            String text = filled ? "§a● §f" + trim(names.get(i), slotW - 18) : "§8空位 " + (i + 1);
            gg.drawString(font, text, x + 5, y + 5, filled ? PixelTheme.TEXT : PixelTheme.TEXT_DIM, false);
        }
        if (room.capacity() > count) {
            gg.drawString(font, "§8… 还有 " + (room.capacity() - count) + " 个槽位", left + 14, top + 145, PixelTheme.TEXT_DIM, false);
        }
        if (supportsTeamChoice(room)) {
            teamY = top + 154;
            teamW = 72;
            teamH = 18;
            blueX = left + 14;
            redX = blueX + teamW + 8;
            renderButton(gg, blueX, teamY, teamW, teamH, "加入蓝队", mouseX, mouseY, true);
            renderButton(gg, redX, teamY, teamW, teamH, "加入红队", mouseX, mouseY, true);
        }
    }

    private void renderControls(GuiGraphics gg, RoomDto room, int mouseX, int mouseY) {
        int y = top + H - 30;
        boolean host = isHost(room);
        startX = left + 12;
        startY = y;
        startW = 72;
        startH = 20;
        renderButton(gg, startX, startY, startW, startH, host ? "立即开始" : "房主开始", mouseX, mouseY, host);

        minusX = startX + startW + 8;
        adjustY = y;
        adjustW = 24;
        adjustH = 20;
        plusX = minusX + adjustW + 64;
        renderButton(gg, minusX, adjustY, adjustW, adjustH, "-", mouseX, mouseY, host && room.capacity() > room.size());
        gg.drawCenteredString(font, "§7人数", minusX + adjustW + 32, y + 6, PixelTheme.TEXT_DIM);
        renderButton(gg, plusX, adjustY, adjustW, adjustH, "+", mouseX, mouseY, host && room.capacity() < 32);

        leaveW = 54;
        leaveH = 20;
        leaveX = left + W - leaveW - 12;
        leaveY = y;
        renderButton(gg, leaveX, leaveY, leaveW, leaveH, "退出", mouseX, mouseY, true);

        browserW = 66;
        browserH = 20;
        browserX = leaveX - browserW - 8;
        browserY = y;
        renderButton(gg, browserX, browserY, browserW, browserH, "浏览器", mouseX, mouseY, true);
    }

    private void renderButton(GuiGraphics gg, int x, int y, int w, int h, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && inRect(mouseX, mouseY, x, y, w, h);
        int border = enabled ? (hovered ? PixelTheme.ACCENT : PixelTheme.BEVEL_LIGHT) : PixelTheme.BEVEL_SHADOW;
        int body = enabled ? (hovered ? 0xFF2B3522 : 0xFF20261C) : 0xFF171A15;
        gg.fill(x, y, x + w, y + h, PixelTheme.BORDER_DARK);
        gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        gg.fill(x + 1, y + 1, x + w - 1, y + 2, border);
        gg.fill(x + 1, y + 1, x + 2, y + h - 1, border);
        int color = enabled ? PixelTheme.TEXT : PixelTheme.TEXT_DIM;
        gg.drawCenteredString(font, label, x + w / 2, y + 6, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || minecraft == null || minecraft.player == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        RoomDto room = currentRoom();
        if (room == null) {
            if (inRect((int) mouseX, (int) mouseY, browserX, browserY, browserW, browserH)) {
                minecraft.setScreen(new RoomBrowserScreen());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean host = isHost(room);
        if (host && inRect((int) mouseX, (int) mouseY, startX, startY, startW, startH)) {
            minecraft.player.connection.sendCommand("arcade room start");
            return true;
        }
        if (host && inRect((int) mouseX, (int) mouseY, minusX, adjustY, adjustW, adjustH)) {
            minecraft.player.connection.sendCommand("arcade room size " + Math.max(room.size(), room.capacity() - capacityStep(room)));
            requestRefresh();
            return true;
        }
        if (host && inRect((int) mouseX, (int) mouseY, plusX, adjustY, adjustW, adjustH)) {
            minecraft.player.connection.sendCommand("arcade room size " + Math.min(32, room.capacity() + capacityStep(room)));
            requestRefresh();
            return true;
        }
        if (supportsTeamChoice(room) && inRect((int) mouseX, (int) mouseY, blueX, teamY, teamW, teamH)) {
            minecraft.player.connection.sendCommand("arcade room team blue");
            requestRefresh();
            return true;
        }
        if (supportsTeamChoice(room) && inRect((int) mouseX, (int) mouseY, redX, teamY, teamW, teamH)) {
            minecraft.player.connection.sendCommand("arcade room team red");
            requestRefresh();
            return true;
        }
        if (inRect((int) mouseX, (int) mouseY, leaveX, leaveY, leaveW, leaveH)) {
            minecraft.player.connection.sendCommand("arcade room leave");
            minecraft.setScreen(new RoomBrowserScreen());
            return true;
        }
        if (inRect((int) mouseX, (int) mouseY, browserX, browserY, browserW, browserH)) {
            minecraft.setScreen(new RoomBrowserScreen());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHost(RoomDto room) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getGameProfile().getName().equals(room.hostName());
    }

    private static boolean supportsTeamChoice(RoomDto room) {
        return "团队死斗".equals(room.modeName()) || "跳狙飞人".equals(room.modeName());
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

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
