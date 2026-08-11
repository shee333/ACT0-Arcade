package org.shee33.act0.arcade.match;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.mode.MatchOptions;
import org.shee33.act0.arcade.mode.RandomWeaponMode;

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
    private int capacity;
    /** 对局限时（秒）；0 表示不限时。 */
    private final int timeLimitSeconds;
    /** 随机武器细分模式。 */
    private final RandomWeaponMode randomWeaponMode;
    /** 完整对局设定。 */
    private final MatchOptions options;

    /** 成员（保持加入顺序，房主始终在首位）。 */
    private final Set<UUID> members = new LinkedHashSet<>();
    /** 团队模式选边偏好：0=蓝队，1=红队。 */
    private final Map<UUID, Integer> preferredTeams = new LinkedHashMap<>();
    /**
     * 成员中属于 AI 士兵的那一部分（{@link #members} 的子集，保持加入顺序即为踢人优先级）。
     *
     * <p>bot 是伪造连接的真 {@code ServerPlayer}，在 {@link #members} 里与真人无从区分，
     * 而"真人加入时优先踢 bot 让位"与"房间解散时撤走 bot"都必须能区分，故单独记一份。
     */
    private final Set<UUID> bots = new LinkedHashSet<>();
    /** 本房间 bot 的统一难度档；由房主在大厅内切换，不随全局默认值变动。 */
    private AimModel.Difficulty botDifficulty = AimModel.Difficulty.NORMAL;
    private State state = State.WAITING;
    /** 进行中对局的 id（仅 IN_PROGRESS 态有效）。 */
    private String matchId;

    public ArcadeRoom(String roomId, UUID hostId, String hostName,
                      String modeId, String arenaId, Integer winTarget, int capacity,
                      int timeLimitSeconds, boolean randomWeapons) {
        this(roomId, hostId, hostName, modeId, arenaId, winTarget, capacity, timeLimitSeconds,
                randomWeapons ? RandomWeaponMode.ALL : RandomWeaponMode.OFF);
    }

    public ArcadeRoom(String roomId, UUID hostId, String hostName,
                      String modeId, String arenaId, Integer winTarget, int capacity,
                      int timeLimitSeconds, RandomWeaponMode randomWeaponMode) {
        this(roomId, hostId, hostName, modeId, arenaId, winTarget, capacity,
                new MatchOptions(winTarget, timeLimitSeconds, randomWeaponMode));
    }

    public ArcadeRoom(String roomId, UUID hostId, String hostName,
                      String modeId, String arenaId, Integer winTarget, int capacity,
                      MatchOptions options) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.hostName = hostName;
        this.modeId = modeId;
        this.arenaId = arenaId;
        this.options = options != null ? options : MatchOptions.defaults();
        this.winTarget = this.options.winTarget();
        this.capacity = Math.max(2, capacity);
        this.timeLimitSeconds = this.options.timeLimitSeconds();
        this.randomWeaponMode = this.options.randomMode();
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

    void setCapacity(int capacity) {
        this.capacity = Math.max(2, capacity);
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public boolean randomWeapons() {
        return randomWeaponMode.enabled();
    }

    public RandomWeaponMode randomWeaponMode() {
        return randomWeaponMode;
    }

    public MatchOptions options() {
        return options;
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
        preferredTeams.remove(id);
        bots.remove(id);
        return members.remove(id);
    }

    /** 以 AI 士兵身份加入；除登记进 {@link #bots} 外与真人成员完全一致。 */
    boolean addBotMember(UUID id) {
        if (!addMember(id)) {
            return false;
        }
        bots.add(id);
        return true;
    }

    /** bot 成员视图（只读副本，保持加入顺序）。 */
    public Set<UUID> botIds() {
        return new LinkedHashSet<>(bots);
    }

    public boolean isBot(UUID id) {
        return bots.contains(id);
    }

    public int botCount() {
        return bots.size();
    }

    /** 真人成员数；房主必然在内，故恒 {@code >= 1}。 */
    public int humanCount() {
        return members.size() - bots.size();
    }

    public AimModel.Difficulty botDifficulty() {
        return botDifficulty;
    }

    void setBotDifficulty(AimModel.Difficulty difficulty) {
        if (difficulty != null) {
            this.botDifficulty = difficulty;
        }
    }

    public Integer preferredTeam(UUID id) {
        return preferredTeams.get(id);
    }

    void setPreferredTeam(UUID id, int team) {
        if (members.contains(id)) {
            preferredTeams.put(id, Math.floorMod(team, 2));
        }
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
