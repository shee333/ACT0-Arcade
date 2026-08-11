package org.shee33.act0.arcade.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.shee33.act0.arcade.bot.BotNames;
import org.shee33.act0.arcade.bot.mc.BotManager;
import org.shee33.act0.arcade.bot.mc.BotPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /arcade bot} 调试指令分支：阶段 0 的人工验收入口。
 *
 * <p>本分支只做躯体层验证——生成、行走、撤走。它不是玩家可见的功能，而是回答那个决定整条路线
 * 成立与否的问题：<b>假玩家能否在两点之间平滑行走，不卡台阶、不抖、朝向正常</b>。
 * 决策层接入后本分支仍可保留，用于隔离排查"是脑子的问题还是身体的问题"。
 *
 * <p>刻意独立成文件而非并入 {@code ArcadeCommand}：后者已 1300 余行，远超 {@code AGENTS.md}
 * 的 250 行上限，不应继续膨胀。
 */
public final class BotCommand {

    /** 单次生成上限，防止误输入把服务器塞满假玩家。 */
    private static final int MAX_SPAWN_PER_CALL = 16;

    /** 生成时的散布半径（格），避免多个 bot 叠在同一格里互相挤开。 */
    private static final double SPAWN_SPREAD_RADIUS = 2.0D;

    /** 在场 bot 名补全。 */
    private static final SuggestionProvider<CommandSourceStack> ACTIVE_BOTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(BotManager.INSTANCE.activeNames(), builder);

    private BotCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBranch() {
        LiteralArgumentBuilder<CommandSourceStack> bot = Commands.literal("bot")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_SPAWN_PER_CALL))
                                .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("walk")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ACTIVE_BOTS)
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(BotCommand::walk))))
                .then(Commands.literal("come")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ACTIVE_BOTS)
                                .executes(BotCommand::come)))
                .then(Commands.literal("stop")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ACTIVE_BOTS)
                                .executes(BotCommand::stop)))
                .then(Commands.literal("list").executes(BotCommand::list))
                .then(Commands.literal("clear").executes(BotCommand::clear));
        return BotWeaponCommand.attach(bot);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        List<String> names = BotNames.pick(count, occupiedNames(server), server.getTickCount());
        if (names.isEmpty()) {
            source.sendFailure(Component.literal("§c名池已被占满，无法再生成 bot。"));
            return 0;
        }

        // 刻意取来源坐标而非要求 getPlayerOrException()：控制台与命令方块同为合法生成入口。
        Vec3 origin = source.getPosition();
        float yaw = source.getRotation().y;

        int spawned = 0;
        for (int i = 0; i < names.size(); i++) {
            double angle = 2.0D * Math.PI * i / names.size();
            double x = origin.x + Math.cos(angle) * SPAWN_SPREAD_RADIUS;
            double z = origin.z + Math.sin(angle) * SPAWN_SPREAD_RADIUS;
            BotPlayer bot = BotManager.INSTANCE.spawn(server, source.getLevel(), names.get(i),
                    x, origin.y, z, yaw, 0.0F);
            if (bot != null) {
                spawned++;
            }
        }

        int finalSpawned = spawned;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已生成 §e" + finalSpawned + " §a名 AI 士兵：§7" + String.join("§8, §7", names)), true);
        return spawned;
    }

    private static int walk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Vec3 target = Vec3Argument.getVec3(ctx, "pos");
        return order(ctx, name, target);
    }

    private static int come(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        return order(ctx, name, ctx.getSource().getPlayerOrException().position());
    }

    private static int order(CommandContext<CommandSourceStack> ctx, String name, Vec3 target) {
        if (!BotManager.INSTANCE.walkTo(name, target.x, target.y, target.z)) {
            return notFound(ctx, name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "§7%s §a前往 §e%.1f %.1f %.1f", name, target.x, target.y, target.z)), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!BotManager.INSTANCE.stop(name)) {
            return notFound(ctx, name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + name + " §a已停止。"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = BotManager.INSTANCE.activeNames();
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7当前没有在场的 AI 士兵。"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a在场 AI 士兵 §e" + names.size() + " §a名：§7" + String.join("§8, §7", names)), false);
        return names.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int removed = BotManager.INSTANCE.despawnAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a已撤走 §e" + removed + " §a名 AI 士兵。"), true);
        return removed;
    }

    /** 同包内的 {@link BotWeaponCommand} 共用同一条"查无此 bot"提示，避免两处文案漂移。 */
    static int notFound(CommandContext<CommandSourceStack> ctx, String name) {
        ctx.getSource().sendFailure(Component.literal("§c没有在场的 AI 士兵：§7" + name));
        return 0;
    }

    /**
     * 当前已被占用的玩家名（含真人与已在场 bot）。
     *
     * <p>原版允许重名，但客户端的名牌与记分板会混淆到无法分辨谁是谁——属于会直接破坏
     * 对局可读性的细节，必须在生成阶段就避开。
     */
    private static Set<String> occupiedNames(MinecraftServer server) {
        Set<String> taken = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            taken.add(player.getGameProfile().getName());
        }
        return taken;
    }
}
