package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.shee33.act0.arcade.client.ClientRoomList;
import org.shee33.act0.arcade.match.RoomManager;
import org.shee33.act0.arcade.mode.CreateRoomModeCatalog;
import org.shee33.act0.arcade.mode.RandomWeaponMode;

import java.util.List;

/**
 * 创建房间界面 —— 视觉与动效严格对照《创建房间菜单动效规格文档》重写。
 *
 * <p>抛弃原版 {@code Button} 组件，改为手动命中检测 + 自绘（{@link #mouseClicked}/{@link #render}），
 * 因为滑动指示器/数值滚轮/扫光等效果原版 Button 画不出来。所有坐标锚点（{@code left+12}、
 * {@code rx=left+140}、{@code footY=top+H-26} 等）与原实现保持一致，只替换绘制样式并叠加三类动效
 * （开启级联 / 选中联动 / 点击反馈），均由 {@link CreateRoomAnimator} 持有的 {@link MenuTween.Anim}
 * 驱动，时间源为 {@link MenuTween#now()}。
 */
public final class CreateRoomScreen extends Screen {

    private static final int W = 280;
    private static final int H = 278;

    // ============================================================
    // 配色（规格文档 §2.2，按需用 FlatTheme.withAlpha 叠加透明度）
    // ============================================================
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT_BASE = 0xFFE8EDF2;
    private static final int GOLD = 0xFFFFD76A;
    private static final int GOLD_TEXT = 0xFF14181D;
    private static final int GREEN = 0xFF7EE2A8;
    private static final int PANEL_BG = 0xFF1A1F26;
    private static final int OVERLAY_BLACK = 0xFF000000;

    // 按压回弹数组索引（对应 CreateRoomAnimator.press[]，共 11 个可按压控件）
    private static final int P_SCORE_MINUS = 0;
    private static final int P_SCORE_PLUS = 1;
    private static final int P_TIME_MINUS = 2;
    private static final int P_TIME_PLUS = 3;
    private static final int P_PLAYERS_MINUS = 4;
    private static final int P_PLAYERS_PLUS = 5;
    private static final int P_ARENA_LEFT = 6;
    private static final int P_ARENA_RIGHT = 7;
    private static final int P_SETTINGS = 8;
    private static final int P_BACK = 9;
    private static final int P_CREATE = 10;

    private final RoomBrowserScreen parent;
    private CreateRoomAnimator anim;
    /** 是否已经完整 init() 过一次：resize/GUI Scale 变更或从 MatchSettingsScreen 返回都会重跑
     *  {@code init()}，此标记确保模型状态（人数/动画实例）只在真正首次打开时重置一次。 */
    private boolean initialized;

    private int left;
    private int top;

    // 布局锚点（与原实现完全一致）
    private int modeListX;
    private int modeListY;
    private int rx;
    private int arenaY;
    private int targetY;
    private int timeY;
    private int capY;
    private int randomY;
    private int settingsY;
    private int footY;

    // ============================================================
    // 模型状态
    // ============================================================
    private int selectedMode;
    private int selectedArena;
    private int target = CreateRoomModeCatalog.MODES.get(0).defTarget();
    private int timeLimitSeconds;
    private RandomWeaponMode randomMode = RandomWeaponMode.OFF;
    private int capacity = 2;
    private boolean playersVisible;

    private double settingsHealthOverride = 0.0D;
    private int settingsRespawnDelaySeconds = 3;
    private boolean settingsFriendlyFire = false;
    private int settingsAmmoCrateChancePercent = 25;
    private int settingsHotZoneRotateSeconds = 60;
    private double settingsHotZoneRadius = 6.0D;
    private int settingsHotZoneScorePerSecond = 1;

    // ============================================================
    // 选中/点击动效的过渡状态（哪条旧文案在滑出、方向）
    // ============================================================
    private String scoreOldText = "";
    private int scoreDir = 1;
    private MenuTween.Anim scoreRollAnim;

    private String timeOldText = "";
    private int timeDir = 1;

    private String playersOldText = "";
    private int playersDir = 1;

    private String arenaOldText = "";
    private int arenaDir = 1;

    // 每个模式列表项的悬停方向：true=正在进入(padding 6→10)，false=正在离开(10→6)
    private final boolean[] hoverEntering = new boolean[CreateRoomModeCatalog.MODES.size()];
    private final boolean[] hoveredPrev = new boolean[CreateRoomModeCatalog.MODES.size()];

    // 滑动指示器 Y 边界（配合 anim.indicatorY 做 lerp）
    private int indicatorFromY;
    private int indicatorToY;

    // 创建按钮三连状态机
    private boolean createArmed;
    private boolean createConfirmed;

    // 次级操作（返回/对局设定）：按压回弹播完才真正跳转，回弹才有机会被看到
    private int pendingNav;
    private static final int NAV_NONE = 0;
    private static final int NAV_SETTINGS = 1;
    private static final int NAV_BACK = 2;

    public CreateRoomScreen(RoomBrowserScreen parent) {
        super(Component.literal("创建房间"));
        this.parent = parent;
    }

    private CreateRoomModeCatalog.ModeDef mode() {
        return CreateRoomModeCatalog.MODES.get(selectedMode);
    }

