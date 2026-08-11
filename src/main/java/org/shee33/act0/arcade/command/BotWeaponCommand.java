package org.shee33.act0.arcade.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.bot.AimModel;
import org.shee33.act0.arcade.bot.Steering;
import org.shee33.act0.arcade.bot.mc.BotGunBridge;
import org.shee33.act0.arcade.bot.mc.BotManager;
import org.shee33.act0.arcade.bot.mc.BotPerception;
import org.shee33.act0.arcade.bot.mc.BotPlayer;
import org.shee33.act0.arcade.bot.mc.BotWeaponController;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.loadout.LoadoutRuleset;
import org.shee33.act0.arcade.match.ArcadeServices;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /arcade bot} 的交火相关子命令：发枪、单发试射、持续交火、难度切换。
 *
 * <p>与 {@link BotCommand}（生成／移动／生命周期）分文件，既按主题划分，也让两个文件都留在
 * {@code AGENTS.md} 的 250 行上限内。
 */
public final class BotWeaponCommand {

    private static final SuggestionProvider<CommandSourceStack> ACTIVE_BOTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(BotManager.INSTANCE.activeNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTIES = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (AimModel.Difficulty difficulty : AimModel.Difficulty.values()) {
            names.add(difficulty.name().toLowerCase(Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private BotWeaponCommand() {
    }

    /** 把交火子命令挂到 {@code bot} 分支上。 */
    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> bot) {
        return bot
                .then(Commands.literal("gun")
                        .then(botArg().executes(BotWeaponCommand::giveGun)))
                .then(Commands.literal("fireat")
                        .then(botArg()
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(BotWeaponCommand::fireOnce))))
                .then(Commands.literal("engage")
                        .then(botArg()
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(BotWeaponCommand::engage))))
                .then(Commands.literal("disengage")
                        .then(botArg().executes(BotWeaponCommand::disengage)))
                .then(Commands.literal("auto")
                        .then(botArg()
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(BotWeaponCommand::setAuto))))
                .then(Commands.literal("difficulty")
                        .then(botArg()
                                .then(Commands.argument("tier", StringArgumentType.word()).suggests(DIFFICULTIES)
                                        .executes(BotWeaponCommand::setDifficulty))))
                .then(Commands.literal("reload").executes(BotWeaponCommand::reloadTuning));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> botArg() {
        return Commands.argument("name", StringArgumentType.word()).suggests(ACTIVE_BOTS);
    }

    private static int giveGun(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BotPlayer bot = BotManager.INSTANCE.find(name);
        if (bot == null) {
            return BotCommand.notFound(ctx, name);
        }
        ArcadeServices services = Act0Arcade.services();
        services.applier().apply(bot,
                DefaultLoadoutCatalog.defaultLoadout(services.registry()),
                LoadoutRuleset.ARCADE,
                ArcadePlayerUnlocks.get(ctx.getSource().getServer()).unlocked(bot.getUUID()),
                true);
        boolean drawn = BotGunBridge.drawMainHand(bot);
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + name + " §a已发放默认配装；TaCZ 持枪登记："
                + (drawn ? "§a成功" : "§c失败（TaCZ 未安装？）")), true);
        return drawn ? 1 : 0;
    }

    /**
     * 单发试射：绕过瞄准模型直接扣一次扳机，并回显 TaCZ 的结果枚举名。
     *
     * <p>回显原始枚举名而非"成功/失败"：{@code NOT_DRAW} / {@code NO_AMMO} / {@code COOL_DOWN} /
     * {@code NETWORK_FAIL} 各自指向完全不同的排查方向，折叠成布尔会让调试无从下手。
     *
     * <p>与 {@code engage} 的分工：本命令验证"枪能不能响"，{@code engage} 验证"交火手感对不对"。
     */
    private static int fireOnce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        BotPlayer bot = BotManager.INSTANCE.find(name);
        if (bot == null) {
            return BotCommand.notFound(ctx, name);
        }
        Entity target = EntityArgument.getEntity(ctx, "target");

        Vec3 eye = bot.getEyePosition();
        Vec3 aimPoint = BotWeaponController.aimPointOf(target);
        double dx = aimPoint.x - eye.x;
        double dy = aimPoint.y - eye.y;
        double dz = aimPoint.z - eye.z;
        float yaw = Steering.yawToward(dx, dz);
        float pitch = Steering.pitchToward(dy, Math.sqrt(dx * dx + dz * dz));

        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
        bot.setXRot(pitch);

        String result = BotGunBridge.shoot(bot, pitch, yaw);
        boolean ok = BotGunBridge.RESULT_SUCCESS.equals(result);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "§7%s §a试射 → %s%s §8(yaw %.1f, pitch %.1f)",
                name, ok ? "§a" : "§c", result, yaw, pitch)), true);
        return ok ? 1 : 0;
    }

    private static int engage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Entity target = EntityArgument.getEntity(ctx, "target");
        if (!BotManager.INSTANCE.engage(name, target)) {
            return BotCommand.notFound(ctx, name);
        }
        AimModel.Difficulty tier = BotManager.INSTANCE.difficultyOf(name);
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + name + " §a进入交火：目标 §e"
                + target.getName().getString() + " §8(难度 " + tier + ")"), true);
        return 1;
    }

    /** 开关自主选目标：开启后 bot 自行发现并挑选敌人，不再需要 {@code engage} 指定。 */
    private static int setAuto(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        if (!BotManager.INSTANCE.setAutoTarget(name, enabled)) {
            return BotCommand.notFound(ctx, name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + name + " §a自主选目标 → "
                + (enabled
                ? "§a开启 §8(搜索半径 " + (int) BotPerception.DEFAULT_SEARCH_RADIUS + " 格)"
                : "§c关闭，并已脱离交火")), true);
        return 1;
    }

    private static int disengage(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!BotManager.INSTANCE.engage(name, null)) {
            return BotCommand.notFound(ctx, name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + name + " §a已脱离交火。"), true);
        return 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String tier = StringArgumentType.getString(ctx, "tier");
        AimModel.Difficulty difficulty;
        try {
            difficulty = AimModel.Difficulty.valueOf(tier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("§c未知难度档：§7" + tier));
            return 0;
        }
        if (!BotManager.INSTANCE.setDifficulty(name, difficulty)) {
            return BotCommand.notFound(ctx, name);
        }
        AimModel model = Act0Arcade.services().botDifficulty().get(difficulty);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "§7%s §a难度 → §e%s%s §8(反应 %d tick，收敛误差 %.2f°，30 格偏移 %.2f 格)",
                name, difficulty,
                Act0Arcade.services().botDifficulty().isOverridden(difficulty) ? " §6(已被配置覆盖)" : "",
                model.reactionTicks(), model.errorSettledDegrees(),
                AimModel.lateralOffsetBlocks(model.errorSettledDegrees(), 30.0D))), true);
        return 1;
    }

    /**
     * 重载 {@code config/act0_arcade/bot/difficulty.json} 并立即作用到在场 bot。
     *
     * <p>回显各档"30 格偏移"而非原始角度：调手感时真正要看的是"这个距离上能不能打中"
     * （玩家碰撞箱宽 0.6 格），角度值本身不直观。
     */
    private static int reloadTuning(CommandContext<CommandSourceStack> ctx) {
        ArcadeServices services = Act0Arcade.services();
        int loaded = services.reloadBotTuning();
        if (loaded < 0) {
            ctx.getSource().sendFailure(Component.literal("§c难度配置加载失败，详见服务器日志。"));
            return 0;
        }
        int refreshed = BotManager.INSTANCE.refreshDifficulties();

        StringBuilder summary = new StringBuilder();
        for (AimModel.Difficulty tier : AimModel.Difficulty.values()) {
            AimModel m = services.botDifficulty().get(tier);
            summary.append(String.format("\n §7%-6s §8反应 %2d tick，30 格偏移 %.2f 格%s",
                    tier, m.reactionTicks(),
                    AimModel.lateralOffsetBlocks(m.errorSettledDegrees(), 30.0D),
                    services.botDifficulty().isOverridden(tier) ? " §6(覆盖)" : ""));
        }
        List<String> violations = services.botDifficulty().monotonicityViolations();
        String warn = violations.isEmpty() ? ""
                : "\n §e单调性告警 " + violations.size() + " 条（详见日志）：更高难度并非全维度更强";

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a难度配置已重载（覆盖 §e" + loaded + " §a档，刷新 §e" + refreshed
                        + " §a个在场 bot）" + summary + warn), true);
        return 1;
    }
}
