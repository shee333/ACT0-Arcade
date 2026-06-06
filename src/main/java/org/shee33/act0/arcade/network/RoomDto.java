package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 房间在网络/客户端展示用的数据传输对象（MC 之外的纯数据）。
 *
 * <p>{@link #youAreMember} 由服务端按接收玩家逐一计算，便于客户端直接判断"加入/离开"按钮状态。
 */
public final class RoomDto {

    private final String roomId;
    private final String modeName;
    private final String arenaId;
    private final String hostName;
    private final int size;
    private final int capacity;
    private final String targetText;
    private final boolean youAreMember;
    private final boolean inProgress;
    private final String playersText;
    private final int elapsedSeconds;

    public RoomDto(String roomId, String modeName, String arenaId, String hostName,
                   int size, int capacity, String targetText, boolean youAreMember,
                   boolean inProgress) {
        this(roomId, modeName, arenaId, hostName, size, capacity, targetText, youAreMember, inProgress, "", 0);
    }

    public RoomDto(String roomId, String modeName, String arenaId, String hostName,
                   int size, int capacity, String targetText, boolean youAreMember,
                   boolean inProgress, String playersText, int elapsedSeconds) {
        this.roomId = roomId;
        this.modeName = modeName;
        this.arenaId = arenaId;
        this.hostName = hostName;
        this.size = size;
        this.capacity = capacity;
        this.targetText = targetText;
        this.youAreMember = youAreMember;
        this.inProgress = inProgress;
        this.playersText = playersText != null ? playersText : "";
        this.elapsedSeconds = Math.max(0, elapsedSeconds);
    }

    public String roomId() {
        return roomId;
    }

    public String modeName() {
        return modeName;
    }

    public String arenaId() {
        return arenaId;
    }

    public String hostName() {
        return hostName;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public String targetText() {
        return targetText;
    }

    public boolean youAreMember() {
        return youAreMember;
    }

    public boolean inProgress() {
        return inProgress;
    }

    public String playersText() {
        return playersText;
    }

    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isFull() {
        return size >= capacity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomId);
        buf.writeUtf(modeName);
        buf.writeUtf(arenaId);
        buf.writeUtf(hostName);
        buf.writeVarInt(size);
        buf.writeVarInt(capacity);
        buf.writeUtf(targetText);
        buf.writeBoolean(youAreMember);
        buf.writeBoolean(inProgress);
        buf.writeUtf(playersText);
        buf.writeVarInt(elapsedSeconds);
    }

    public static RoomDto decode(FriendlyByteBuf buf) {
        return new RoomDto(
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
                buf.readBoolean(), buf.readUtf(), buf.readVarInt());
    }
}