    private int defaultCapacity() {
        return RoomManager.capacityFor(mode().id());
    }

    private boolean capacityEditable() {
        return mode().killBased() || "hot_zone".equals(mode().id()) || "jump_sniper".equals(mode().id())
                || "arms_race".equals(mode().id()) || "team_arms_race".equals(mode().id());
    }

    private int capacityStep() {
        String id = mode().id();
        return ("team_deathmatch".equals(id) || "hot_zone".equals(id)
                || "jump_sniper".equals(id) || "team_arms_race".equals(id)) ? 2 : 1;
    }

    private List<String> arenas() {
        return ClientRoomList.arenaIds();
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;

        modeListX = left + 12;
        modeListY = top + 40;
        rx = left + 140;
        arenaY = top + 56;
        targetY = top + 96;
        timeY = top + 136;
        capY = top + 168;
        randomY = top + 200;
        settingsY = Math.min(top + H - 56, modeListY + CreateRoomModeCatalog.MODES.size() * 22 + 6);
        footY = top + H - 26;

        if (!initialized) {
            initialized = true;
            playersVisible = capacityEditable();
            capacity = defaultCapacity();
            anim = new CreateRoomAnimator(MenuTween.now(), CreateRoomModeCatalog.MODES.size());
        }
    }

    // ============================================================
    // 布局几何辅助
    // ============================================================

    private int itemTop(int idx) {
        return modeListY + idx * 22 - 1;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int withAlpha(int color, float alpha) {
        return FlatTheme.withAlpha(color, alpha);
    }

    private static int lerpColor(int c1, int c2, float t) {
        float tc = CreateRoomAnimator.clamp01(t);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * tc);
        int g = Math.round(g1 + (g2 - g1) * tc);
        int b = Math.round(b1 + (b2 - b1) * tc);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ============================================================
    // 文案格式化
    // ============================================================

    private static String scoreText(int value, String unit) {
        return "先到 " + value + " " + unit;
    }

    private String timeText() {
        return timeLimitSeconds <= 0 ? "不限时" : (timeLimitSeconds / 60) + " 分钟";
    }

    private String playersText() {
        return capacity + " 人";
    }

    private String arenaText() {
        List<String> a = arenas();
        if (a.isEmpty()) {
            return "暂无可用战场";
        }
        int idx = Math.min(selectedArena, a.size() - 1);
        return a.get(idx) + " (" + (idx + 1) + "/" + a.size() + ")";
    }

    private String weaponText() {
        if ("jump_sniper".equals(mode().id())) {
            return "跳狙固定: 狙击+近战";
        }
        return randomMode.enabled() ? "随机武器: " + randomMode.displayName() : "随机武器: 关";
    }

    // ============================================================
    // 交互：模式选择（§4.1 唯一滑动指示器 + §4.2 右栏联动）
    // ============================================================

    private void onSelectMode(int idx) {
        if (idx == selectedMode) {
            return;
        }
        long now = MenuTween.now();
        int dir = CreateRoomAnimator.scrollDir(selectedMode, idx);

        indicatorFromY = itemTop(selectedMode);
        indicatorToY = itemTop(idx);
        anim.indicatorY.start(now);

        CreateRoomModeCatalog.ModeDef oldMode = mode();
        String oldScoreText = scoreText(target, oldMode.targetUnit());

        selectedMode = idx;
        CreateRoomModeCatalog.ModeDef m = mode();
        target = m.defTarget();
        scoreOldText = oldScoreText;
        scoreDir = dir;
        scoreRollAnim = anim.scoreRoll;
        scoreRollAnim.start(now);

        boolean newShow = capacityEditable();
        if (newShow != playersVisible) {
            anim.playersExpand.start(now);
        }
        playersVisible = newShow;
        capacity = defaultCapacity();

        if ("jump_sniper".equals(m.id())) {
            randomMode = RandomWeaponMode.SNIPER;
        }
        playClick();
    }

    // ============================================================
    // 交互：数值调整 / 竞技场切换（§5，边界静默——静默的分支提前 return，不播音效/不播动画）
    // ============================================================

    private void onScoreAdjust(int dir) {
        CreateRoomModeCatalog.ModeDef m = mode();
        int next = target + dir * m.step();
        if (next < m.minTarget() || next > m.maxTarget()) {
            return;
        }
        long now = MenuTween.now();
        scoreOldText = scoreText(target, m.targetUnit());
        target = next;
        scoreDir = dir;
        scoreRollAnim = anim.scoreTimePlayersRoll[0];
        scoreRollAnim.start(now);
        anim.press[dir > 0 ? P_SCORE_PLUS : P_SCORE_MINUS].start(now);
        playClick();
    }

    private void onTimeAdjust(int dir) {
        int next = timeLimitSeconds + dir * 60;
        if (next < 0 || next > 3600) {
            return;
        }
        long now = MenuTween.now();
        timeOldText = timeText();
        timeLimitSeconds = next;
        timeDir = dir;
        anim.scoreTimePlayersRoll[1].start(now);
        anim.press[dir > 0 ? P_TIME_PLUS : P_TIME_MINUS].start(now);
        playClick();
    }

    private void onPlayersAdjust(int dir) {
        if (!capacityEditable()) {
            return;
        }
        int step = capacityStep();
        int next = RoomManager.normalizeCapacity(mode().id(), capacity + dir * step);
        if (next == capacity) {
            return;
        }
        long now = MenuTween.now();
        playersOldText = playersText();
        capacity = next;
        playersDir = dir;
        anim.scoreTimePlayersRoll[2].start(now);
        anim.press[dir > 0 ? P_PLAYERS_PLUS : P_PLAYERS_MINUS].start(now);
        playClick();
    }

    private void onArenaAdjust(int dir) {
        List<String> a = arenas();
        if (a.size() <= 1) {
            return;
        }
        long now = MenuTween.now();
        arenaOldText = arenaText();
        selectedArena = CreateRoomAnimator.wrapIndex(selectedArena, dir, a.size());
        arenaDir = dir;
        anim.arenaSlide.start(now);
        anim.press[dir > 0 ? P_ARENA_RIGHT : P_ARENA_LEFT].start(now);
        playClick();
    }

    private void onWeaponClicked() {
        if ("jump_sniper".equals(mode().id())) {
            return;
        }
        RandomWeaponMode[] modes = RandomWeaponMode.values();
        randomMode = modes[Math.floorMod(randomMode.ordinal() + 1, modes.length)];
        anim.weaponSweep.start(MenuTween.now());
        playClick();
    }

    private void onSettingsClicked() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        anim.press[P_SETTINGS].start(MenuTween.now());
        pendingNav = NAV_SETTINGS;
        playClick();
    }

