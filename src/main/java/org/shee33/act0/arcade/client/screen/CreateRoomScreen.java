package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.shee33.act0.arcade.client.ClientRoomList;
import org.shee33.act0.arcade.match.RoomManager;

import java.util.List;

/**
 * 创建房间界面：选择模式、竞技场与计分目标，提交 {@code /arcade room create}。
 *
 * <p>计分目标随模式语义切换——回合制模式为"先到几胜"，击杀制模式为"先到几杀"。
 */
public final class CreateRoomScreen extends Screen {

    private static final int W = 280;
    private static final int H = 278;

    /** 模式定义：id、显示名、是否击杀制、默认目标、目标下限、目标上限、步长。 */
    private record ModeDef(String id, String name, boolean killBased,
                           int defTarget, int minTarget, int maxTarget, int step) {
    }

    private static final List<ModeDef> MODES = List.of(
            new ModeDef("duel_1v1", "单挑 1v1", false, 3, 1, 15, 1),
            new ModeDef("duel_2v2", "2v2", false, 3, 1, 15, 1),
            new ModeDef("team_deathmatch", "团队死斗", true, 30, 5, 99, 5),
            new ModeDef("free_for_all", "个人乱斗", true, 20, 5, 99, 5));

    private final RoomBrowserScreen parent;

    private int left;
    private int top;
    private int selectedMode;
    private int selectedArena;
    private int target = MODES.get(0).defTarget();
    /** 限时（秒）；0 表示不限时。 */
    private int timeLimitSeconds = 0;
    /** 是否随机武器。 */
    private boolean randomWeapons = false;
    private int capacity = 2;
    private Button randomToggle;

    public CreateRoomScreen(RoomBrowserScreen parent) {
        super(Component.literal("创建房间"));
        this.parent = parent;
    }

    private ModeDef mode() {
        return MODES.get(selectedMode);
    }

    private int defaultCapacity() {
        return RoomManager.capacityFor(mode().id());
    }

    private boolean capacityEditable() {
        return mode().killBased();
    }

