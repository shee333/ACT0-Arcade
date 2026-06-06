package org.shee33.act0.arcade.match;

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 单玩家发光同步：只向<b>某一个观察者</b>发送实体数据包，把目标实体的"发光"共享标记位打开/关闭，
 * 从而实现"只有死者能看到击杀者描边、其他玩家完全看不到"的竞技正确行为。
 *
 * <p>原版 {@code Entity.setGlowingTag} 是全局发光（所有客户端可见，会泄露击杀者位置）。这里改为手动
 * 构造 {@link ClientboundSetEntityDataPacket}：用目标实体的公开状态重建共享标记字节（{@code accessor id 0}），
 * 仅翻转第 6 位（{@code 0x40} 发光位），<b>只发给指定观察者</b>。关闭时发一个用实体真实状态重建的还原包。
 *
 * <p>说明：{@code Entity.DATA_SHARED_FLAGS_ID} 在 Entity 内为 protected，无法直接读取，故按已知位布局
 * 从公开 getter 重建字节（着火/潜行/疾跑/游泳/隐身/发光/滑翔）。
 */
public final class GlowSync {

    /** 共享标记字节的数据访问器：accessor id 固定为 0，序列化为 Byte。 */
    private static final EntityDataAccessor<Byte> SHARED_FLAGS =
            new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    private static final int FLAG_ON_FIRE = 0;
    private static final int FLAG_CROUCHING = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    private static final int FLAG_GLOWING = 6;
    private static final int FLAG_FALL_FLYING = 7;

    private GlowSync() {
    }

    /** 只让 {@code viewer} 看到 {@code target} 发光。 */
    public static void showGlowTo(ServerPlayer viewer, Entity target) {
        sendSharedFlags(viewer, target, true);
    }

    /** 只对 {@code viewer} 关闭 {@code target} 的发光（恢复其真实标记）。 */
    public static void hideGlowFrom(ServerPlayer viewer, Entity target) {
        sendSharedFlags(viewer, target, false);
    }

    private static void sendSharedFlags(ServerPlayer viewer, Entity target, boolean glow) {
        if (viewer == null || target == null) {
            return;
        }
        byte flags = baseFlags(target);
        flags = setBit(flags, FLAG_GLOWING, glow || target.hasGlowingTag());
        SynchedEntityData.DataValue<Byte> value = SynchedEntityData.DataValue.create(SHARED_FLAGS, flags);
        viewer.connection.send(new ClientboundSetEntityDataPacket(target.getId(), List.of(value)));
    }

    /** 用实体公开状态重建共享标记字节（不含发光位，由调用方决定）。 */
    private static byte baseFlags(Entity e) {
        byte flags = 0;
        flags = setBit(flags, FLAG_ON_FIRE, e.isOnFire());
        flags = setBit(flags, FLAG_CROUCHING, e.isCrouching());
        flags = setBit(flags, FLAG_SPRINTING, e.isSprinting());
        flags = setBit(flags, FLAG_SWIMMING, e.isSwimming());
        flags = setBit(flags, FLAG_INVISIBLE, e.isInvisible());
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            flags = setBit(flags, FLAG_FALL_FLYING, living.isFallFlying());
        }
        return flags;
    }

    private static byte setBit(byte base, int bit, boolean on) {
        if (on) {
            return (byte) (base | (1 << bit));
        }
        return (byte) (base & ~(1 << bit));
    }
}

