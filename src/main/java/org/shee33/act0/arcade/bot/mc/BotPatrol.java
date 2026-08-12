package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;
import org.shee33.act0.arcade.match.ArcadeMatch;
import org.shee33.act0.arcade.storage.ArenaRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 无敌可寻时的巡逻去向：在本场竞技场的出生点之间往复移动。
 *
 * <p><b>为什么必须有巡逻。</b>没有它，搜索半径内一个敌人都没有的 bot 会原地站住不动
 * ——玩家看到的是满场雕像。而竞技场的出生点本就是设计时铺开在全图的，直接拿来当巡逻点，
 * 既不需要额外的路点数据，也天然覆盖了地图的主要通路。
 *
 * <p><b>只在对局内巡逻。</b>巡逻点取自对局所用的竞技场；裸生成的调试 bot 不属于任何对局，
 * 保持原地不动反而便于定点观察，故此处如实返回"无处可去"。
 */
final class BotPatrol {

    /** 认定"已到达"巡逻点的水平半径（格）。比抵达判定宽，避免为了踩准一个点反复微调。 */
    private static final double REACH_RADIUS = 3.0D;

    private final BotPlayer bot;
    private final Random random;

    /** 巡逻点缓存。解析要查对局与竞技场注册表，不该每 tick 做一遍。 */
    @Nullable
    private List<Vec3> points;

    /** 已尝试解析过：区分"还没查"与"查过但这个 bot 不在对局里"，避免反复空查。 */
    private boolean resolved;

    @Nullable
    private Vec3 destination;

    BotPatrol(BotPlayer bot) {
        this.bot = bot;
        // 种子取自 bot 身份，使其巡逻路线跨重启可复现，便于排查"它怎么老往那边走"。
        this.random = new Random(bot.getUUID().hashCode());
    }

    /**
     * 当前巡逻去向；已抵达则就地换下一个。
     *
     * @return 巡逻目标；本 bot 不在对局中或竞技场没有可用点时返回 {@code null}
     */
    @Nullable
    Vec3 destination(MinecraftServer server) {
        List<Vec3> all = points(server);
        if (all.isEmpty()) {
            return null;
        }
        if (destination == null || reached(destination)) {
            destination = pick(all);
        }
        return destination;
    }

    /** 放弃当前巡逻点（例如无路可走），下次调用会另选。 */
    void abandon() {
        destination = null;
    }

    private boolean reached(Vec3 target) {
        double dx = target.x - bot.getX();
        double dz = target.z - bot.getZ();
        return dx * dx + dz * dz <= REACH_RADIUS * REACH_RADIUS;
    }

    /**
     * 随机挑一个巡逻点，但排除脚下这个。
     *
     * <p>不排除的话，随机有相当概率选中当前所在点，{@link #reached} 立刻成立、
     * 于是每 tick 换一个点却始终不动——表现与没有巡逻毫无区别。
     */
    private Vec3 pick(List<Vec3> all) {
        List<Vec3> candidates = new ArrayList<>(all.size());
        for (Vec3 point : all) {
            if (!reached(point)) {
                candidates.add(point);
            }
        }
        if (candidates.isEmpty()) {
            return all.get(random.nextInt(all.size()));
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private List<Vec3> points(MinecraftServer server) {
        if (resolved) {
            return points == null ? List.of() : points;
        }
        resolved = true;
        points = resolvePoints(server);
        return points;
    }

    private List<Vec3> resolvePoints(MinecraftServer server) {
        ArcadeMatch match = Act0Arcade.services().matches().matchOf(bot.getUUID());
        if (match == null) {
            return List.of();
        }
        ArcadeArena arena = ArenaRegistry.get(server).find(match.arenaId()).orElse(null);
        if (arena == null) {
            return List.of();
        }
        List<Vec3> collected = new ArrayList<>();
        for (SpawnPoint spawn : arena.sideSpawns()) {
            collected.add(new Vec3(spawn.x(), spawn.y(), spawn.z()));
        }
        for (SpawnPoint spawn : arena.randomSpawns()) {
            collected.add(new Vec3(spawn.x(), spawn.y(), spawn.z()));
        }
        return List.copyOf(collected);
    }
}
