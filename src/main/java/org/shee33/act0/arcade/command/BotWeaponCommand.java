package org.shee33.act0.arcade.command;

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
                .then(Commands.literal("difficulty")
                        .then(botArg()
                                .then(Commands.argument("tier", StringArgumentType.word()).suggests(DIFFICULTIES)
                                        .executes(BotWeaponCommand::setDifficulty))));
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
        AimModel model = difficulty.model();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "§7%s §a难度 → §e%s §8(反应 %d tick，收敛误差 %.2f°，30 格偏移 %.2f 格)",
                name, difficulty, model.reactionTicks(), model.errorSettledDegrees(),
                AimModel.lateralOffsetBlocks(model.errorSettledDegrees(), 30.0D))), true);
        return 1;
    }
}
