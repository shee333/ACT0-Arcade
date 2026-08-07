package org.shee33.act0.arcade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 房间在网络/客户端展示用的数据传输对象（MC 之外的纯数据）。
 *
 * <p>{@link #youAreMember} 由服务端按接收玩家逐一计算，便于客户端直接判断"加入/离开"按钮状态。
 *
 * <p>{@link #modeId}/{@link #timeLimitSeconds}/{@link #randomWeapons}/{@link #friendlyFire}
 * 是为「房间浏览器」右侧详情抽屉的设定键值对（模式/地图/计分目标/限时/随机武器/友伤）新增的
 * 字段：抽屉需要按模式语义生成键名与值，而 {@link #modeName} 是本地化后的展示名、
 * 不适合做逻辑分支的判据，因此额外携带稳定的 {@link #modeId}。
 */
public final class RoomDto {

    private final String roomId;
    private final String modeId;
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
    private final int timeLimitSeconds;
    private final boolean randomWeapons;
    private final boolean friendlyFire;

    public RoomDto(String roomId, String modeName, String arenaId, String hostName,
                   int size, int capacity, String targetText, boolean youAreMember,
                   boolean inProgress) {
        this(roomId, "", modeName, arenaId, hostName, size, capacity, targetText, youAreMember,
                inProgress, "", 0, 0, false, false);
    }

    public RoomDto(String roomId, String modeId, String modeName, String arenaId, String hostName,
                   int size, int capacity, String targetText, boolean youAreMember,
                   boolean inProgress, String playersText, int elapsedSeconds,
                   int timeLimitSeconds, boolean randomWeapons, boolean friendlyFire) {
        this.roomId = roomId;
        this.modeId = modeId != null ? modeId : "";
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
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
        this.randomWeapons = randomWeapons;
        this.friendlyFire = friendlyFire;
    }

    public String roomId() {
        return roomId;
    }

    /** 稳定的模式标识（{@code team_deathmatch}/{@code hot_zone} 等），供 UI 做模式语义分支。 */
    public String modeId() {
        return modeId;
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

    /** 对局限时（秒）；{@code 0} 表示不限时。 */
    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public boolean randomWeapons() {
        return randomWeapons;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public boolean isFull() {
        return size >= capacity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomId);
        buf.writeUtf(modeId);
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
        buf.writeVarInt(timeLimitSeconds);
        buf.writeBoolean(randomWeapons);
        buf.writeBoolean(friendlyFire);
    }

    public static RoomDto decode(FriendlyByteBuf buf) {
        return new RoomDto(
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
                buf.readBoolean(), buf.readUtf(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }
}
