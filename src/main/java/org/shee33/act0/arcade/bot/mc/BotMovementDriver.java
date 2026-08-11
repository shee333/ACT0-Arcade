package org.shee33.act0.arcade.bot.mc;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.shee33.act0.arcade.bot.Steering;

/**
 * AI 士兵的移动驱动：把"走到某个坐标"翻译成原版物理认得的每 tick 输入。
 *
 * <p><b>刻意不自研物理。</b>本类只写三样东西——朝向、前进量、跳跃意图——之后的重力、碰撞、
 * 摩擦、台阶攀爬全部交给原版 {@code LivingEntity.travel} 处理（其调用链由
 * {@link BotPlayer#tick()} 补齐）。自研玩家物理不仅工作量大，还必然与真人玩家的手感产生偏差，
 * 而 bot 与玩家手感不一致是会被立刻察觉的出戏点。
 *
 * <p>必须在世界实体循环推进之前调用（{@code ServerTickEvent.Phase.START}），
 * 否则本 tick 写入的意图要到下一 tick 才生效，表现为操控延迟一帧。
 */
public final class BotMovementDriver {

    /**
     * 允许起步前进的最大朝向偏差（度）。
     *
     * <p>偏差超过此值时原地转身而不前进。人在转身超过约 60° 时会先转再走，
     * 而不是侧着身子画弧线过去——后者正是"AI 味"的典型来源。
     */
    private static final float FORWARD_GATE_DEGREES = 60.0F;

    /** 原版玩家的自动跨步高度为 0.6，跨越整块（1.0）必须起跳。 */
    private static final float FULL_FORWARD = 1.0F;

    private BotMovementDriver() {
    }

    /**
     * 驱动 bot 朝目标点行走一 tick。
     *
     * @param bot          目标 bot
     * @param tx           目标 X
     * @param ty           目标 Y（用于俯仰，使行走姿态自然）
     * @param tz           目标 Z
     * @param turnRate     每 tick 最大转向角（度），同时也是日后瞄准模型的难度旋钮
     * @param arriveRadius 判定抵达的水平半径
     * @param sprint       是否疾跑
     * @return 是否已抵达目标
     */
    public static boolean driveTo(BotPlayer bot,
                                  double tx, double ty, double tz,
                                  float turnRate,
                                  double arriveRadius,
                                  boolean sprint) {
        double dx = tx - bot.getX();
        double dz = tz - bot.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal <= arriveRadius) {
            halt(bot);
            return true;
        }

        float desiredYaw = Steering.yawToward(dx, dz);
        float newYaw = Steering.turnToward(bot.getYRot(), desiredYaw, turnRate);
        applyYaw(bot, newYaw);

        float desiredPitch = Steering.pitchToward(ty - bot.getEyeY(), horizontal);
        bot.setXRot(Steering.turnToward(bot.getXRot(), desiredPitch, turnRate));

        // 速度取自移动速度属性，与真人玩家同源，保证 bot 不会莫名比玩家快或慢。
        bot.setSpeed((float) bot.getAttributeValue(Attributes.MOVEMENT_SPEED));
        bot.setSprinting(sprint);

        boolean facingTarget = Steering.angleBetween(newYaw, desiredYaw) <= FORWARD_GATE_DEGREES;
        bot.zza = facingTarget ? FULL_FORWARD : 0.0F;
        bot.xxa = 0.0F;

        // 原版的自动跨步只到 0.6，整块高的坎必须起跳；撞墙时起跳也能顺带脱离卡角。
        bot.setJumping(bot.horizontalCollision && bot.onGround());
        return false;
    }

    /**
     * 朝世界方向移动一 tick，<b>不接管朝向</b>——交火时朝向归瞄准所有。
     *
     * <p>分解基准取 {@code getYRot()} 而非 {@code getYBodyRot()}：原版
     * {@code Entity.moveRelative} 内部正是用 {@code getYRot()} 旋转移动输入，
     * 取错基准会让 bot 朝着与期望呈一定夹角的方向漂走。
     *
     * <p>疾跑仅在以前进为主时开启：原版的疾跑速度加成本就为向前冲刺设计，
     * 横着疾跑既不自然，也会让侧移压制的节奏快到玩家来不及反应。
     *
     * @param dirX 期望位移的世界 X 分量，无需归一化
     * @param dirZ 期望位移的世界 Z 分量，无需归一化
     */
    public static void driveRelative(BotPlayer bot, double dirX, double dirZ, boolean sprint) {
        Steering.MoveInput input = Steering.moveInput(dirX, dirZ, bot.getYRot());
        if (input.equals(Steering.MoveInput.ZERO)) {
            halt(bot);
            return;
        }
        bot.setSpeed((float) bot.getAttributeValue(Attributes.MOVEMENT_SPEED));
        bot.setSprinting(sprint && input.forward() > 0.8F);
        bot.zza = input.forward();
        bot.xxa = input.strafe();
        bot.setJumping(bot.horizontalCollision && bot.onGround());
    }

    /** 停止移动，但保留当前朝向。 */
    public static void halt(BotPlayer bot) {
        bot.zza = 0.0F;
        bot.xxa = 0.0F;
        bot.setJumping(false);
        bot.setSprinting(false);
    }

    /**
     * 同步设置偏航、头部与躯干朝向。
     *
     * <p>只设 {@code yRot} 会让其他玩家看到 bot 的身体朝向与移动方向脱节——头朝前、身子横着走，
     * 是最廉价也最刺眼的"假人感"来源之一。
     */
    private static void applyYaw(BotPlayer bot, float yaw) {
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
    }
}
