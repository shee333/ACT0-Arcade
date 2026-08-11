package org.shee33.act0.arcade.bot.mc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.bot.AimTracker;
import org.shee33.act0.arcade.bot.Steering;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 单个 AI 士兵的武器驱动：把瞄准模型的判断变成每 tick 的朝向与扳机动作。
 *
 * <p>职责边界刻意收窄——<b>本类不选目标</b>。目标由上层（调试指令，日后的感知与战术层）指定，
 * 本类只负责"给定一个目标，如何像人一样把枪指过去并开火"。这样目标选择策略可以独立演进，
 * 而交火手感这一层保持稳定。
 *
 * <p>决策部分全在 MC-free 的 {@link AimTracker}（反应延迟、点射纪律、目标记忆、后坐力累积），
 * 本类只做三件 MC 相关的事：视线遮挡判定、朝向写入、调用 {@link BotGunBridge} 扣扳机。
 *
 * <p><b>换弹窗口是真实的。</b>早先自建弹道的方案需要人为造出"假换弹停顿"作为玩家的进攻窗口；
 * 改用 TaCZ 后弹药与换弹是真的，本类只需在 TaCZ 报告弹尽时触发换弹，
 * 那段带动画与音效的真实空档自然成为可利用的战术窗口。
 */
public final class BotWeaponController {

    /**
     * 瞄点在目标碰撞箱高度上的比例。
     *
     * <p>0.85 约为上胸／颈部：既非脚下（会被地形挡住）也非头顶（过窄导致 bot 命中率异常低），
     * 与战地系列 bot 默认瞄点一致。作为 bot 瞄点的唯一来源，避免各处各写一个魔数。
     */
    public static final double AIM_HEIGHT_RATIO = 0.85D;

    private static final AimTracker.AimOffset ZERO_OFFSET = new AimTracker.AimOffset(0.0F, 0.0F);

    private final BotPlayer bot;
    private final AimTracker aim;

    private AimTracker.AimOffset offset = ZERO_OFFSET;

    @Nullable
    private Entity target;

    public BotWeaponController(BotPlayer bot, AimModel model, long seed) {
        this.bot = Objects.requireNonNull(bot, "bot");
        this.aim = new AimTracker(Objects.requireNonNull(model, "model"), seed);
    }

    /** 目标瞄点：上胸高度。 */
    public static Vec3 aimPointOf(Entity target) {
        return target.position().add(0.0D, target.getBbHeight() * AIM_HEIGHT_RATIO, 0.0D);
    }

    /** 指定交火目标；传 {@code null} 表示脱离交火。 */
    public void setTarget(@Nullable Entity target) {
        if (this.target != target) {
            aim.forgetTarget();
            // 获得新目标时必须立刻重掷偏移。偏移原本只在开火成功后重掷，而其初值为零，
            // 于是每场交火的第一枪都精确命中——正是"探头即被爆头"这种必须避免的手感。
            // 此处紧接 forgetTarget() 调用，因此掷出的是"初见目标"那个最大的误差圆内的偏移。
            offset = target == null ? ZERO_OFFSET : aim.rollAimOffset();
        }
        this.target = target;
    }

    @Nullable
    public Entity target() {
        return target;
    }

    public AimTracker aimTracker() {
        return aim;
    }

    /**
     * 推进一 tick：更新瞄准、写入朝向、按节奏开火。
     *
     * <p>必须在世界实体循环之前调用，与移动驱动同处 {@code ServerTickEvent.Phase.START}。
     */
    public void tick() {
        if (!isTargetEngageable()) {
            aim.tick(false);
            if (!aim.hasTargetMemory()) {
                target = null;
                BotGunBridge.aim(bot, false);
            }
            return;
        }

        Vec3 eye = bot.getEyePosition();
        Vec3 aimPoint = aimPointOf(target);
        double dx = aimPoint.x - eye.x;
        double dy = aimPoint.y - eye.y;
        double dz = aimPoint.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = Steering.yawToward(dx, dz);
        float idealPitch = Steering.pitchToward(dy, horizontal);

        AimModel model = aim.model();
        boolean inFov = model.withinFov(Steering.angleBetween(bot.getYRot(), idealYaw));
        boolean visible = inFov && hasClearLineOfSight(eye, aimPoint);
        aim.tick(visible);

        // 朝向朝"理想方向 + 当前误差偏移"转动：偏移只在每次射击后重掷，
        // 因此转动是平滑的，而非每 tick 抖动（后者观感像抽搐而非瞄不准）。
        float targetYaw = idealYaw + offset.yawDegrees();
        float targetPitch = idealPitch + offset.pitchDegrees();
        float rate = model.turnRateDegPerTick();
        applyRotation(Steering.turnToward(bot.getYRot(), targetYaw, rate),
                Steering.turnToward(bot.getXRot(), targetPitch, rate));

        BotGunBridge.aim(bot, true);
        if (visible) {
            tryFire();
        }
    }

    private void tryFire() {
        if (!aim.canFire() || !weaponReady()) {
            return;
        }
        String result = BotGunBridge.shoot(bot, bot.getXRot(), bot.getYRot());
        if (BotGunBridge.RESULT_SUCCESS.equals(result)) {
            aim.onShotFired();
            offset = aim.rollAimOffset();
            return;
        }
        // 弹尽与待上膛不是错误，而是应当自动处理的正常状态；其余结果交由上层排查。
        switch (result) {
            case "NO_AMMO" -> BotGunBridge.reload(bot);
            case "NEED_BOLT" -> BotGunBridge.bolt(bot);
            default -> {
            }
        }
    }

    private boolean weaponReady() {
        return BotGunBridge.shootCoolDownMillis(bot) <= 0L
                && !BotGunBridge.isReloading(bot)
                && !BotGunBridge.isBolting(bot);
    }

    private boolean isTargetEngageable() {
        return target != null && target.isAlive() && !target.isRemoved()
                && target.level() == bot.level();
    }

    /**
     * 眼到瞄点之间是否无方块遮挡。
     *
     * <p>只判方块不判实体：队友挡住射线时依然允许开火，与真人行为一致
     * （友伤由 {@code ArcadeMatch.shouldCancelDamage} 另行裁决）。
     */
    private boolean hasClearLineOfSight(Vec3 from, Vec3 to) {
        HitResult hit = bot.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bot));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void applyRotation(float yaw, float pitch) {
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
        bot.setXRot(pitch);
    }
}