    private void onBackClicked() {
        if (pendingNav != NAV_NONE) {
            return;
        }
        anim.press[P_BACK].start(MenuTween.now());
        pendingNav = NAV_BACK;
        playClick();
    }

    private void onCreateClicked() {
        if (createArmed || minecraft == null || minecraft.player == null) {
            return;
        }
        List<String> a = arenas();
        if (a.isEmpty()) {
            return;
        }
        sendCreateCommand(a);
        long now = MenuTween.now();
        createArmed = true;
        createConfirmed = false;
        anim.press[P_CREATE].start(now);
        anim.createSweep.start(now);
        anim.createColorShift.start(now);
        playClick();
    }

    /**
     * 只发 {@code arcade room create}——{@code arcade browse} 故意不在这里发：服务端处理
     * browse 只需约一个 tick(~50ms)就会把界面切成 {@link RoomLobbyScreen}，而创建按钮的三连
     * 反馈(扫光 350ms + 变绿 150ms + "✓已创建" 展示 1100ms)加起来接近 1.6s，若在此处同步发出
     * browse，动画播到一半界面就被换掉，"✓已创建"和还原逻辑永远够不到执行时机。真正的
     * {@code arcade browse} 挪到 {@link #render} 里 {@code createRevert.isDone} 那个分支，
     * 紧跟在 {@link #onClose()} 之前发出，让玩家先看完确认反馈。
     */
    private void sendCreateCommand(List<String> arenaSnapshot) {
        String arena = arenaSnapshot.get(Math.min(selectedArena, arenaSnapshot.size() - 1));
        int cap = capacityEditable() ? capacity : defaultCapacity();
        RandomWeaponMode submitRandom = "jump_sniper".equals(mode().id()) ? RandomWeaponMode.SNIPER : randomMode;
        minecraft.player.connection.sendCommand(
                "arcade room create " + mode().id() + " " + CreateRoomModeCatalog.quoteArg(arena) + " " + target
                        + " " + timeLimitSeconds + " " + submitRandom.id() + " " + cap
                        + " " + (int) Math.round(settingsHealthOverride)
                        + " " + settingsRespawnDelaySeconds
                        + " " + (settingsFriendlyFire ? "true" : "false")
                        + " " + settingsAmmoCrateChancePercent
                        + " " + settingsHotZoneRotateSeconds
                        + " " + settingsHotZoneRadius
                        + " " + settingsHotZoneScorePerSecond);
    }

    /** 手动命中检测取代原版 Button 后丢失的点击音效反馈；边界静默的调整不经过这里。 */
    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ============================================================
    // MatchSettingsScreen 依赖的 accessor（签名不可变）
    // ============================================================

    double settingsHealthOverride() {
        return settingsHealthOverride;
    }

    RandomWeaponMode settingsRandomMode() {
        return randomMode;
    }

    int settingsRespawnDelaySeconds() {
        return settingsRespawnDelaySeconds;
    }

    boolean settingsFriendlyFire() {
        return settingsFriendlyFire;
    }

    int settingsAmmoCrateChancePercent() {
        return settingsAmmoCrateChancePercent;
    }

    int settingsHotZoneRotateSeconds() {
        return settingsHotZoneRotateSeconds;
    }

    double settingsHotZoneRadius() {
        return settingsHotZoneRadius;
    }

    int settingsHotZoneScorePerSecond() {
        return settingsHotZoneScorePerSecond;
    }

