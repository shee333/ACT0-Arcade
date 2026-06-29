package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.shee33.act0.arcade.mode.RandomWeaponMode;

/** 创建房间的二级“对局设定”界面。 */
public final class MatchSettingsScreen extends Screen {
    private static final int W = 300;
    private static final int H = 238;
    private static final int ROW_H = 22;

    private final CreateRoomScreen parent;
    private int left;
    private int top;
    private int backX, backY, backW, backH;

    public MatchSettingsScreen(CreateRoomScreen parent) {
        super(Component.literal("对局设定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        backW = 76;
        backH = 20;
        backX = left + W / 2 - backW / 2;
        backY = top + H - 28;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, W, H);
        PixelTheme.titleBar(gg, left + 1, top + 1, W - 2, 24);
        gg.drawCenteredString(font, "§l对局设定", left + W / 2, top + 9, PixelTheme.TEXT);
        gg.drawString(font, "§7仅影响本房间", left + 14, top + 32, PixelTheme.TEXT_DIM, false);

        int y = top + 48;
        renderRow(gg, mouseX, mouseY, 0, y, "玩家血量", healthText());
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 1, y, "武器规则", parent.settingsRandomMode().enabled() ? parent.settingsRandomMode().displayName() : "使用配装");
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 2, y, "重生等待", parent.settingsRespawnDelaySeconds() + " 秒");
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 3, y, "友伤", parent.settingsFriendlyFire() ? "开启" : "关闭");
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 4, y, "补给箱", parent.settingsAmmoCrateChancePercent() + "%");
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 5, y, "热区轮换", parent.settingsHotZoneRotateSeconds() + " 秒");
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 6, y, "热区半径", String.format("%.0f 格", parent.settingsHotZoneRadius()));
        y += ROW_H;
        renderRow(gg, mouseX, mouseY, 7, y, "热区得分", parent.settingsHotZoneScorePerSecond() + " / 秒");

        renderButton(gg, backX, backY, backW, backH, "返回", mouseX, mouseY);
        super.render(gg, mouseX, mouseY, partialTick);
    }

    private String healthText() {
        double health = parent.settingsHealthOverride();
        return health <= 0.0D ? "默认" : Integer.toString((int) Math.round(health));
    }

    private void renderRow(GuiGraphics gg, int mouseX, int mouseY, int index, int y, String label, String value) {
        int x = left + 14;
        int w = W - 28;
        boolean hovered = mouseY >= y && mouseY < y + 18 && mouseX >= x && mouseX < x + w;
        PixelTheme.card(gg, x, y, w, 18, hovered);
        gg.drawString(font, "§7" + label, x + 7, y + 5, PixelTheme.TEXT_DIM, false);
        gg.drawCenteredString(font, "◀", x + w - 72, y + 5, PixelTheme.ACCENT);
        gg.drawCenteredString(font, value, x + w - 42, y + 5, PixelTheme.TEXT);
        gg.drawCenteredString(font, "▶", x + w - 10, y + 5, PixelTheme.ACCENT);
    }

    private void renderButton(GuiGraphics gg, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = inRect(mouseX, mouseY, x, y, w, h);
        PixelTheme.card(gg, x, y, w, h, hovered);
        gg.drawCenteredString(font, label, x + w / 2, y + 6, PixelTheme.TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inRect((int) mouseX, (int) mouseY, backX, backY, backW, backH)) {
            onClose();
            return true;
        }
        int y = top + 48;
        for (int i = 0; i < 8; i++) {
            if (inRect((int) mouseX, (int) mouseY, left + 14, y, W - 28, 18)) {
                int dir = mouseX < left + W - 42 ? -1 : 1;
                adjust(i, dir);
                return true;
            }
            y += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void adjust(int index, int dir) {
        switch (index) {
            case 0 -> parent.adjustSettingsHealth(dir);
            case 1 -> parent.adjustSettingsRandomMode(dir);
            case 2 -> parent.adjustSettingsRespawn(dir);
            case 3 -> parent.toggleSettingsFriendlyFire();
            case 4 -> parent.adjustSettingsAmmoCrate(dir);
            case 5 -> parent.adjustSettingsHotZoneRotate(dir);
            case 6 -> parent.adjustSettingsHotZoneRadius(dir);
            case 7 -> parent.adjustSettingsHotZoneScore(dir);
            default -> {
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
