package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.shee33.act0.arcade.client.ClientRoomList;

import java.util.function.Supplier;

/** S→C：关闭游戏浏览器。开局/中途加入成功后避免玩家卡在浏览器界面。 */
public final class CloseRoomBrowserPacket {

    public CloseRoomBrowserPacket() {
    }

    public static void encode(CloseRoomBrowserPacket msg, FriendlyByteBuf buf) {
    }

    public static CloseRoomBrowserPacket decode(FriendlyByteBuf buf) {
        return new CloseRoomBrowserPacket();
    }

    public static void handle(CloseRoomBrowserPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientRoomList::closeBrowser));
        context.setPacketHandled(true);
    }
}
