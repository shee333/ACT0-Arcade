package org.shee33.act0.arcade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.client.screen.FlatTheme;

import java.util.List;

/**
 * 客户端对局 HUD：在屏幕右侧自绘侧边栏（FlatTheme 战地 2042 风格）。
 *
 * <p>替代原版计分板侧边栏，<b>不渲染右侧红色分值（序号）</b>。内容来自 {@link ClientSidebar}，
 * 由服务端经 {@code SyncSidebarPacket} 下发。位置与原版侧边栏一致（右边缘、垂直居中）。
 *
 * <p><b>动效范围决策</b>（详见 AGENTS.md「视觉设计规范」/「菜单动效规范」，本类是持久战斗 HUD
 * 而非玩家主动打开的弹层菜单，二者动效目标不同——见类内 rationale）：本类只借用
 * {@code MenuTween} 引擎的「实时时钟采样、每帧重新取值、无 MC-tick 状态机」纪律，
 * 用于内容行新增/变化时的柔和淡入；<b>不</b>引入 {@code MenuChrome.GOLD}、幽灵按钮、
 * 滑动选中指示器、按压回弹或任何"号召性用语"视觉处理——这些是弹层菜单专属的语言。
 * 由于 {@code MenuTween} 类本身是 package-private（{@code client.screen} 包内可见，
 * 本类位于父包 {@code client}，跨包不可见），且任务明确禁止修改 {@code MenuTween.java}
 * 使其变为 public，此处改用 {@link FlatTheme#easeOut} ——与 {@code MenuTween.Ease.OUT_CUBIC}
 * 完全同一条公式 {@code 1-(1-t)^3}，且其 javadoc 已声明与 {@code MenuTween} 共享同一时间基线——
 * 来复刻相同的缓动纪律，而不是引入非法的跨包引用。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcadeHudOverlay {

    private static final int PAD_X = 8;
    private static final int LINE_H = 12;
    private static final int HEADER_H = 16;

    /** 单行内容淡入时长：150-300ms 区间取下段（AGENTS.md 动效规范），战斗中信息应尽快可读、克制而非表演。 */
    private static final long ROW_FADE_MS = 160L;

    // 逐行淡入起点时间戳，按下标与 ClientSidebar.lines() 位置对应（渲染循环本就按下标做斑马纹，
    // 这里复用同一套"位置稳定"假设，不引入额外的行 ID 概念）。仅做"实时时钟+每帧重采样"，
    // 不持有任何 MC tick 状态——语义上等价于每个 HUD 帧都新建一份 MenuTween.Anim 并立即采样。
    private static String[] prevLines = new String[0];
    private static long[] rowAppearAtMs = new long[0];
    private static String prevTitle = "";
    private static long titleAppearAtMs = 0L;

    private ArcadeHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientSidebar.isShown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        List<String> lines = ClientSidebar.lines();
        String title = ClientSidebar.title();
        if (lines.isEmpty() && title.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        updateRowFadeState(lines, title, now);

        GuiGraphics gg = event.getGuiGraphics();
        Font font = mc.font;

        int contentW = font.width(title);
        for (String line : lines) {
            contentW = Math.max(contentW, font.width(line));
        }
        int panelW = Math.max(118, contentW + PAD_X * 2 + 10);
        int screenW = gg.guiWidth();
        int screenH = gg.guiHeight();

        int totalH = HEADER_H + lines.size() * LINE_H + 6;
        int top = screenH / 2 - totalH / 2;
        int right = screenW - 6;
        int left = right - panelW;

        // 主面板：扁平纯色 + 1px 边框 + 标题栏（无阴影/无斜角高光）——结构性外壳，不参与淡入，
        // 避免整块面板随内容刷新而闪烁；面板尺寸沿用既有的按内容即时计算，不做尺寸补间。
        FlatTheme.panel(gg, left, top, panelW, totalH);
        FlatTheme.titleBar(gg, left, top, panelW, HEADER_H);
        gg.fill(left, top, left + 2, top + totalH, FlatTheme.ACCENT);

        // 标题：右对齐文本；文本变化时随 titleAppearAtMs 做同款柔和淡入，静止时与改动前完全一致（alpha=1）。
        float titleAlpha = rowFadeAlpha(titleAppearAtMs, now);
        gg.drawString(font, title, right - PAD_X - font.width(title), top + 4,
                FlatTheme.withAlpha(FlatTheme.TEXT_HEADER, titleAlpha), false);

        int y = top + HEADER_H + 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float alpha = rowFadeAlpha(rowAppearAtMs[i], now);
            int rowColor = (i & 1) == 0 ? FlatTheme.SURFACE : FlatTheme.SURFACE_DISABLED;
            gg.fill(left + 4, y, right - 4, y + LINE_H - 1, FlatTheme.withAlpha(rowColor, alpha));
            int bulletColor = colorForLine(line);
            gg.fill(left + 6, y + 4, left + 9, y + 7, FlatTheme.withAlpha(bulletColor, alpha));
            gg.drawString(font, line, right - PAD_X - font.width(line), y + 2,
                    FlatTheme.withAlpha(FlatTheme.TEXT, alpha), false);
            y += LINE_H;
        }
    }

    /**
     * 按下标比对上一帧内容：新增行或文本变化的行重置淡入起点；未变化的行沿用旧起点
     * （已经播完的淡入不会被打断重播，只在内容真的变化时才重新触发一次短促交叉淡入）。
     *
     * <p>不做逐行错峰级联（stagger delay）——本 HUD 侧边栏没有"陆续登场"的演出诉求，
     * 多行同时更新时应作为一次同步的内容刷新一起柔化，而不是像弹层菜单那样分批吸引视线。
     */
    private static void updateRowFadeState(List<String> lines, String title, long now) {
        int n = lines.size();
        long[] nextAppearAt = new long[n];
        String[] nextLines = new String[n];
        for (int i = 0; i < n; i++) {
            String line = lines.get(i);
            nextLines[i] = line;
            boolean changed = i >= prevLines.length || !line.equals(prevLines[i]);
            nextAppearAt[i] = changed ? now : rowAppearAtMs[i];
        }
        prevLines = nextLines;
        rowAppearAtMs = nextAppearAt;

        if (!title.equals(prevTitle)) {
            titleAppearAtMs = now;
            prevTitle = title;
        }
    }

    /**
     * 行/标题淡入透明度：OUT_CUBIC 缓动，{@link #ROW_FADE_MS}。复用 {@link FlatTheme#easeOut}——
     * 与 {@code MenuTween.Ease.OUT_CUBIC} 完全同一条公式，理由见类头 rationale——
     * 而不是直接引用 {@code MenuTween} 类本身（package-private，跨包不可见）。
     */
    private static float rowFadeAlpha(long appearAtMs, long now) {
        if (appearAtMs <= 0L) {
            return 1f;
        }
        float t = Math.max(0f, Math.min(1f, (now - appearAtMs) / (float) ROW_FADE_MS));
        return FlatTheme.easeOut(t);
    }

    private static int colorForLine(String line) {
        if (line == null) {
            return FlatTheme.ACCENT;
        }
        if (line.contains("§c") || line.contains("红")) {
            return FlatTheme.RED;
        }
        if (line.contains("§9") || line.contains("蓝")) {
            return FlatTheme.BLUE;
        }
        if (line.contains("§a") || line.contains("胜") || line.contains("击杀")) {
            return FlatTheme.SUCCESS;
        }
        if (line.contains("§e")) {
            return FlatTheme.WARNING;
        }
        return FlatTheme.ACCENT;
    }
}
