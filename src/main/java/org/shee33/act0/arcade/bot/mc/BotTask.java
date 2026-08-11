package org.shee33.act0.arcade.bot.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.bot.AimModel;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * 单个在场 AI 士兵的完整状态：躯体、武器控制器、难度档、位移层。
 *
 * <p>包内可见，由 {@link BotManager} 独占持有与驱动。独立成类型是因为它已不只是一个数据袋
 * ——"按当前配置重建武器控制器"这件事有真实的语义（见 {@link #rebuildWeapon}）。
 */
final class BotTask {

    /** 新生成 bot 的默认难度档。 */
    static final AimModel.Difficulty DEFAULT_DIFFICULTY = AimModel.Difficulty.NORMAL;

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

    /** 是否自主选择交火目标与移动目标；关闭时只听 {@code /arcade bot} 调试指令。 */
    boolean autoTarget;

    private final BotLocomotion locomotion;

    /**
     * 自主移动的去向：最近的敌人，<b>不要求可见</b>。
     *
     * <p>与交火目标分开保存：交火目标必须看得见（否则就是穿墙射击），而行军方向不该受视线限制
     * ——若两者共用一个字段，bot 在看不到敌人时就没有去向，只能站桩。
     */
    @Nullable
    private ServerPlayer moveGoal;

    BotTask(BotPlayer bot) {
        this.bot = bot;
        this.weapon = newWeapon(bot, DEFAULT_DIFFICULTY);
        this.locomotion = new BotLocomotion(bot);
    }

    /**
     * 推进本 bot 一 tick：按需重评目标 → 驱动武器 → 驱动位移。
     *
     * <p>次序不可调换：武器控制器会写入朝向，位移层需要据此决定"朝向已被瞄准占用"，
     * 从而改用侧移分解而非转身行走。
     */
    void tick(MinecraftServer server, BiPredicate<ServerPlayer, ServerPlayer> hostility) {
        if (autoTarget) {
            rescan(server, hostility);
        }
        weapon.tick();
        boolean engaged = weapon.target() != null;

        // 手动航点优先：调试指令下的 walk 必须能压过自主战斗移动，否则无法定点验证移动行为。
        if (locomotion.waypoint() != null) {
            locomotion.tickWaypoint(server, engaged);
            return;
        }
        if (autoTarget) {
            locomotion.tickCombat(server, moveGoal, engaged);
        }
    }

    /** 设定移动航点。 */
    void setWaypoint(@Nullable Vec3 target) {
        locomotion.setWaypoint(target);
    }

    /** bot 撤走时释放导航资源，避免影子 Mob 随之泄漏。 */
    void releaseNavigation() {
        locomotion.release();
    }

    /**
     * 按间隔重新评估交火目标与移动去向。
     *
     * <p>用 bot 的实体 id 给扫描相位错开，避免所有 bot 在同一 tick 一起做 raycast 造成周期性尖峰。
     *
     * <p><b>扫描无果时刻意不清空交火目标。</b>清空会绕过
     * {@link org.shee33.act0.arcade.bot.AimTracker} 的目标记忆——那正是"敌人闪进掩体后 bot 仍
     * 短暂压制、玩家绕后需要够快"这一玩法赖以存在的机制。目标的过期交由
     * {@link BotWeaponController} 按 {@code reacquireTicks} 判定。
     */
    private void rescan(MinecraftServer server, BiPredicate<ServerPlayer, ServerPlayer> hostility) {
        if ((server.getTickCount() + bot.getId()) % TARGET_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        // 把本 bot 绑为提问方：敌我关系取决于是谁在问，感知层只需要一个单参谓词。
        ServerPlayer found = BotPerception.findTarget(
                bot, weapon.aimTracker().model(), candidate -> hostility.test(bot, candidate),
                weapon.target());
        if (found != null && found != weapon.target()) {
            weapon.setTarget(found);
        }
        moveGoal = BotPerception.findNearestHostile(
                bot, candidate -> hostility.test(bot, candidate), BotPerception.DEFAULT_SEARCH_RADIUS);
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
