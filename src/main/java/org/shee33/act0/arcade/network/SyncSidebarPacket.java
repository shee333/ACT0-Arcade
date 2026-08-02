package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientSidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C：下发对局侧边栏内容，由客户端自绘 HUD 显示。
 *
 * <p>替代原版计分板侧边栏：原版侧边栏会在每行右侧强制显示红色分值（序号），无法隐藏；
 * 改由本包把标题与各行文本（含 §-颜色码）直接发给客户端，自绘时<b>不渲染任何数字</b>。
 *
 * <p>{@code show=false} 表示清除侧边栏（对局结束/离场）。
 */
public final class SyncSidebarPacket {

    private static final int MAX_LIST_ENTRIES = 256;

    private final boolean show;
    private final String title;
    private final List<String> lines;

    public SyncSidebarPacket(boolean show, String title, List<String> lines) {
        this.show = show;
        this.title = title != null ? title : "";
        this.lines = lines != null ? lines : List.of();
    }

    public static void encode(SyncSidebarPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.show);
        buf.writeUtf(msg.title);
        buf.writeVarInt(msg.lines.size());
        for (String line : msg.lines) {
            buf.writeUtf(line);
        }
    }

    public static SyncSidebarPacket decode(FriendlyByteBuf buf) {
        boolean show = buf.readBoolean();
        String title = buf.readUtf();
        int n = Math.max(0, Math.min(buf.readVarInt(), MAX_LIST_ENTRIES));
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(buf.readUtf());
        }
        return new SyncSidebarPacket(show, title, lines);
    }

    public static void handle(SyncSidebarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSidebar.accept(msg.show, msg.title, msg.lines)));
        context.setPacketHandled(true);
    }
}
