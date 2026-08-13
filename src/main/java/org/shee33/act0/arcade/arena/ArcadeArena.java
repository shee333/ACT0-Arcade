package org.shee33.act0.arcade.arena;

import org.shee33.act0.arcade.round.RespawnPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 竞技场：一张地图上各参战方的出生点、个人乱斗的手动复活候选点，以及离场返回点。MC-free 纯数据。
 *
 * <p>设计为"方索引 → 出生点"，与 {@link org.shee33.act0.arcade.mode.MatchSettings#sideCount()} 对应：
 * <ul>
 *   <li>决斗/团队：每方一个固定出生点（{@code sideSpawns.get(sideIndex)}）。</li>
 *   <li>个人乱斗：使用 {@link #randomSpawns} 手动候选池取点。</li>
 *   <li>热区模式：使用 {@link #hotZones} 这一组管理员手动框选的矩形区域（可为空表示未设置），
 *       与 {@link #randomSpawns} 完全独立，不复用出生点。只预设一个即为全程固定热区；预设多个则
 *       由 {@link HotZoneRotation} 在对局中按固定时长顺序轮换，同一时刻只有一个热区生效。</li>
 * </ul>
 */
public final class ArcadeArena {

    private final String arenaId;
    private final List<SpawnPoint> sideSpawns;
    private final List<SpawnPoint> randomSpawns;
    private final SpawnPoint returnSpawn;
    private final List<HotZoneArea> hotZones;

    public ArcadeArena(String arenaId,
                       List<SpawnPoint> sideSpawns,
                       List<SpawnPoint> randomSpawns,
                       SpawnPoint returnSpawn) {
        this(arenaId, sideSpawns, randomSpawns, returnSpawn, List.of());
    }

    public ArcadeArena(String arenaId,
                       List<SpawnPoint> sideSpawns,
                       List<SpawnPoint> randomSpawns,
                       SpawnPoint returnSpawn,
                       List<HotZoneArea> hotZones) {
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId");
        this.sideSpawns = List.copyOf(Objects.requireNonNull(sideSpawns, "sideSpawns"));
        this.randomSpawns = randomSpawns == null ? List.of() : List.copyOf(randomSpawns);
        this.returnSpawn = returnSpawn;
        this.hotZones = hotZones == null ? List.of() : List.copyOf(hotZones);
    }

    public String arenaId() {
        return arenaId;
    }

    /** 某一方的固定出生点；索引越界时回退到第 0 个。 */
    public SpawnPoint sideSpawn(int sideIndex) {
        if (sideSpawns.isEmpty()) {
            return returnSpawn;
        }
        int i = Math.floorMod(sideIndex, sideSpawns.size());
        return sideSpawns.get(i);
    }

    public List<SpawnPoint> sideSpawns() {
        return Collections.unmodifiableList(sideSpawns);
    }

    public List<SpawnPoint> randomSpawns() {
        return Collections.unmodifiableList(randomSpawns);
    }

    /** 离场返回点（大厅/出生岛）。 */
    public SpawnPoint returnSpawn() {
        return returnSpawn;
    }

    public boolean hasReturnSpawn() {
        return returnSpawn != null;
    }

    /** 热区模式的预设区域列表，按管理员框选顺序排列，也就是对局中的轮换顺序；可能为空。 */
    public List<HotZoneArea> hotZones() {
        return Collections.unmodifiableList(hotZones);
    }

    public boolean hasHotZones() {
        return !hotZones.isEmpty();
    }

    /**
     * 按索引取热区，索引越界时按热区数量取模回绕——{@link HotZoneRotation} 的索引本就在
     * {@code [0, size)} 内，这里的取模只是防御性兜底。热区列表为空时返回 {@code null}。
     */
    public HotZoneArea hotZone(int index) {
        if (hotZones.isEmpty()) {
            return null;
        }
        return hotZones.get(Math.floorMod(index, hotZones.size()));
    }

    /**
     * 校验该竞技场是否满足某复活策略所需的出生点数量。
     *
     * @param policy    复活策略
     * @param sideCount 参战方数
     * @return 校验结果（含失败原因）
     */
    public Validation validateFor(RespawnPolicy policy, int sideCount) {
        if (returnSpawn == null) {
            return Validation.fail("需要先手动设置对局结束返回点");
        }
        if (policy == RespawnPolicy.RANDOM) {
            if (randomSpawns.isEmpty()) {
                return Validation.fail("个人乱斗需要至少 1 个手动复活点");
            }
            return Validation.ok();
        }
        if (sideSpawns.size() < sideCount) {
            return Validation.fail("需要 " + sideCount + " 个出生点，当前仅 " + sideSpawns.size() + " 个");
        }
        return Validation.ok();
    }

    /** 简单的校验结果。 */
    public static final class Validation {
        private final boolean valid;
        private final String reason;

        private Validation(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static Validation ok() {
            return new Validation(true, null);
        }

        public static Validation fail(String reason) {
            return new Validation(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String reason() {
            return reason;
        }
    }

    /** 便捷构造器。 */
    public static final class Builder {
        private final String arenaId;
        private final List<SpawnPoint> sideSpawns = new ArrayList<>();
        private final List<SpawnPoint> randomSpawns = new ArrayList<>();
        private SpawnPoint returnSpawn;
        private final List<HotZoneArea> hotZones = new ArrayList<>();

        public Builder(String arenaId) {
            this.arenaId = arenaId;
        }

        public Builder addSideSpawn(SpawnPoint spawn) {
            this.sideSpawns.add(spawn);
            return this;
        }

        public Builder addRandomSpawn(SpawnPoint spawn) {
            this.randomSpawns.add(spawn);
            return this;
        }

        public Builder returnSpawn(SpawnPoint spawn) {
            this.returnSpawn = spawn;
            return this;
        }

        public Builder addHotZone(HotZoneArea hotZone) {
            if (hotZone != null) {
                this.hotZones.add(hotZone);
            }
            return this;
        }

        public Builder hotZones(List<HotZoneArea> zones) {
            this.hotZones.clear();
            if (zones != null) {
                zones.forEach(this::addHotZone);
            }
            return this;
        }

        public ArcadeArena build() {
            return new ArcadeArena(arenaId, sideSpawns, randomSpawns, returnSpawn, hotZones);
        }
    }
}
