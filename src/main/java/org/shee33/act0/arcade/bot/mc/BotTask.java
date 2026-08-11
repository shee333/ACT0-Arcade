package org.shee33.act0.arcade.bot.mc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.bot.AimModel;

import javax.annotation.Nullable;

/**
 * 单个在场 AI 士兵的完整状态：躯体、武器控制器、难度档、移动航点。
 *
 * <p>包内可见，由 {@link BotManager} 独占持有与驱动。独立成类型是因为它已不只是一个数据袋
 * ——"按当前配置重建武器控制器"这件事有真实的语义（见 {@link #rebuildWeapon}）。
 */
final class BotTask {

    /** 新生成 bot 的默认难度档。 */
    static final AimModel.Difficulty DEFAULT_DIFFICULTY = AimModel.Difficulty.NORMAL;

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

    BotTask(BotPlayer bot) {
        this.bot = bot;
        this.weapon = newWeapon(bot, DEFAULT_DIFFICULTY);
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
