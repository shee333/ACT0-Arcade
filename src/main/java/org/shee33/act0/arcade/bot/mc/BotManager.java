package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.bot.BotNames;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * AI 士兵的在场注册表与每 tick 驱动循环。
 *
 * <p>阶段 0 只承载"生成 / 走到某点 / 撤走"这三件事，用于验证假玩家躯体与移动驱动是否可用；
 * 决策层（感知、目标选择、导航、瞄准）将来接在 {@link #drive} 之上，而不是塞进本类
 * ——本类属于 Body 层，只负责让意图变成动作，不负责产生意图。
 *
 * <p>所有公开方法必须在服务器主线程调用。
 */
public final class BotManager {

    public static final BotManager INSTANCE = new BotManager();

    /** 在场 bot 及其当前指令；用 LinkedHashMap 保证命令回显顺序稳定。 */
    private final Map<UUID, BotTask> tasks = new LinkedHashMap<>();

    /**
     * 敌我判定谓词。
     *
     * <p>默认混战——除自己以外皆为敌，便于用两个 bot 直接验证自主交火。阶段 1 接入对局后由
     * {@code ArcadeMatch} 的阵营判定替换，感知层（{@link BotPerception}）无需改动。
     */
    private Predicate<ServerPlayer> hostility = player -> true;

    private BotManager() {
    }

    // ---------------- 生命周期 ----------------

    /**
     * 生成一个 AI 士兵并纳入驱动循环。
     *
     * @return 生成的 bot；同名者已在场时返回 {@code null}
     */
    @Nullable
    public BotPlayer spawn(MinecraftServer server, ServerLevel level, String name,
                           double x, double y, double z, float yaw, float pitch) {
        BotPlayer bot = BotSpawner.spawn(server, level, name, x, y, z, yaw, pitch);
        if (bot == null) {
            return null;
        }
        tasks.put(bot.getUUID(), new BotTask(bot));
        return bot;
    }

    /** 按名字撤走一个 bot；返回是否确有其人。 */
    public boolean despawn(MinecraftServer server, String name) {
        BotTask task = tasks.remove(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        BotSpawner.despawn(server, task.bot);
        return true;
    }

    /** 撤走全部 bot；返回撤走数量。 */
    public int despawnAll(MinecraftServer server) {
        List<BotTask> all = new ArrayList<>(tasks.values());
        tasks.clear();
        for (BotTask task : all) {
            BotSpawner.despawn(server, task.bot);
        }
        return all.size();
    }

    // ---------------- 指令 ----------------

    /** 命令某个 bot 走向目标点；返回是否确有其人。 */
    public boolean walkTo(String name, double x, double y, double z) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        task.waypoint = new Vec3(x, y, z);
        return true;
    }

    /** 命令某个 bot 停下；返回是否确有其人。 */
    public boolean stop(String name) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        task.waypoint = null;
        BotMovementDriver.halt(task.bot);
        return true;
    }

    /** 命令某个 bot 与目标交火；{@code target} 为 {@code null} 表示脱离。返回是否确有其人。 */
    public boolean engage(String name, @Nullable Entity target) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        task.weapon.setTarget(target);
        return true;
    }

    /**
     * 切换某个 bot 的难度档位；返回是否确有其人。
     *
     * <p>难度参数在 {@link org.shee33.act0.arcade.bot.AimTracker} 内不可变，故整体重建武器控制器，
     * 并沿用原交火目标——避免"调难度顺手让 bot 脱战"这种意外副作用。
     */
    public boolean setDifficulty(String name, AimModel.Difficulty difficulty) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        task.rebuildWeapon(difficulty);
        return true;
    }

    /**
     * 用当前配置里的生效参数重建全部在场 bot 的武器控制器，保持各自档位与交火目标。
     *
     * <p>难度参数重载后必须调用，否则已在场的 bot 仍用旧参数——调参循环会在"改了 JSON
     * 却看不到变化"这一步断掉，而快速迭代正是把参数做成配置的全部理由。
     *
     * @return 受影响的 bot 数
     */
    public int refreshDifficulties() {
        for (BotTask task : tasks.values()) {
            task.rebuildWeapon(task.difficulty);
        }
        return tasks.size();
    }

    /** 开关某个 bot 的自主选目标；返回是否确有其人。 */
    public boolean setAutoTarget(String name, boolean enabled) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        if (task == null) {
            return false;
        }
        task.autoTarget = enabled;
        if (!enabled) {
            task.weapon.setTarget(null);
        }
        return true;
    }

    /** 某个 bot 是否开启了自主选目标。 */
    public boolean isAutoTarget(String name) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        return task != null && task.autoTarget;
    }

    /** 替换敌我判定谓词；阶段 1 接入对局时由对局侧注入阵营判定。 */
    public void setHostility(Predicate<ServerPlayer> hostility) {
        this.hostility = Objects.requireNonNull(hostility, "hostility");
    }

    /** 某个 bot 当前难度档；不在场返回 {@code null}。 */
    @Nullable
    public AimModel.Difficulty difficultyOf(String name) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        return task != null ? task.difficulty : null;
    }

    /** 按名字取在场 bot；不在场返回 {@code null}。 */
    @Nullable
    public BotPlayer find(String name) {
        BotTask task = tasks.get(BotNames.uuidOf(name));
        return task != null ? task.bot : null;
    }

    /** 当前在场 bot 名，按生成顺序。 */
    public List<String> activeNames() {
        List<String> names = new ArrayList<>(tasks.size());
        for (BotTask task : tasks.values()) {
            names.add(task.bot.getGameProfile().getName());
        }
        return names;
    }

    // ---------------- 驱动循环 ----------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 必须在 START 阶段：世界的实体循环随后才会推进物理，此时写入的意图才能在本 tick 生效。
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        drive(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        despawnAll(event.getServer());
    }

    private void drive(MinecraftServer server) {
        if (tasks.isEmpty()) {
            return;
        }
        tasks.values().removeIf(task -> isStale(server, task));
        for (BotTask task : tasks.values()) {
            task.tick(server, hostility);
        }
    }

    /**
     * bot 是否已不再有效。
     *
     * <p>bot 可能被外部途径移除（管理员 {@code /kick}、维度卸载、异常清理）。
     * 继续驱动一个已从世界剥离的实体会静默失效并泄漏引用，因此每 tick 做一次归属校验。
     */
    private static boolean isStale(MinecraftServer server, BotTask task) {
        return task.bot.isRemoved()
                || server.getPlayerList().getPlayer(task.bot.getUUID()) != task.bot;
    }

}
