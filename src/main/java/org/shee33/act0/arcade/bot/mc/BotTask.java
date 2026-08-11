package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.bot.AimModel;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * 单个在场 AI 士兵的完整状态：躯体、武器控制器、难度档、移动航点。
 *
 * <p>包内可见，由 {@link BotManager} 独占持有与驱动。独立成类型是因为它已不只是一个数据袋
 * ——"按当前配置重建武器控制器"这件事有真实的语义（见 {@link #rebuildWeapon}）。
 */
final class BotTask {

    /** 新生成 bot 的默认难度档。 */
    static final AimModel.Difficulty DEFAULT_DIFFICULTY = AimModel.Difficulty.NORMAL;

    /**
     * 行进时每 tick 最大转向角（度）。
     *
     * <p>与 {@link AimModel#turnRateDegPerTick()} 刻意分开：那是交火时枪口追踪目标的速率、
     * 属难度参数；这是行军时身体的转向速率，与强弱无关，四档共用一个值即可。
     */
    private static final float MOVE_TURN_RATE = 12.0F;

    /** 抵达判定半径（格）。小于玩家碰撞箱宽度会导致到点后反复微调抖动。 */
    private static final double ARRIVE_RADIUS = 0.6D;

    /**
     * 目标重新评估间隔（tick）。
     *
     * <p>感知含 raycast，不应每 tick 对每个 bot 都跑一遍；0.5 秒重评一次已远快于人眼能察觉的
     * 反应差异，而黏滞（见 {@link org.shee33.act0.arcade.bot.TargetScoring}）会吸收掉
     * 评估间隔带来的抖动。
     */
    private static final int TARGET_SCAN_INTERVAL_TICKS = 10;

    final BotPlayer bot;
    BotWeaponController weapon;
    AimModel.Difficulty difficulty = DEFAULT_DIFFICULTY;

    /**
     * 当前移动航点。
     *
     * <p>刻意不叫 {@code target}：那会与武器控制器的<b>交火目标</b>混淆，而两者在交火时是竞争
     * 朝向控制权的（见 {@link BotManager} 的驱动循环）。
     */
    @Nullable
    Vec3 waypoint;

    /** 是否自主选择交火目标；关闭时目标只能由 {@code /arcade bot engage} 指定。 */
    boolean autoTarget;

    BotTask(BotPlayer bot) {
        this.bot = bot;
        this.weapon = newWeapon(bot, DEFAULT_DIFFICULTY);
    }

    /**
     * 推进本 bot 一 tick：按需重评目标 → 驱动武器 → 驱动移动。
     *
     * <p>次序不可调换：武器控制器会写入朝向，而移动驱动也会——交火时必须让武器的写入生效。
     */
    void tick(MinecraftServer server, Predicate<ServerPlayer> hostility) {
        if (autoTarget) {
            scanForTarget(server, hostility);
        }
        weapon.tick();

        // 交火时由武器控制器独占朝向——士兵瞄的是敌人，不是航点。
        // 因此此时抑制移动，bot 停下开火；"行进中侧向射击"要等导航层引入侧移后
        // 才能把前进量与朝向解耦，现在强行做只会得到边走边打却朝着航点开枪的错误画面。
        if (weapon.target() != null) {
            BotMovementDriver.halt(bot);
            return;
        }
        if (waypoint == null) {
            return;
        }
        boolean arrived = BotMovementDriver.driveTo(bot,
                waypoint.x, waypoint.y, waypoint.z, MOVE_TURN_RATE, ARRIVE_RADIUS, false);
        if (arrived) {
            waypoint = null;
        }
    }

    /**
     * 按间隔重新评估交火目标。
     *
     * <p>用 bot 的实体 id 给扫描相位错开，避免所有 bot 在同一 tick 一起做 raycast 造成周期性尖峰。
     *
     * <p><b>扫描无果时刻意不清空目标。</b>清空会绕过 {@link org.shee33.act0.arcade.bot.AimTracker}
     * 的目标记忆——那正是"敌人闪进掩体后 bot 仍短暂压制、玩家绕后需要够快"这一玩法赖以存在的机制。
     * 目标的过期交由 {@link BotWeaponController} 按 {@code reacquireTicks} 判定。
     */
    private void scanForTarget(MinecraftServer server, Predicate<ServerPlayer> hostility) {
        if ((server.getTickCount() + bot.getId()) % TARGET_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        ServerPlayer found = BotPerception.findTarget(
                bot, weapon.aimTracker().model(), hostility, weapon.target());
        if (found != null && found != weapon.target()) {
            weapon.setTarget(found);
        }
    }

    /**
     * 用当前配置里的生效参数重建武器控制器，并沿用原交火目标。
     *
     * <p>沿用目标是为了避免"调难度顺手让 bot 脱战"这种意外副作用；重建则是因为难度参数在
     * {@link org.shee33.act0.arcade.bot.AimTracker} 内不可变。
     */
    void rebuildWeapon(AimModel.Difficulty difficulty) {
        Entity current = weapon.target();
        this.weapon = newWeapon(bot, difficulty);
        this.weapon.setTarget(current);
        this.difficulty = difficulty;
    }

    /**
     * 某档的生效参数一律走注册表（配置可覆盖），不直接读枚举内置默认值。
     *
     * <p>随机种子取自 bot 的 UUID：bot 身份稳定（见 {@link org.shee33.act0.arcade.bot.BotNames}），
     * 故其点射与瞄准抖动模式跨重启可复现，便于对着同一个 bot 反复排查手感问题。
     */
    private static BotWeaponController newWeapon(BotPlayer bot, AimModel.Difficulty difficulty) {
        AimModel model = Act0Arcade.services().botDifficulty().get(difficulty);
        return new BotWeaponController(bot, model, bot.getUUID().hashCode());
    }
}
