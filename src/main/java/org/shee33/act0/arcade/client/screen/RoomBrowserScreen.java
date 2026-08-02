package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
 */
public final class RoomBrowserScreen extends Screen {

    private static final int W = 320;
    private static final int H = 200;
    private static final int ROW_H = 36;
    private static final int VISIBLE_ROWS = 4;
    private static final int ACTION_W = 52;
    private static final int REFRESH_INTERVAL = 40; // ticks (2s)

    private int left;
    private int top;
    private int listX;
    private int listY;
    private int listW;
    private int viewportH;
    private int scrollRow;
    private int refreshTimer;
    private final long openedAtMs = System.currentTimeMillis();

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

        int footY = top + H - 26;
        int bx = left + 10;

        addRenderableWidget(Button.builder(Component.literal("刷新"), b -> requestRefresh())
                .bounds(bx, footY, 50, 18).build());
        bx += 56;
        addRenderableWidget(Button.builder(Component.literal("§a创建房间"), b -> openCreate())
            .bounds(bx, footY, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left + W - 58, footY, 48, 18).build());

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
            if (actionable && mouseX >= ax && mouseX <= ax + ACTION_W && mouseY >= ay && mouseY <= ay + ah) {
                doRoomAction(room);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        FlatTheme.Fade fade = FlatTheme.fadeIn(openedAtMs);
        int panelTop = top + fade.offsetY();
        FlatTheme.panel(gg, left, panelTop, W, H, fade.alpha());
        FlatTheme.titleBar(gg, left, panelTop, W, 24);

        gg.drawCenteredString(font, "游戏浏览器", left + W / 2, panelTop + 9, FlatTheme.TEXT_HEADER);
        gg.drawString(font, "§7房间与进行中的对局", left + 12, panelTop + 29, FlatTheme.TEXT_DIM, false);

        List<RoomDto> list = rooms();
        if (list.isEmpty()) {
                gg.drawCenteredString(font, "§7暂无房间，点击下方创建",
                    left + W / 2, listY + viewportH / 2 - 4, FlatTheme.TEXT_DIM);
        } else {
            gg.enableScissor(listX, listY, listX + listW, listY + viewportH);
            for (int i = 0; i < list.size(); i++) {
                if (!isRowVisible(i)) {
                    continue;
                }
                renderRoomRow(gg, list.get(i), rowY(i), mouseX, mouseY);
            }
            gg.disableScissor();
            renderScrollbar(gg);
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void renderRoomRow(GuiGraphics gg, RoomDto room, int y, int mouseX, int mouseY) {
        int ax = listX + listW - ACTION_W - 4;
        int ay = y + 8;
        int ah = ROW_H - 8;
        boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + ROW_H - 2;
        FlatTheme.card(gg, listX, y, listW, ROW_H - 2, room.youAreMember() || hovered);

        String status = room.inProgress() ? " §a[进行中 " + formatClock(room.elapsedSeconds()) + "]" : "";
        gg.drawString(font, "§f" + room.modeName() + " §8· §7" + room.targetText() + status,
                listX + 4, y + 4, FlatTheme.TEXT, false);
        gg.drawString(font, "§8战场 §7" + room.arenaId() + "  §8房主 §7" + room.hostName(),
                listX + 4, y + 14, FlatTheme.TEXT_DIM, false);
        String players = room.playersText().isBlank() ? "-" : trim(room.playersText(), listW - ACTION_W - 76);
        gg.drawString(font, "§8玩家 §7" + players, listX + 4, y + 24, FlatTheme.TEXT_DIM, false);

        String count = room.size() + "/" + room.capacity();
        int countColor = room.isFull() ? FlatTheme.DANGER : FlatTheme.SUCCESS;
        int countX = ax - font.width(count) - 8;
        FlatTheme.progress(gg, countX - 30, y + 25, 48, 3,
            room.capacity() <= 0 ? 0f : room.size() / (float) room.capacity(), countColor);
        gg.drawString(font, count, countX, y + 13, countColor, false);

        if (room.inProgress()) {
            FlatTheme.chip(gg, listX + listW - ACTION_W - 72, y + 4, 44, 10, FlatTheme.SUCCESS);
            gg.drawCenteredString(font, "进行中", listX + listW - ACTION_W - 50, y + 5, FlatTheme.TEXT);
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
        drawActionButton(gg, ax, ay, ACTION_W, ah, label, color, enabled && buttonHovered);
    }

    private void drawActionButton(GuiGraphics gg, int x, int y, int w, int h, String label, int color, boolean hovered) {
        FlatTheme.chip(gg, x, y, w, h, hovered ? FlatTheme.ACCENT : color);
        gg.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    private void renderScrollbar(GuiGraphics gg) {
        if (maxScrollRow() <= 0) {
            return;
        }
        int trackX = listX + listW - 2;
        int trackY = listY;
        int trackH = viewportH;
        gg.fill(trackX, trackY, trackX + 2, trackY + trackH, FlatTheme.SURFACE_DISABLED);

        int total = totalRows();
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / total);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * scrollRow / maxScrollRow());
        gg.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, FlatTheme.ACCENT);
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
