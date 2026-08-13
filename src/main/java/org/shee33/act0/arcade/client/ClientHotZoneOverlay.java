package org.shee33.act0.arcade.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.arena.HotZoneArea;
import org.shee33.act0.arcade.arena.HotZoneAreaEdges;

/**
 * 热区矩形边界世界空间渲染：把 {@link ClientHotZoneState} 里缓存的矩形范围画成一圈发光线框
 * （WorldEdit / 创造模式选区式的标准可视化），随视角实时跟随。技术路线抄的是姊妹仓库
 * ACT0-Battlefield {@code BattlefieldDeployWorldOverlay#drawAreaBox} 已验证过的写法
 * （{@code RenderLevelStageEvent} + {@code PoseStack} 平移相机偏移 + {@code RenderType.LINES}）。
 *
 * <p>渲染时机选 {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS}——地形（含半透明
 * 方块，如水/玻璃）已经画完、粒子与天气尚未开始，是世界标记类叠加层的标准插入点。
 *
 * <p>选区高亮框必须"看穿地形"才有意义——玩家可能站在热区矩形被山体/建筑遮挡的一侧，看不到
 * 边框就等于没有这个功能。因此绘制时主动 {@link RenderSystem#disableDepthTest()}，画完立刻
 * 恢复，不影响后续正常的地形/实体渲染。
 *
 * <p>同时画两个框：当前生效的热区用暖金橙常亮，{@link ClientHotZoneState#next()} 提供的迁移预告
 * 区用白色 + 正弦脉动透明度。用白色而不是另一种橙/黄，是为了和当前热区一眼分得开，也避开蓝红两个
 * 阵营色；脉动本质是周期性淡入淡出（FlatTheme 允许的 fade），不是被禁用的闪烁，且时间源与
 * {@code MenuTween} 一致用 {@link System#currentTimeMillis()}，不绑 MC 世界 tick。
 *
 * <p>渲染门槛只看 {@link ClientHotZoneState#present()}：服务端只在真正的热区模式对局里才会
 * 下发区域（见 {@code ArcadeMatch#broadcastHotZoneToParticipants}），客户端不需要再额外判断
 * "当前是否在对局中"。对局结束/主动退出对局由 {@code ClearHotZonePacket} 通知清空；玩家整个断开
 * 服务器连接（{@link ClientPlayerNetworkEvent.LoggingOut}）则由本类兜底清空，避免下一次连到
 * 别的服务器/世界时残留上一局的边框。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientHotZoneOverlay {

    /** 落地轮廓（4 条底边）：较高不透明度的暖金橙色——玩家最需要辨认的部分。 */
    private static final int FLOOR_EDGE_COLOR = 0xF2FFB74D;
    /** 顶边 + 立柱（次要边界）：同色但更透明，纯色 + 透明度分层，不引入渐变。 */
    private static final int WALL_EDGE_COLOR = 0x80FFB74D;

    private static final int NEXT_FLOOR_EDGE_COLOR = 0xE6FFFFFF;
    private static final int NEXT_WALL_EDGE_COLOR = 0x66FFFFFF;

    private static final long PULSE_PERIOD_MS = 1200L;
    private static final float PULSE_MIN_ALPHA = 0.40f;

    private static final BoxCache ACTIVE_CACHE = new BoxCache();
    private static final BoxCache NEXT_CACHE = new BoxCache();

    private ClientHotZoneOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        HotZoneArea active = ClientHotZoneState.active();
        if (active == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        String playerDimension = mc.player.level().dimension().location().toString();

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();
        pose.popPose();

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.LINES);

        boolean drewAnything = false;
        if (active.dimension().equals(playerDimension)) {
            drawBox(consumer, matrix, ACTIVE_CACHE.edgesOf(active),
                    FLOOR_EDGE_COLOR, WALL_EDGE_COLOR, 1.0f);
            drewAnything = true;
        }
        HotZoneArea next = ClientHotZoneState.next();
        if (next != null && next.dimension().equals(playerDimension)) {
            drawBox(consumer, matrix, NEXT_CACHE.edgesOf(next),
                    NEXT_FLOOR_EDGE_COLOR, NEXT_WALL_EDGE_COLOR, pulseAlpha());
            drewAnything = true;
        }
        if (!drewAnything) {
            return; // 玩家当前维度与热区维度不一致（例如误传送到其它维度），没有任何顶点要提交
        }

        RenderSystem.disableDepthTest();
        buffer.endBatch(RenderType.LINES);
        RenderSystem.enableDepthTest();
    }

    /** 断开服务器连接时兜底清空，防止下次连到另一个世界/服务器时残留上一局的边框。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHotZoneState.clear();
    }

    private static float pulseAlpha() {
        double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
        double unit = (Math.sin(phase * 2.0 * Math.PI) + 1.0) * 0.5;
        return PULSE_MIN_ALPHA + (1.0f - PULSE_MIN_ALPHA) * (float) unit;
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix, double[][] edges,
                                int floorColor, int wallColor, float alphaScale) {
        for (int i = 0; i < edges.length; i++) {
            double[] e = edges[i];
            int color = i < HotZoneAreaEdges.FLOOR_EDGE_COUNT ? floorColor : wallColor;
            drawLine(consumer, matrix, e[0], e[1], e[2], e[3], e[4], e[5], color, alphaScale);
        }
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2, int argb, float alphaScale) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f * alphaScale;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        // RenderType.LINES 用的顶点格式含法线，两端都要补一个法线分量，否则严格的顶点格式
        // 校验（如 Sodium/Xenon 等第三方渲染优化 mod）会崩；朝向对纯色线框的视觉效果没有影响。
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f).endVertex();
    }

    /**
     * 单个矩形的 12 条棱端点坐标缓存：只要区域没变就不重新计算，避免每帧重复拼 8 个顶点的组合。
     * 当前热区与预告热区各持一份，否则两个框会互相踢掉对方的缓存、导致每帧都重算两次。
     */
    private static final class BoxCache {

        private double[][] edges;
        private HotZoneArea cached;

        private double[][] edgesOf(HotZoneArea area) {
            if (edges != null && sameBounds(cached, area)) {
                return edges;
            }
            edges = HotZoneAreaEdges.edgesOf(area);
            cached = area;
            return edges;
        }

        private static boolean sameBounds(HotZoneArea a, HotZoneArea b) {
            return a != null && b != null
                    && a.dimension().equals(b.dimension())
                    && a.minX() == b.minX() && a.maxX() == b.maxX()
                    && a.minY() == b.minY() && a.maxY() == b.maxY()
                    && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
        }
    }
}