    private List<String> arenas() {
        return ClientRoomList.arenaIds();
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;

        // 左栏：模式
        int mx = left + 12;
        int my = top + 40;
        for (int i = 0; i < MODES.size(); i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(MODES.get(i).name()), b -> selectMode(idx))
                    .bounds(mx, my, 110, 18).build());
            my += 22;
        }

        int rx = left + 140;

        // 竞技场切换
        int arenaY = top + 56;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleArena(-1))
                .bounds(rx, arenaY, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleArena(1))
                .bounds(rx + 110, arenaY, 18, 18).build());

        // 目标调整
        int targetY = top + 96;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustTarget(-1))
                .bounds(rx, targetY, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustTarget(1))
                .bounds(rx + 110, targetY, 18, 18).build());

        // 限时调整（分钟，0=不限时）
        int timeY = top + 136;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustTime(-1))
                .bounds(rx, timeY, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustTime(1))
                .bounds(rx + 110, timeY, 18, 18).build());

        // 人数调整（击杀制可改，回合制固定）
        int capY = top + 168;
        Button capMinus = Button.builder(Component.literal("-"), b -> adjustCapacity(-1))
            .bounds(rx, capY, 18, 18).build();
        capMinus.active = capacityEditable();
        addRenderableWidget(capMinus);
        Button capPlus = Button.builder(Component.literal("+"), b -> adjustCapacity(1))
            .bounds(rx + 110, capY, 18, 18).build();
        capPlus.active = capacityEditable();
        addRenderableWidget(capPlus);

        // 随机武器开关
        int randomY = top + 200;
        randomToggle = Button.builder(randomLabel(), b -> toggleRandom())
                .bounds(rx, randomY, 128, 18).build();
        addRenderableWidget(randomToggle);

        // 底部：创建 / 返回
        int footY = top + H - 26;
        Button create = Button.builder(Component.literal("§a创建"), b -> create())
                .bounds(rx, footY, 80, 18).build();
        create.active = !arenas().isEmpty();
        addRenderableWidget(create);
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                .bounds(left + 12, footY, 60, 18).build());
    }

    private Component randomLabel() {
        return Component.literal(randomWeapons ? "§a随机武器: 开" : "§7随机武器: 关");
    }

    private void toggleRandom() {
        randomWeapons = !randomWeapons;
        if (randomToggle != null) {
            randomToggle.setMessage(randomLabel());
        }
    }

    private void adjustTime(int dir) {
        // 60 秒步进，0~3600 秒（0=不限时）
        timeLimitSeconds = Math.max(0, Math.min(3600, timeLimitSeconds + dir * 60));
    }

    private void adjustCapacity(int dir) {
        if (!capacityEditable()) {
            capacity = defaultCapacity();
            return;
        }
        int step = "team_deathmatch".equals(mode().id()) ? 2 : 1;
        capacity = RoomManager.normalizeCapacity(mode().id(), capacity + dir * step);
    }

    private void selectMode(int idx) {
        selectedMode = idx;
        target = mode().defTarget();
        capacity = defaultCapacity();
    }

    private void cycleArena(int dir) {
        if (arenas().isEmpty()) {
            return;
        }
        selectedArena = Math.floorMod(selectedArena + dir, arenas().size());
    }

    private void adjustTarget(int dir) {
        ModeDef m = mode();
        target = Math.max(m.minTarget(), Math.min(m.maxTarget(), target + dir * m.step()));
    }

    private void create() {
        if (minecraft == null || minecraft.player == null || arenas().isEmpty()) {
            return;
        }
        String arena = arenas().get(Math.min(selectedArena, arenas().size() - 1));
        int cap = capacityEditable() ? capacity : defaultCapacity();
        minecraft.player.connection.sendCommand(
                "arcade room create " + mode().id() + " " + arena + " " + target
                + " " + timeLimitSeconds + " " + randomWeapons + " " + cap);
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        PixelTheme.panel(gg, left, top, W, H);

        gg.drawCenteredString(font, "§l创建房间", left + W / 2, top + 12, PixelTheme.ACCENT);
        gg.drawString(font, "§7选择模式", left + 12, top + 28, PixelTheme.TEXT_DIM, false);

        // 左栏选中高亮
        int hy = top + 40 + selectedMode * 22;
        PixelTheme.row(gg, left + 10, hy - 1, 114, 20, true);

        int rx = left + 140;
        ModeDef m = mode();

        // 竞技场
        gg.drawString(font, "§7竞技场", rx, top + 44, PixelTheme.TEXT_DIM, false);
        String arenaText = arenas().isEmpty()
                ? "§c暂无战场"
                : "§e" + arenas().get(Math.min(selectedArena, arenas().size() - 1))
                        + " §7(" + (selectedArena + 1) + "/" + arenas().size() + ")";
        gg.drawCenteredString(font, arenaText, rx + 64, top + 61, PixelTheme.TEXT);

        // 目标
        String unit = m.killBased() ? "杀" : "胜";
        gg.drawString(font, "§7计分目标", rx, top + 84, PixelTheme.TEXT_DIM, false);
        gg.drawCenteredString(font, "§f先到 §e" + target + " " + unit, rx + 64, top + 101, PixelTheme.TEXT);

        // 限时
        gg.drawString(font, "§7对局限时", rx, top + 124, PixelTheme.TEXT_DIM, false);
        String timeText = timeLimitSeconds <= 0
                ? "§7不限时"
                : "§e" + (timeLimitSeconds / 60) + " §7分钟";
        gg.drawCenteredString(font, timeText, rx + 64, top + 141, PixelTheme.TEXT);

        gg.drawString(font, "§7房间人数", rx, top + 156, PixelTheme.TEXT_DIM, false);
        String capText = capacityEditable()
            ? "§e" + capacity + " §7人"
            : "§7固定 §e" + defaultCapacity() + " §7人";
        gg.drawCenteredString(font, capText, rx + 64, top + 173, PixelTheme.TEXT);

        gg.drawString(font, "§8满员将自动开局，房主也可提前开始", rx, top + 228, PixelTheme.TEXT_DIM, false);
        gg.drawString(font, "§8限时到则领先者胜，平分则平局", rx, top + 240, PixelTheme.TEXT_DIM, false);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