    void adjustSettingsHealth(int dir) {
        double[] values = {0, 20, 40, 60, 100, 150, 200};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsHealthOverride);
        settingsHealthOverride = values[Math.floorMod(idx + dir, values.length)];
    }

    void adjustSettingsRandomMode(int dir) {
        if ("jump_sniper".equals(mode().id())) {
            randomMode = RandomWeaponMode.SNIPER;
            return;
        }
        RandomWeaponMode[] modes = RandomWeaponMode.values();
        randomMode = modes[Math.floorMod(randomMode.ordinal() + dir, modes.length)];
    }

    void adjustSettingsRespawn(int dir) {
        int[] values = {0, 3, 5, 8, 10};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsRespawnDelaySeconds);
        settingsRespawnDelaySeconds = values[Math.floorMod(idx + dir, values.length)];
    }

    void toggleSettingsFriendlyFire() {
        settingsFriendlyFire = !settingsFriendlyFire;
    }

    void adjustSettingsAmmoCrate(int dir) {
        int[] values = {0, 25, 50, 100};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsAmmoCrateChancePercent);
        settingsAmmoCrateChancePercent = values[Math.floorMod(idx + dir, values.length)];
    }

    void adjustSettingsHotZoneRotate(int dir) {
        int[] values = {30, 45, 60, 90, 120};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsHotZoneRotateSeconds);
        settingsHotZoneRotateSeconds = values[Math.floorMod(idx + dir, values.length)];
    }

    void adjustSettingsHotZoneRadius(int dir) {
        double[] values = {4, 6, 8, 10, 12};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsHotZoneRadius);
        settingsHotZoneRadius = values[Math.floorMod(idx + dir, values.length)];
    }

    void adjustSettingsHotZoneScore(int dir) {
        int[] values = {1, 2, 3};
        int idx = CreateRoomModeCatalog.nearestIndex(values, settingsHotZoneScorePerSecond);
        settingsHotZoneScorePerSecond = values[Math.floorMod(idx + dir, values.length)];
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // ============================================================
    // 鼠标输入：手动命中检测
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (int i = 0; i < CreateRoomModeCatalog.MODES.size(); i++) {
            if (inRect(mouseX, mouseY, modeListX - 2, itemTop(i), 114, 20)) {
                onSelectMode(i);
                return true;
            }
        }
        if (inRect(mouseX, mouseY, modeListX, settingsY, 110, 18)) {
            onSettingsClicked();
            return true;
        }

        if (inRect(mouseX, mouseY, rx, arenaY, 18, 18)) {
            onArenaAdjust(-1);
            return true;
        }
        if (inRect(mouseX, mouseY, rx + 110, arenaY, 18, 18)) {
            onArenaAdjust(1);
            return true;
        }
        if (inRect(mouseX, mouseY, rx, targetY, 18, 18)) {
            onScoreAdjust(-1);
            return true;
        }
        if (inRect(mouseX, mouseY, rx + 110, targetY, 18, 18)) {
            onScoreAdjust(1);
            return true;
        }
        if (inRect(mouseX, mouseY, rx, timeY, 18, 18)) {
            onTimeAdjust(-1);
            return true;
        }
        if (inRect(mouseX, mouseY, rx + 110, timeY, 18, 18)) {
            onTimeAdjust(1);
            return true;
        }
        if (playersVisible) {
            if (inRect(mouseX, mouseY, rx, capY, 18, 18)) {
                onPlayersAdjust(-1);
                return true;
            }
            if (inRect(mouseX, mouseY, rx + 110, capY, 18, 18)) {
                onPlayersAdjust(1);
                return true;
            }
        }
        if (inRect(mouseX, mouseY, rx, randomY, 128, 18)) {
            onWeaponClicked();
            return true;
        }

        if (inRect(mouseX, mouseY, modeListX, footY, 60, 18)) {
            onBackClicked();
            return true;
        }
        if (inRect(mouseX, mouseY, rx, footY, 80, 18) && !arenas().isEmpty()) {
            onCreateClicked();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();

        if (pendingNav != NAV_NONE) {
            boolean ready = pendingNav == NAV_SETTINGS ? anim.press[P_SETTINGS].isDone(now) : anim.press[P_BACK].isDone(now);
            if (ready) {
                int nav = pendingNav;
                pendingNav = NAV_NONE;
                if (nav == NAV_SETTINGS) {
                    if (minecraft != null) {
                        minecraft.setScreen(new MatchSettingsScreen(this));
                    }
                } else {
                    onClose();
                }
                return;
            }
        }
        if (createArmed && createConfirmed && anim.createRevert.isDone(now)) {
            createArmed = false;
            createConfirmed = false;
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand("arcade browse");
            }
            onClose();
            return;
        }

        renderBackground(gg);

        float overlayE = anim.backdrop.easedT(now, MenuTween.Ease.OUT_CUBIC);
        gg.fill(0, 0, width, height, withAlpha(OVERLAY_BLACK, 0.45f * overlayE));

        updateModeHover(mouseX, mouseY, now);

        float panelE = anim.panel.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int panelYOffset = Math.round(12 * (1 - panelE));
        float panelScale = 0.97f + 0.03f * panelE;

        gg.pose().pushPose();
        gg.pose().translate(0, panelYOffset, 0);
        float ccx = left + W / 2f;
        float ccy = top + H / 2f;
        gg.pose().translate(ccx, ccy, 0);
        gg.pose().scale(panelScale, panelScale, 1f);
        gg.pose().translate(-ccx, -ccy, 0);

        drawPanel(gg, mouseX, mouseY, now, panelE);

        gg.pose().popPose();

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void updateModeHover(int mouseX, int mouseY, long now) {
        for (int i = 0; i < CreateRoomModeCatalog.MODES.size(); i++) {
            // 选中项强制视为"未悬停"，但仍走同一套跟踪逻辑——否则一个正处于悬停完成态(padding
            // 10/alpha 0.9)的项被选中后，会因为提前 continue 跳过状态更新而永久卡在悬停态，
            // 再也无法收到"离开"事件把它补间回 6/0.55。
            boolean hoveredNow = i != selectedMode && inRect(mouseX, mouseY, modeListX - 2, itemTop(i), 114, 20);
            if (hoveredNow != hoveredPrev[i]) {
                hoveredPrev[i] = hoveredNow;
                hoverEntering[i] = hoveredNow;
                anim.hoverProgress[i].start(now);
            }
        }
    }

    private void drawPanel(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        int x2 = left + W;
        int y2 = top + H;
        gg.fill(left, top, x2, y2, withAlpha(PANEL_BG, panelAlpha));
        int border = withAlpha(WHITE, 0.07f * panelAlpha);
        gg.fill(left, top, x2, top + 1, border);
        gg.fill(left, y2 - 1, x2, y2, border);
        gg.fill(left, top, left + 1, y2, border);
        gg.fill(x2 - 1, top, x2, y2, border);

        drawHeader(gg, now, panelAlpha);

        int divider = withAlpha(WHITE, 0.06f * panelAlpha);
        gg.fill(left, top + 24, x2, top + 25, divider);
        gg.fill(left + 130, top + 25, left + 131, footY - 8, divider);
        gg.fill(left, footY - 8, x2, footY - 7, divider);

        drawLeftColumn(gg, mouseX, mouseY, now, panelAlpha);
        drawRightColumn(gg, mouseX, mouseY, now, panelAlpha);
        drawFooter(gg, mouseX, mouseY, now, panelAlpha);
    }

    private void drawHeader(GuiGraphics gg, long now, float panelAlpha) {
        gg.drawString(font, "创建房间", left + 12, top + 8, withAlpha(WHITE, panelAlpha), false);
        float lineE = anim.titleLine.easedT(now, MenuTween.Ease.OUT_EXPO);
        int lineW = Math.round(48 * lineE);
        if (lineW > 0) {
            gg.fill(left + 12, top + 20, left + 12 + lineW, top + 22, withAlpha(GOLD, panelAlpha));
        }
    }

    // ------------------------------------------------------------
    // 左栏：模式列表 + 唯一滑动指示器 + 对局设定
    // ------------------------------------------------------------

    private void drawLeftColumn(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float labelE = anim.leftColumn[0].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int labelX = left + 12 + Math.round(-12 * (1 - labelE));
        gg.drawString(font, "选择模式", labelX, top + 30, withAlpha(TEXT_BASE, 0.4f * labelE * panelAlpha), false);

        drawIndicator(gg, now, panelAlpha);

        List<CreateRoomModeCatalog.ModeDef> modes = CreateRoomModeCatalog.MODES;
        for (int i = 0; i < modes.size(); i++) {
            float e = anim.leftColumn[i + 1].easedT(now, MenuTween.Ease.OUT_CUBIC);
            int xOffset = Math.round(-12 * (1 - e));
            float alpha = e * panelAlpha;

            boolean selected = i == selectedMode;
            float padding;
            float textAlpha;
            if (selected) {
                padding = 6f;
                textAlpha = 1.0f;
            } else {
                float hoverE = anim.hoverProgress[i].easedT(now, MenuTween.Ease.OUT_CUBIC);
                CreateRoomAnimator.HoverState hs = CreateRoomAnimator.hoverState(hoverEntering[i], hoverE);
                padding = hs.padding();
                textAlpha = hs.textAlpha();
            }
            int textX = modeListX + xOffset + Math.round(padding);
            int textY = itemTop(i) + 6;
            int color = withAlpha(selected ? WHITE : TEXT_BASE, textAlpha * alpha);
            gg.drawString(font, modes.get(i).name(), textX, textY, color, false);
        }

        float settingsE = anim.leftColumn[modes.size() + 1].easedT(now, MenuTween.Ease.OUT_CUBIC);
        int settingsX = modeListX + Math.round(-12 * (1 - settingsE));
        boolean settingsHovered = pendingNav == NAV_NONE && inRect(mouseX, mouseY, modeListX, settingsY, 110, 18);
        float settingsPress = pressScale(P_SETTINGS, now);
        drawScaled(gg, modeListX + 55, settingsY + 9, settingsPress, () ->
                drawGhostButton(gg, settingsX, settingsY, 110, 18, "对局设定", settingsHovered, true, settingsE * panelAlpha));
    }

    private void drawIndicator(GuiGraphics gg, long now, float panelAlpha) {
        int x = modeListX - 2;
        int w = 114;
        int h = 20;
        int yTop;
        float stretch;
        if (anim.indicatorY.isRunning() && !anim.indicatorY.isDone(now)) {
            float e = anim.indicatorY.easedT(now, MenuTween.Ease.OUT_CUBIC);
            yTop = Math.round(CreateRoomAnimator.lerp(indicatorFromY, indicatorToY, e));
            stretch = MenuTween.pulse(e);
        } else {
            yTop = itemTop(selectedMode);
            stretch = 1f;
        }
        int cx = x + w / 2;
        int cy = yTop + h / 2;
        drawScaledAxis(gg, cx, cy, 1f, stretch, () -> {
            gg.fill(x, yTop, x + w, yTop + h, withAlpha(WHITE, 0.06f * panelAlpha));
            gg.fill(x, yTop, x + 2, yTop + h, withAlpha(GOLD, panelAlpha));
        });
    }

    // ------------------------------------------------------------
    // 右栏：竞技场 / 计分目标 / 对局限时 / 房间人数 / 随机武器 / 说明
    // ------------------------------------------------------------

    private void drawRightColumn(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        drawArenaSection(gg, mouseX, mouseY, now, panelAlpha);
        drawScoreSection(gg, mouseX, mouseY, now, panelAlpha);
        drawTimeSection(gg, mouseX, mouseY, now, panelAlpha);
        drawPlayersSection(gg, mouseX, mouseY, now, panelAlpha);
        drawWeaponSection(gg, mouseX, mouseY, now, panelAlpha);
        drawFootnoteSection(gg, now, panelAlpha);
    }

    private float sectionAlpha(int idx, long now) {
        return anim.rightColumn[idx].easedT(now, MenuTween.Ease.OUT_CUBIC);
    }

    private int sectionYOffset(float e) {
        return Math.round(10 * (1 - e));
    }

    private void drawArenaSection(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = sectionAlpha(0, now);
        int yOff = sectionYOffset(e);
        float alpha = e * panelAlpha;
        gg.drawString(font, "竞技场", rx, top + 44 + yOff, withAlpha(TEXT_BASE, 0.4f * alpha), false);

        boolean leftHover = inRect(mouseX, mouseY, rx, arenaY + yOff, 18, 18);
        boolean rightHover = inRect(mouseX, mouseY, rx + 110, arenaY + yOff, 18, 18);
        boolean canCycle = arenas().size() > 1;
        drawArrow(gg, rx, arenaY + yOff, "◀", leftHover, canCycle, P_ARENA_LEFT, now, alpha);
        drawArrow(gg, rx + 110, arenaY + yOff, "▶", rightHover, canCycle, P_ARENA_RIGHT, now, alpha);

        int cx = rx + 64;
        int cy = arenaY + yOff + 9;
        int clipY1 = arenaY + yOff;
        int clipY2 = arenaY + yOff + 18;
        gg.enableScissor(rx + 18, clipY1, rx + 110, clipY2);
        drawSlideX(gg, cx, cy, arenaOldText, arenaText(), arenaDir, anim.arenaSlide, now, withAlpha(TEXT_BASE, alpha));
        gg.disableScissor();
    }

    private void drawScoreSection(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = sectionAlpha(1, now);
        int yOff = sectionYOffset(e);
        float alpha = e * panelAlpha;
        gg.drawString(font, "计分目标", rx, top + 84 + yOff, withAlpha(TEXT_BASE, 0.4f * alpha), false);

        boolean minusHover = inRect(mouseX, mouseY, rx, targetY + yOff, 18, 18);
        boolean plusHover = inRect(mouseX, mouseY, rx + 110, targetY + yOff, 18, 18);
        drawArrow(gg, rx, targetY + yOff, "-", minusHover, true, P_SCORE_MINUS, now, alpha);
        drawArrow(gg, rx + 110, targetY + yOff, "+", plusHover, true, P_SCORE_PLUS, now, alpha);

        int cx = rx + 64;
        int cy = targetY + yOff + 9;
        gg.enableScissor(rx + 18, targetY + yOff, rx + 110, targetY + yOff + 18);
        MenuTween.Anim rollAnim = scoreRollAnim != null ? scoreRollAnim : anim.scoreTimePlayersRoll[0];
        drawRollY(gg, cx, cy, scoreOldText, scoreText(target, mode().targetUnit()), scoreDir, rollAnim, now, withAlpha(TEXT_BASE, alpha));
        gg.disableScissor();
    }

    private void drawTimeSection(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = sectionAlpha(2, now);
        int yOff = sectionYOffset(e);
        float alpha = e * panelAlpha;
        gg.drawString(font, "对局限时", rx, top + 124 + yOff, withAlpha(TEXT_BASE, 0.4f * alpha), false);

        boolean minusHover = inRect(mouseX, mouseY, rx, timeY + yOff, 18, 18);
        boolean plusHover = inRect(mouseX, mouseY, rx + 110, timeY + yOff, 18, 18);
        drawArrow(gg, rx, timeY + yOff, "-", minusHover, true, P_TIME_MINUS, now, alpha);
        drawArrow(gg, rx + 110, timeY + yOff, "+", plusHover, true, P_TIME_PLUS, now, alpha);

        int cx = rx + 64;
        int cy = timeY + yOff + 9;
        gg.enableScissor(rx + 18, timeY + yOff, rx + 110, timeY + yOff + 18);
        drawRollY(gg, cx, cy, timeOldText, timeText(), timeDir, anim.scoreTimePlayersRoll[1], now, withAlpha(TEXT_BASE, alpha));
        gg.disableScissor();
    }

    private void drawPlayersSection(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = sectionAlpha(3, now);
        int yOff = sectionYOffset(e);

        float expandE;
        boolean expanding = anim.playersExpand.isRunning() && !anim.playersExpand.isDone(now);
        if (expanding) {
            float eased = anim.playersExpand.easedT(now, MenuTween.Ease.OUT_CUBIC);
            expandE = CreateRoomAnimator.expandProgress(playersVisible, eased);
        } else {
            expandE = playersVisible ? 1f : 0f;
        }
        if (expandE <= 0.001f) {
            return;
        }
        float alpha = e * panelAlpha * expandE;

        gg.drawString(font, "房间人数", rx, top + 156 + yOff, withAlpha(TEXT_BASE, 0.4f * alpha), false);

        boolean minusHover = inRect(mouseX, mouseY, rx, capY + yOff, 18, 18);
        boolean plusHover = inRect(mouseX, mouseY, rx + 110, capY + yOff, 18, 18);
        drawArrow(gg, rx, capY + yOff, "-", minusHover, playersVisible, P_PLAYERS_MINUS, now, alpha);
        drawArrow(gg, rx + 110, capY + yOff, "+", plusHover, playersVisible, P_PLAYERS_PLUS, now, alpha);

        int cx = rx + 64;
        int cy = capY + yOff + 9;
        gg.enableScissor(rx + 18, capY + yOff, rx + 110, capY + yOff + 18);
        drawRollY(gg, cx, cy, playersOldText, playersText(), playersDir, anim.scoreTimePlayersRoll[2], now, withAlpha(TEXT_BASE, alpha));
        gg.disableScissor();
    }

    private void drawWeaponSection(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = sectionAlpha(4, now);
        int yOff = sectionYOffset(e);
        boolean locked = "jump_sniper".equals(mode().id());
        float lockAlpha = locked ? 0.45f : 1.0f;
        float alpha = e * panelAlpha * lockAlpha;

        boolean hovered = !locked && inRect(mouseX, mouseY, rx, randomY + yOff, 128, 18);
        drawGhostButton(gg, rx, randomY + yOff, 128, 18, weaponText(), hovered, !locked, alpha);

        if (anim.weaponSweep.isRunning()) {
            float sweepE = anim.weaponSweep.easedT(now, MenuTween.Ease.OUT_CUBIC);
            drawSweep(gg, rx, randomY + yOff, 128, 18, sweepE, 0.25f * panelAlpha);
        }
    }

    private void drawFootnoteSection(GuiGraphics gg, long now, float panelAlpha) {
        float e = sectionAlpha(5, now);
        int yOff = sectionYOffset(e);
        float alpha = e * panelAlpha;
        gg.drawString(font, "满员将自动开局，房主也可提前开始", rx, top + 220 + yOff, withAlpha(TEXT_BASE, 0.35f * alpha), false);
        gg.drawString(font, "限时到则领先者胜，平分则平局", rx, top + 232 + yOff, withAlpha(TEXT_BASE, 0.35f * alpha), false);
    }

    // ------------------------------------------------------------
    // 底部：返回 / 创建
    // ------------------------------------------------------------

    private void drawFooter(GuiGraphics gg, int mouseX, int mouseY, long now, float panelAlpha) {
        float e = anim.footer.easedT(now, MenuTween.Ease.OUT_CUBIC);
        float alpha = e * panelAlpha;

        boolean backHovered = pendingNav == NAV_NONE && inRect(mouseX, mouseY, modeListX, footY, 60, 18);
        float backPress = pressScale(P_BACK, now);
        drawScaled(gg, modeListX + 30, footY + 9, backPress, () ->
                drawGhostButton(gg, modeListX, footY, 60, 18, "返回", backHovered, true, alpha));

        boolean createEnabled = !arenas().isEmpty();
        boolean createHovered = createEnabled && !createArmed && inRect(mouseX, mouseY, rx, footY, 80, 18);
        float createPress = pressScale(P_CREATE, now);
        drawScaled(gg, rx + 40, footY + 9, createPress, () -> drawCreateButton(gg, now, alpha, createHovered, createEnabled));
    }

    private void drawCreateButton(GuiGraphics gg, long now, float panelAlpha, boolean hovered, boolean enabled) {
        int x = rx, y = footY, w = 80, h = 18;
        float colorT;
        String label;
        if (createArmed) {
            if (!createConfirmed && anim.createColorShift.isDone(now)) {
                createConfirmed = true;
                anim.createRevert.start(now);
            }
            colorT = createConfirmed ? 1f : anim.createColorShift.easedT(now, MenuTween.Ease.OUT_CUBIC);
            label = createConfirmed ? "✓ 已创建" : "创 建";
        } else {
            colorT = 0f;
            label = "创 建";
        }
        int bg = enabled ? lerpColor(GOLD, GREEN, colorT) : withAlpha(GOLD, 0.35f);
        gg.fill(x, y, x + w, y + h, withAlpha(bg, panelAlpha));
        if (hovered) {
            gg.fill(x, y, x + w, y + 1, withAlpha(WHITE, 0.5f * panelAlpha));
        }
        gg.drawCenteredString(font, label, x + w / 2, y + 5, withAlpha(GOLD_TEXT, panelAlpha));

        if (createArmed && anim.createSweep.isRunning()) {
            float sweepE = anim.createSweep.easedT(now, MenuTween.Ease.OUT_CUBIC);
            drawSweep(gg, x, y, w, h, sweepE, 0.5f * panelAlpha);
        }
    }

    // ============================================================
    // 通用绘制原语
    // ============================================================

    private void drawArrow(GuiGraphics gg, int x, int y, String glyph, boolean hovered, boolean enabled,
                            int pressIdx, long now, float alpha) {
        float scale = pressScale(pressIdx, now);
        int cx = x + 9, cy = y + 9;
        drawScaled(gg, cx, cy, scale, () -> {
            int bg = withAlpha(WHITE, (enabled ? (hovered ? 0.12f : 0.05f) : 0.03f) * alpha);
            gg.fill(x, y, x + 18, y + 18, bg);
            int col = enabled ? (hovered ? withAlpha(WHITE, alpha) : withAlpha(TEXT_BASE, 0.7f * alpha))
                    : withAlpha(TEXT_BASE, 0.25f * alpha);
            gg.drawCenteredString(font, glyph, x + 9, y + 5, col);
        });
    }

    private void drawGhostButton(GuiGraphics gg, int x, int y, int w, int h, String label,
                                  boolean hovered, boolean enabled, float alpha) {
        float borderA = (enabled ? (hovered ? 0.4f : 0.15f) : 0.08f) * alpha;
        int border = withAlpha(WHITE, borderA);
        gg.fill(x, y, x + w, y + 1, border);
        gg.fill(x, y + h - 1, x + w, y + h, border);
        gg.fill(x, y, x + 1, y + h, border);
        gg.fill(x + w - 1, y, x + w, y + h, border);
        int textColor = enabled
                ? (hovered ? withAlpha(WHITE, alpha) : withAlpha(TEXT_BASE, 0.7f * alpha))
                : withAlpha(TEXT_BASE, 0.3f * alpha);
        gg.drawCenteredString(font, label, x + w / 2, y + 5, textColor);
    }

    private void drawSweep(GuiGraphics gg, int x, int y, int w, int h, float e, float maxAlpha) {
        if (e <= 0f || e >= 1f) {
            return;
        }
        float centerFrac = -0.4f + 1.8f * e;
        int barCenter = x + Math.round(centerFrac * w);
        int half = Math.max(1, Math.round(w * 0.15f));
        int bx1 = Math.max(x, barCenter - half);
        int bx2 = Math.min(x + w, barCenter + half);
        if (bx2 > bx1) {
            gg.fill(bx1, y, bx2, y + h, withAlpha(WHITE, maxAlpha));
        }
    }

    /** 纵向数字滚轮：旧值滑出 + 新值滑入（规格 §5 `roll()`，dir=1 新值自下而上）。
     *  振幅取裁剪区高度 18px，确保 e=1 时旧文案已完全移出裁剪区，不会有一帧跳变。 */
    private void drawRollY(GuiGraphics gg, int cx, int cy, String oldText, String newText, int dir,
                            MenuTween.Anim rollAnim, long now, int color) {
        float e = rollAnim.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int clip = 18;
        if (e < 1f && oldText != null && !oldText.isEmpty()) {
            int oy = cy + Math.round(CreateRoomAnimator.rollOffset(dir, e, clip, true));
            gg.drawCenteredString(font, oldText, cx, oy - 4, color);
        }
        int ny = cy + Math.round(CreateRoomAnimator.rollOffset(dir, e, clip, false));
        gg.drawCenteredString(font, newText, cx, ny - 4, color);
    }

    /** 横向滑动（竞技场，规格 §5 `slide()`，dir=1 ▶ 新值自右滑入）。
     *  振幅取裁剪区宽度 92px（rx+18 到 rx+110），同样确保完全滑出裁剪区。 */
    private void drawSlideX(GuiGraphics gg, int cx, int cy, String oldText, String newText, int dir,
                             MenuTween.Anim slideAnim, long now, int color) {
        float e = slideAnim.easedT(now, MenuTween.Ease.OUT_CUBIC);
        int clip = 92;
        if (e < 1f && oldText != null && !oldText.isEmpty()) {
            int ox = cx + Math.round(CreateRoomAnimator.rollOffset(dir, e, clip, true));
            gg.drawCenteredString(font, oldText, ox, cy - 4, color);
        }
        int nx = cx + Math.round(CreateRoomAnimator.rollOffset(dir, e, clip, false));
        gg.drawCenteredString(font, newText, nx, cy - 4, color);
    }

    private float pressScale(int idx, long now) {
        MenuTween.Anim a = anim.press[idx];
        if (!a.isRunning()) {
            return 1f;
        }
        return 0.82f + 0.18f * a.easedT(now, MenuTween.Ease.OUT_BACK);
    }

    private void drawScaled(GuiGraphics gg, int cx, int cy, float scale, Runnable draw) {
        drawScaledAxis(gg, cx, cy, scale, scale, draw);
    }

    private void drawScaledAxis(GuiGraphics gg, int cx, int cy, float sx, float sy, Runnable draw) {
        if (Math.abs(sx - 1f) < 0.001f && Math.abs(sy - 1f) < 0.001f) {
            draw.run();
            return;
        }
        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().scale(sx, sy, 1f);
        gg.pose().translate(-cx, -cy, 0);
        draw.run();
        gg.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
