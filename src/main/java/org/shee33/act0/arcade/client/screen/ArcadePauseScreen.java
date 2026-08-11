package org.shee33.act0.arcade.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shee33.act0.arcade.client.ClientSidebar;

import javax.annotation.Nullable;

/**
 * 街机模式对局内的 ESC 暂停菜单（《战地暂停菜单动效规格文档》的街机裁剪版）。
 *
 * <p>由 {@code ArcadePauseMenuHook} 在原版 {@code PauseScreen} 打开时替换进来。菜单项四个：
 * 回到游戏 / 设置 / 退出战局 / 退出游戏——街机没有小队系统，故没有「小队管理」项，也没有右侧
 * 票数/小队状态区。
 *
 * <p>本类刻意保持很薄：一切时间轴与像素在 {@link ArcadePauseAnimator}，这里只做生命周期、
 * 输入转发与命令下发——与本仓库既有的 {@link CreateRoomScreen} + {@link CreateRoomAnimator}
 * 分工完全一致。
 *
 * <p>{@link #isPauseScreen()} 返回 {@code false}：多人对局本来就不会因为 ESC 暂停，世界必须继续
 * 渲染与推进。这是规格文档第一条设计原则（"游戏没停"是第一信息）的技术前提——返回 {@code true}
 * 会让单人测试环境里的世界冻结，整套"对局仍在进行"的表达当场变成谎话。
 */
public final class ArcadePauseScreen extends Screen {

    /** 危险操作的反馈延时：Toast 淡入完再多留 240ms，否则玩家永远看不到自己触发了什么。 */
    private static final int DEFER_MS = PauseMenuAnim.TOAST_IN_MS + 240;

    private final ArcadePauseAnimator animator = new ArcadePauseAnimator(MenuTween.now());

    /** 待延迟执行的危险动作与其执行时刻；{@code null} 表示无。 */
    @Nullable
    private ArcadePauseAnimator.Item deferred;
    private long deferredAtMs;

    /** Enter 长按状态：键盘与鼠标共用同一套长按确认，只是触发源不同。 */
    private boolean enterHeld;

    public ArcadePauseScreen() {
        super(Component.literal("暂停"));
    }

    /** 街机的"在局中"判定，与被替换掉的 {@code ArcadePauseMenuExitButton} 完全一致。 */
    public static boolean isInArcadeMatch() {
        return ClientSidebar.isShown();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        long now = MenuTween.now();
        animator.render(gg, font, width, height, now);

        ArcadePauseAnimator.Item item = animator.pollPendingItem();
        if (item != null) {
            dispatch(item, now);
        }
        if (deferred != null && now >= deferredAtMs) {
            ArcadePauseAnimator.Item run = deferred;
            deferred = null;
            execute(run);
            return;
        }
        if (animator.isClosing() && now >= animator.closeDoneAt()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** 对局结束/被踢出时兜底关闭：菜单的模式名靠侧边栏驱动，对局一没了就成了上一局的残影。 */
    @Override
    public void tick() {
        if (deferred == null && !animator.isClosing() && !isInArcadeMatch()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    // ============================================================
    // 动作分发
    // ============================================================

    private void dispatch(ArcadePauseAnimator.Item item, long now) {
        switch (item) {
            case RESUME -> animator.beginClose(now);
            case SETTINGS -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new OptionsScreen(this, mc.options));
            }
            // 长按确认项：Toast 已由动画层在填满那一刻弹出，这里只安排延迟执行。
            case LEAVE_MATCH, QUIT_GAME -> {
                deferred = item;
                deferredAtMs = now + DEFER_MS;
            }
        }
    }

    private void execute(ArcadePauseAnimator.Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (item == ArcadePauseAnimator.Item.LEAVE_MATCH) {
            LocalPlayer player = mc.player;
            if (player != null) {
                player.connection.sendCommand("arcade leave");
            }
            mc.setScreen(null);
            return;
        }
        // 退出游戏：先干净地离开服务器，再退出进程（等价于原版"断开 + 退出"两步）。
        ClientLevel level = mc.level;
        if (level != null) {
            level.disconnect();
        }
        mc.clearLevel();
        mc.stop();
    }

    // ============================================================
    // 输入
    // ============================================================

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        animator.onMouseMoved(mouseX, mouseY, MenuTween.now());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || animator.isClosing()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = MenuTween.now();
        int index = animator.itemAt(mouseX, mouseY);
        if (index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        animator.setFocus(index, now);
        ArcadePauseAnimator.Item item = animator.itemOf(index);
        if (item.hold) {
            animator.beginHold(index, now);
        } else {
            animator.activate(item);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && animator.isHolding()) {
            animator.cancelHold(MenuTween.now());
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 按住不动地拖过界也算移出（规格文档 §3.2：移出即取消）。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && animator.isHolding() && animator.itemAt(mouseX, mouseY) != animator.holdingIndex()) {
            animator.cancelHold(MenuTween.now());
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        long now = MenuTween.now();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            animator.beginClose(now);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            animator.setFocus(animator.focusIndex() - 1, now);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            animator.setFocus(animator.focusIndex() + 1, now);
            return true;
        }
        if (isConfirmKey(keyCode)) {
            ArcadePauseAnimator.Item item = animator.focusedItem();
            if (item.hold) {
                // GLFW 的按键重复也会走 keyPressed，重复触发会把长按起点不断推后。
                if (!enterHeld) {
                    enterHeld = true;
                    animator.beginHold(animator.focusIndex(), now);
                }
            } else {
                animator.activate(item);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isConfirmKey(keyCode)) {
            enterHeld = false;
            animator.cancelHold(MenuTween.now());
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    /** ESC 自己接管（要播反向序列），不能让父类直接 {@code onClose}。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
