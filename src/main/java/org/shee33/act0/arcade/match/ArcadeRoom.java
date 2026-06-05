package org.shee33.act0.arcade.match;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 街机房间（开局前的等待大厅）：由 OP 创建，玩家浏览并加入，满员自动开局或房主手动开局。
 *
 * <p>房间是"空闲"与"进行中的 {@link ArcadeMatch}"之间的一层抽象：它只持有报名信息
 * （模式、竞技场、自定义计分目标、成员），真正开局时交给 {@link MatchLauncher} 创建对局，
 * 随后房间即被 {@link RoomManager} 回收。所有方法在服务器主线程调用，非线程安全。
 */
public final class ArcadeRoom {

    /** 房间状态。 */
    public enum State {
        /** 等待玩家加入。 */
        WAITING,
        /** 已触发开局（即将被回收）。 */
        LAUNCHING,
        /** 对局进行中（弹性模式保留房间以便中途加入/重连展示）。 */
        IN_PROGRESS
    }

    private final String roomId;
    private final UUID hostId;
    private final String hostName;
    private final String modeId;
    private final String arenaId;
    /** 自定义计分目标（回合胜场 / 击杀数）；{@code null} 表示用模式默认值。 */
    private final Integer winTarget;
    private final int capacity;
    /** 对局限时（秒）；0 表示不限时。 */
    private final int timeLimitSeconds;
    /** 是否随机武器。 */
    private final boolean randomWeapons;

    /** 成员（保持加入顺序，房主始终在首位）。 */
    private final Set<UUID> members = new LinkedHashSet<>();
    private State state = State.WAITING;
    /** 进行中对局的 id（仅 IN_PROGRESS 态有效）。 */
    private String matchId;

    public ArcadeRoom(String roomId, UUID hostId, String hostName,
                      String modeId, String arenaId, Integer winTarget, int capacity,
                      int timeLimitSeconds, boolean randomWeapons) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.hostName = hostName;
        this.modeId = modeId;
        this.arenaId = arenaId;
        this.winTarget = winTarget;
        this.capacity = Math.max(2, capacity);
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
        this.randomWeapons = randomWeapons;
        this.members.add(hostId);
    }

    public String roomId() {
        return roomId;
    }

    public UUID hostId() {
        return hostId;
    }

    public String hostName() {
        return hostName;
    }

    public String modeId() {
        return modeId;
    }

    public String arenaId() {
        return arenaId;
    }

    public Integer winTarget() {
        return winTarget;
    }

    public int capacity() {
        return capacity;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public boolean randomWeapons() {
        return randomWeapons;
    }

    public State state() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    public String matchId() {
        return matchId;
    }

    void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    /** 是否进行中（可中途加入展示）。 */
    public boolean isInProgress() {
        return state == State.IN_PROGRESS;
    }

    /** 成员视图（只读副本）。 */
    public Set<UUID> members() {
        return new LinkedHashSet<>(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= capacity;
    }

    public boolean contains(UUID id) {
        return members.contains(id);
    }

    boolean addMember(UUID id) {
        if (isFull() || (state != State.WAITING && state != State.IN_PROGRESS)) {
            return false;
        }
        return members.add(id);
    }

    boolean removeMember(UUID id) {
        return members.remove(id);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
