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
 * 像素风"游戏浏览器"界面（客户端）：浏览当前可加入的房间，一键加入/离开；管理员可创建房间。
 *
 * <p>房间数据来自 {@link ClientRoomList}（由 {@code SyncRoomListPacket} 下发），界面打开后每 2 秒
 * 自动向服务端请求一次最新列表。列表区支持滚轮滚动，避免房间过多撑大界面。
 */
public final class RoomBrowserScreen extends Screen {

    private static final int W = 320;
    private static final int H = 200;
    private static final int ROW_H = 26;
    private static final int VISIBLE_ROWS = 5;
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
        if (ClientRoomList.canManage()) {
            addRenderableWidget(Button.builder(Component.literal("§a创建房间"), b -> openCreate())
                    .bounds(bx, footY, 80, 18).build());
        }
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
        if (room.youAreMember()) {
            minecraft.player.connection.sendCommand("arcade room leave");
        } else {
            minecraft.player.connection.sendCommand("arcade room join " + room.roomId());
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, W, H);

        gg.drawCenteredString(font, "§l游戏浏览器", left + W / 2, top + 12, PixelTheme.ACCENT);
        gg.drawString(font, "§7当前可加入的房间", left + 12, top + 28, PixelTheme.TEXT_DIM, false);

        List<RoomDto> list = rooms();
        if (list.isEmpty()) {
            gg.drawCenteredString(font, "§7暂无房间" + (ClientRoomList.canManage() ? "，点击下方创建" : "，请稍候"),
                    left + W / 2, listY + viewportH / 2 - 4, PixelTheme.TEXT_DIM);
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
        PixelTheme.row(gg, listX, y, listW, ROW_H - 2, room.youAreMember());

        String status = room.inProgress() ? " §a[进行中]" : "";
        gg.drawString(font, "§f" + room.modeName() + " §8· §7" + room.targetText() + status,
                listX + 4, y + 4, PixelTheme.TEXT, false);
        gg.drawString(font, "§8战场 §7" + room.arenaId() + "  §8房主 §7" + room.hostName(),
                listX + 4, y + 14, PixelTheme.TEXT_DIM, false);

        String count = room.size() + "/" + room.capacity();
        int countColor = room.isFull() ? 0xFFCF6A5A : PixelTheme.TEXT;
        gg.drawString(font, count, listX + listW - ACTION_W - 4 - font.width(count) - 6, y + 9, countColor, false);

        // 操作按钮
        int ax = listX + listW - ACTION_W - 4;
        int ay = y + 4;
        int ah = ROW_H - 8;
        String label;
        int color;
        boolean hovered = mouseX >= ax && mouseX <= ax + ACTION_W && mouseY >= ay && mouseY <= ay + ah;
        if (room.youAreMember()) {
            label = "离开";
            color = 0xFFE0C060;
        } else if (room.isFull()) {
            label = "已满";
            color = PixelTheme.TEXT_DIM;
        } else {
            label = "加入";
            color = PixelTheme.ACCENT;
        }
        boolean enabled = room.youAreMember() || !room.isFull();
        drawActionButton(gg, ax, ay, ACTION_W, ah, label, color, enabled && hovered);
    }

    private void drawActionButton(GuiGraphics gg, int x, int y, int w, int h, String label, int color, boolean hovered) {
        gg.fill(x, y, x + w, y + h, hovered ? 0x55FFFFFF : 0x40000000);
        gg.fill(x, y, x + w, y + 1, PixelTheme.BEVEL_LIGHT);
        gg.fill(x, y + h - 1, x + w, y + h, PixelTheme.BEVEL_SHADOW);
        gg.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    private void renderScrollbar(GuiGraphics gg) {
        if (maxScrollRow() <= 0) {
            return;
        }
        int trackX = listX + listW - 2;
        int trackY = listY;
        int trackH = viewportH;
        gg.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x40000000);

        int total = totalRows();
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / total);
        int range = trackH - thumbH;
        int thumbY = trackY + (range * scrollRow / maxScrollRow());
        gg.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, PixelTheme.BEVEL_LIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
