package org.shee33.act0.arcade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.arena.ArcadeArena;
import org.shee33.act0.arcade.arena.SpawnPoint;
import org.shee33.act0.arcade.economy.ArcadeEconomy;
import org.shee33.act0.arcade.economy.BuyOutcome;
import org.shee33.act0.arcade.economy.EconomyManager;
import org.shee33.act0.arcade.hologram.ArcadeEntranceHolograms;
import org.shee33.act0.arcade.integration.TaczBridge;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelSlot;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.loadout.Loadout;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.LoadoutSlot;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.loadout.WeaponCategory;
import org.shee33.act0.arcade.match.ArcadeMatch;
import org.shee33.act0.arcade.match.ArcadeServices;
import org.shee33.act0.arcade.match.MatchLauncher;
import org.shee33.act0.arcade.mode.MatchSettings;
import org.shee33.act0.arcade.mode.RandomWeaponMode;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.storage.ArcadeLoadoutStore;
import org.shee33.act0.arcade.storage.ArcadeGlobalSettings;
import org.shee33.act0.arcade.storage.ArcadePlayerUnlocks;
import org.shee33.act0.arcade.storage.ApparelCatalogIO;
import org.shee33.act0.arcade.storage.ArenaRegistry;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@code /arcade} 命令：街机玩法的管理与接入层。
 *
 * <p>分为三组子命令：
 * <ul>
 *   <li>{@code /arcade arena …} —— 竞技场出生点录入（需 OP，权限等级 2）。</li>
 *   <li>{@code /arcade loadout …} —— 玩家自助配装（任意玩家）。</li>
 *   <li>{@code /arcade start|stop|list} —— 开/收对局（需 OP）。</li>
 * </ul>
 */
public final class ArcadeCommand {

    private ArcadeCommand() {
    }

    private static ArcadeServices services() {
        return Act0Arcade.services();
    }

    /** 已注册竞技场 id 的补全。 */
    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ArenaRegistry.get(ctx.getSource().getServer()).ids(), builder);

    /** 装备 key 的补全。 */
    private static final SuggestionProvider<CommandSourceStack> ITEM_KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    services().registry().all().keySet(), builder);

    /** 当前房间 id 的补全。 */
    private static final SuggestionProvider<CommandSourceStack> ROOM_IDS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (org.shee33.act0.arcade.match.ArcadeRoom room : services().rooms().all()) {
            ids.add(room.roomId());
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    /** 武器分类名的补全。 */
    private static final SuggestionProvider<CommandSourceStack> CATEGORY_KEYS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (WeaponCategory c : WeaponCategory.values()) {
            names.add(c.name());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    /** 随机武器模式补全。 */
    private static final SuggestionProvider<CommandSourceStack> RANDOM_MODE_KEYS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (RandomWeaponMode mode : RandomWeaponMode.values()) {
            names.add(mode.id());
        }
        names.add("true");
        names.add("false");
        return SharedSuggestionProvider.suggest(names, builder);
    };

    /** 服饰槽位补全。 */
    private static final SuggestionProvider<CommandSourceStack> APPAREL_SLOTS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (ApparelSlot slot : ApparelSlot.values()) {
            names.add(slot.name().toLowerCase(java.util.Locale.ROOT));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    /** 服饰 key 补全。 */
    private static final SuggestionProvider<CommandSourceStack> APPAREL_KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(services().apparel().all().keySet(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcade");

        root.then(buildArenaBranch());
        root.then(buildLoadoutBranch());
        root.then(buildMatchBranch());
        root.then(buildQueueBranch());
        root.then(buildRoomBranch());
        root.then(buildArmoryBranch());
        root.then(buildMoneyBranch());
        root.then(buildSettingsBranch());
        root.then(buildHologramBranch());
        root.then(Commands.literal("buy")
                .then(Commands.argument("item", StringArgumentType.word()).suggests(ITEM_KEYS)
                        .executes(ArcadeCommand::buy)));
        root.then(Commands.literal("browse").executes(ArcadeCommand::openBrowser));
        root.then(Commands.literal("leave").executes(ArcadeCommand::leaveActiveMatch));
        root.then(Commands.literal("quit").executes(ArcadeCommand::leaveActiveMatch));
        root.then(Commands.literal("reload")
                .requires(src -> src.hasPermission(2))
                .executes(ArcadeCommand::reloadCatalog));
        root.then(Commands.literal("cleandrops")
            .requires(src -> src.hasPermission(2))
            .executes(ArcadeCommand::cleanDrops));

        dispatcher.register(root);
        dispatcher.register(Commands.literal("suicide").executes(ArcadeCommand::suicide));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildHologramBranch() {
        return Commands.literal("hologram")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("browser")
                .executes(ctx -> createHologram(ctx, ArcadeEntranceHolograms.EntryType.BROWSER)))
            .then(Commands.literal("loadout")
                .executes(ctx -> createHologram(ctx, ArcadeEntranceHolograms.EntryType.LOADOUT)))
            .then(Commands.literal("battlefield")
                .executes(ctx -> createHologram(ctx, ArcadeEntranceHolograms.EntryType.BATTLEFIELD)))
            .then(Commands.literal("all")
                .executes(ArcadeCommand::createAllHolograms))
            .then(Commands.literal("clear")
                .executes(ctx -> clearHolograms(ctx, 6.0D))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 64.0D))
                    .executes(ctx -> clearHolograms(ctx, DoubleArgumentType.getDouble(ctx, "radius")))));
    }

    private static int createHologram(CommandContext<CommandSourceStack> ctx,
                                      ArcadeEntranceHolograms.EntryType type)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ArcadeEntranceHolograms.create(ctx.getSource().getPlayerOrException(), type);
    }

    private static int createAllHolograms(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ArcadeEntranceHolograms.createAll(ctx.getSource().getPlayerOrException());
    }

    private static int clearHolograms(CommandContext<CommandSourceStack> ctx, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ArcadeEntranceHolograms.clear(ctx.getSource().getPlayerOrException(), radius);
    }

    private static int leaveActiveMatch(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean ok = services().matches().leaveMatch(player);
        if (ok) {
            services().rooms().onMatchPlayerQuit(player.getUUID());
            ArcadeNetwork.broadcastRoomList(ctx.getSource().getServer());
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§7你当前不在进行中的对局。"));
        return 0;
    }

    private static int suicide(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.kill();
        return 1;
    }

    private static int cleanDrops(CommandContext<CommandSourceStack> ctx) {
        int removed = services().matches().cleanupAmmoCrates(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清理地面弹药补给箱 §e" + removed + " §a个。"), true);
        return removed;
    }

        private static LiteralArgumentBuilder<CommandSourceStack> buildSettingsBranch() {
        return Commands.literal("settings")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("health")
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 200.0))
                        .executes(ArcadeCommand::settingsHealth)))
            .then(Commands.literal("regenDelay")
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.0, 60.0))
                    .executes(ArcadeCommand::settingsRegenDelay)));
        }

        private static int settingsHealth(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "value");
        ArcadeGlobalSettings settings = ArcadeGlobalSettings.get(ctx.getSource().getServer());
            settings.setMaxHealth(value);
            int applied = settings.applyHealthToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已将全体玩家默认血量设为 §e" + fmt(value)
                    + " §7(已立即应用给 " + applied + " 名在线玩家，之后进服/进对局会再次校验)"), true);
        return 1;
        }

        private static int settingsRegenDelay(CommandContext<CommandSourceStack> ctx) {
        double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
        ArcadeGlobalSettings settings = ArcadeGlobalSettings.get(ctx.getSource().getServer());
        settings.setBreathHealDelaySeconds(seconds);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§a街机呼吸回血等待时间已设为 §e" + fmt(seconds) + " 秒"), true);
        return 1;
        }

    // ---------------- queue ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildQueueBranch() {
        LiteralArgumentBuilder<CommandSourceStack> join = Commands.literal("join");
        for (String mode : List.of("duel_1v1", "duel_2v2", "team_deathmatch", "hot_zone", "free_for_all", "jump_sniper")) {
            join.then(Commands.literal(mode)
                    .then(Commands.argument("arena", StringArgumentType.string()).suggests(ARENA_IDS)
                            .executes(ctx -> queueJoin(ctx, mode))));
        }
        return Commands.literal("queue")
                .then(join)
                .then(Commands.literal("leave").executes(ArcadeCommand::queueLeave))
                .then(Commands.literal("status").executes(ArcadeCommand::queueStatus));
    }

    private static int queueJoin(CommandContext<CommandSourceStack> ctx, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String arenaId = StringArgumentType.getString(ctx, "arena");
        String feedback = services().queue().enqueue(ctx.getSource().getServer(), player, mode, arenaId);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        return 1;
    }

    private static int queueLeave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String feedback = services().queue().leave(player);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        return 1;
    }

    private static int queueStatus(CommandContext<CommandSourceStack> ctx) {
        String status = services().queue().status();
        ctx.getSource().sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    // ---------------- browser / reload ----------------

    private static int openBrowser(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ArcadeNetwork.sendRoomList(player, true);
        return 1;
    }

    // ---------------- room ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoomBranch() {
        LiteralArgumentBuilder<CommandSourceStack> create = Commands.literal("create");
        for (String mode : List.of("duel_1v1", "duel_2v2", "team_deathmatch", "hot_zone", "free_for_all", "jump_sniper")) {
            create.then(Commands.literal(mode)
                    .then(roomCreateArgs(mode)));
        }
        return Commands.literal("room")
                .then(create)
                                .then(Commands.literal("size")
                                    .then(Commands.argument("players", IntegerArgumentType.integer(2, 32))
                                        .executes(ctx -> roomResize(ctx, null)))
                                    .then(Commands.argument("id", StringArgumentType.word()).suggests(ROOM_IDS)
                                        .then(Commands.argument("players", IntegerArgumentType.integer(2, 32))
                                            .executes(ctx -> roomResize(ctx, StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("join")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ROOM_IDS)
                                .executes(ArcadeCommand::roomJoin)))
                .then(Commands.literal("team")
                    .then(Commands.literal("blue").executes(ctx -> roomTeam(ctx, 0)))
                    .then(Commands.literal("red").executes(ctx -> roomTeam(ctx, 1))))
                .then(Commands.literal("leave").executes(ArcadeCommand::roomLeave))
                .then(Commands.literal("start").executes(ArcadeCommand::roomStart))
                .then(Commands.literal("list").executes(ArcadeCommand::roomList));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> roomCreateArgs(String mode) {
        var arena = Commands.argument("arena", StringArgumentType.string()).suggests(ARENA_IDS)
                .executes(ctx -> roomCreate(ctx, mode));
        var target = Commands.argument("target", IntegerArgumentType.integer(1, 300))
                .executes(ctx -> roomCreate(ctx, mode));
        var time = Commands.argument("time", IntegerArgumentType.integer(0, 3600))
                .executes(ctx -> roomCreate(ctx, mode));
        var random = Commands.argument("random", StringArgumentType.word()).suggests(RANDOM_MODE_KEYS)
                .executes(ctx -> roomCreate(ctx, mode));
        var capacity = Commands.argument("capacity", IntegerArgumentType.integer(2, 32))
                .executes(ctx -> roomCreate(ctx, mode));
        var health = Commands.argument("health", IntegerArgumentType.integer(0, 200));
        var respawn = Commands.argument("respawn", IntegerArgumentType.integer(0, 20));
        var friendly = Commands.argument("friendly", StringArgumentType.word());
        var crate = Commands.argument("crate", IntegerArgumentType.integer(0, 100));
        var hotTime = Commands.argument("hotTime", IntegerArgumentType.integer(15, 180));
        var hotRadius = Commands.argument("hotRadius", DoubleArgumentType.doubleArg(2.0D, 16.0D));
        var hotScore = Commands.argument("hotScore", IntegerArgumentType.integer(1, 5))
                .executes(ctx -> roomCreate(ctx, mode));

        hotRadius.then(hotScore);
        hotTime.then(hotRadius);
        crate.then(hotTime);
        friendly.then(crate);
        respawn.then(friendly);
        health.then(respawn);
        capacity.then(health);
        random.then(capacity);
        time.then(random);
        target.then(time);
        arena.then(target);
        return arena;
    }

    private static int roomCreate(CommandContext<CommandSourceStack> ctx, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String arena = StringArgumentType.getString(ctx, "arena");
        Integer target = optInt(ctx, "target");
        int time = optIntOr(ctx, "time", 0);
        RandomWeaponMode random = optRandomMode(ctx, "random");
        Integer capacity = optInt(ctx, "capacity");
        int health = optIntOr(ctx, "health", 0);
        int respawn = optIntOr(ctx, "respawn", 3);
        boolean friendly = optBool(ctx, "friendly", false);
        int crate = optIntOr(ctx, "crate", 25);
        int hotTime = optIntOr(ctx, "hotTime", 60);
        double hotRadius = optDoubleOr(ctx, "hotRadius", 6.0D);
        int hotScore = optIntOr(ctx, "hotScore", 1);
        String feedback = services().rooms().create(server, player, mode, arena, target, time, random, capacity,
            health, respawn, friendly, crate, hotTime, hotRadius, hotScore);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static int roomResize(CommandContext<CommandSourceStack> ctx, String roomId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        int players = IntegerArgumentType.getInteger(ctx, "players");
        boolean privileged = ctx.getSource().hasPermission(2);
        String feedback = services().rooms().resize(server, player, roomId, players, privileged);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static Integer optInt(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return IntegerArgumentType.getInteger(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int optIntOr(CommandContext<CommandSourceStack> ctx, String name, int def) {
        Integer v = optInt(ctx, name);
        return v != null ? v : def;
    }

    private static double optDoubleOr(CommandContext<CommandSourceStack> ctx, String name, double def) {
        try {
            return DoubleArgumentType.getDouble(ctx, name);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static boolean optBool(CommandContext<CommandSourceStack> ctx, String name, boolean def) {
        try {
            String raw = StringArgumentType.getString(ctx, name);
            if ("true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)
                    || "1".equals(raw) || "开".equals(raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase(raw) || "off".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)
                    || "0".equals(raw) || "关".equals(raw)) {
                return false;
            }
            return def;
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static RandomWeaponMode optRandomMode(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return RandomWeaponMode.byName(StringArgumentType.getString(ctx, name));
        } catch (IllegalArgumentException e) {
            return RandomWeaponMode.OFF;
        }
    }

    private static int roomJoin(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String id = StringArgumentType.getString(ctx, "id");
        String feedback = services().rooms().join(server, player, id);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static int roomLeave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String feedback = services().rooms().leave(server, player);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static int roomStart(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        boolean privileged = ctx.getSource().hasPermission(2);
        String feedback = services().rooms().start(server, player, privileged);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static int roomTeam(CommandContext<CommandSourceStack> ctx, int team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String feedback = services().rooms().chooseTeam(server, player, team);
        ctx.getSource().sendSuccess(() -> Component.literal(feedback), false);
        ArcadeNetwork.broadcastRoomList(server);
        return 1;
    }

    private static int roomList(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ArcadeNetwork.sendRoomList(player, true);
        return 1;
    }

    private static int reloadCatalog(CommandContext<CommandSourceStack> ctx) {
        int count = services().reloadCatalog();
        if (count < 0) {
            ctx.getSource().sendFailure(Component.literal("§c装备目录加载失败，详见服务端日志。"));
            return 0;
        }
        int attachCount = services().reloadAttachments();
        int apparelCount = services().reloadApparel();
        // 重载后把新目录同步给所有在线玩家
        for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            ArcadeNetwork.syncCatalog(online, services().registry());
            ArcadeNetwork.syncApparel(online);
            ArcadeNetwork.syncAttachmentCatalog(online);
        }
        final int n = count;
        final int a = Math.max(0, attachCount);
        final int ap = Math.max(0, apparelCount);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已重载装备目录，共 §e" + n + " §a件武器、§e" + a + " §a件配件、§e" + ap
                        + " §a件服饰，并同步到在线玩家。"), true);
        return 1;
    }

    // ---------------- arena ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildArenaBranch() {
        return Commands.literal("arena")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ArcadeCommand::arenaCreate)))
                .then(Commands.literal("addspawn")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                                .executes(ArcadeCommand::arenaAddSideSpawn)))
                .then(Commands.literal("setreturn")
                    .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                        .executes(ArcadeCommand::arenaSetReturnSpawn)))
                .then(Commands.literal("addrespawn")
                    .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                        .executes(ArcadeCommand::arenaAddRandomSpawn)))
                .then(Commands.literal("addhotzone")
                    .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                        .executes(ArcadeCommand::arenaAddHotZone)))
                .then(Commands.literal("addrandom")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                                .executes(ArcadeCommand::arenaAddRandomSpawn)))
                .then(Commands.literal("list")
                        .executes(ArcadeCommand::arenaList))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                                .executes(ArcadeCommand::arenaInfo)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ARENA_IDS)
                                .executes(ArcadeCommand::arenaRemove)));
    }

    private static int arenaCreate(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        ArenaRegistry registry = ArenaRegistry.get(ctx.getSource().getServer());
        if (registry.find(id).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("竞技场已存在：" + id));
            return 0;
        }
        ArcadeArena arena = new ArcadeArena(id, new ArrayList<>(), new ArrayList<>(), null);
        registry.put(arena);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已创建竞技场 §e" + id + " §a。请继续设置返回点、阵营出生点和个人乱斗复活点。"), true);
        return 1;
    }

    private static int arenaSetReturnSpawn(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        ArenaRegistry registry = ArenaRegistry.get(ctx.getSource().getServer());
        Optional<ArcadeArena> existing = registry.find(id);
        if (existing.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("竞技场不存在：" + id));
            return 0;
        }
        ArcadeArena old = existing.get();
        ArcadeArena.Builder builder = new ArcadeArena.Builder(id).returnSpawn(spawnAt(player));
        old.sideSpawns().forEach(builder::addSideSpawn);
        old.randomSpawns().forEach(builder::addRandomSpawn);
        ArcadeArena updated = builder.build();
        registry.put(updated);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已将当前位置设置为 §e" + id + " §a的对局结束返回点。"), true);
        return 1;
    }

    private static int arenaAddSideSpawn(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        ArenaRegistry registry = ArenaRegistry.get(ctx.getSource().getServer());
        Optional<ArcadeArena> existing = registry.find(id);
        if (existing.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("竞技场不存在：" + id));
            return 0;
        }
        ArcadeArena old = existing.get();
        ArcadeArena.Builder builder = new ArcadeArena.Builder(id).returnSpawn(old.returnSpawn());
        old.sideSpawns().forEach(builder::addSideSpawn);
        old.randomSpawns().forEach(builder::addRandomSpawn);
        builder.addSideSpawn(spawnAt(player));
        ArcadeArena updated = builder.build();
        registry.put(updated);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已为 §e" + id + " §a添加阵营出生点 #" + updated.sideSpawns().size()), true);
        return 1;
    }

    private static int arenaAddRandomSpawn(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return arenaAddRandomLikePoint(ctx, "§a已为 §e%s §a添加个人乱斗手动复活点 #%d");
    }

    private static int arenaAddHotZone(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return arenaAddRandomLikePoint(ctx, "§a已为 §e%s §a添加热区点 #%d");
    }

    private static int arenaAddRandomLikePoint(CommandContext<CommandSourceStack> ctx, String successFormat) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        ArenaRegistry registry = ArenaRegistry.get(ctx.getSource().getServer());
        Optional<ArcadeArena> existing = registry.find(id);
        if (existing.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("竞技场不存在：" + id));
            return 0;
        }
        ArcadeArena old = existing.get();
        ArcadeArena.Builder builder = new ArcadeArena.Builder(id).returnSpawn(old.returnSpawn());
        old.sideSpawns().forEach(builder::addSideSpawn);
        old.randomSpawns().forEach(builder::addRandomSpawn);
        builder.addRandomSpawn(spawnAt(player));
        ArcadeArena updated = builder.build();
        registry.put(updated);
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format(successFormat, id, updated.randomSpawns().size())), true);
        return 1;
    }

    private static int arenaList(CommandContext<CommandSourceStack> ctx) {
        ArenaRegistry registry = ArenaRegistry.get(ctx.getSource().getServer());
        if (registry.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7暂无竞技场。"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§e竞技场（" + registry.ids().size() + "）：§f" + String.join(", ", registry.ids())), false);
        return registry.ids().size();
    }

    private static int arenaInfo(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Optional<ArcadeArena> arena = ArenaRegistry.get(ctx.getSource().getServer()).find(id);
        if (arena.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("竞技场不存在：" + id));
            return 0;
        }
        ArcadeArena a = arena.get();
        String ret = a.hasReturnSpawn() ? a.returnSpawn().toString() : "§c未设置";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§e" + id + "§f：阵营出生点 " + a.sideSpawns().size()
                + "，个人乱斗复活点 " + a.randomSpawns().size()
                + "，返回点 " + ret), false);
        return 1;
    }

    private static int arenaRemove(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        boolean removed = ArenaRegistry.get(ctx.getSource().getServer()).remove(id);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a已删除竞技场：" + id), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("竞技场不存在：" + id));
        return 0;
    }

    // ---------------- loadout ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildLoadoutBranch() {
        LiteralArgumentBuilder<CommandSourceStack> classBranch = Commands.literal("class");
        for (PlayerClassType type : PlayerClassType.values()) {
            classBranch.then(Commands.literal(type.name())
                    .executes(ctx -> loadoutSetClass(ctx, type)));
        }

        LiteralArgumentBuilder<CommandSourceStack> setBranch = Commands.literal("set");
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            setBranch.then(Commands.literal(slot.name())
                    .then(Commands.argument("item", StringArgumentType.word()).suggests(ITEM_KEYS)
                            .executes(ctx -> loadoutSetSlot(ctx, slot))));
        }

        LiteralArgumentBuilder<CommandSourceStack> clearBranch = Commands.literal("clear");
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            clearBranch.then(Commands.literal(slot.name())
                    .executes(ctx -> loadoutClearSlot(ctx, slot)));
        }

        return Commands.literal("loadout")
                .then(classBranch)
                .then(setBranch)
                .then(clearBranch)
                .then(Commands.literal("add").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("slot", StringArgumentType.word()).suggests(APPAREL_SLOTS)
                        .executes(ctx -> apparelAdd(ctx, false))
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                            .executes(ctx -> apparelAdd(ctx, false)))))
                .then(Commands.literal("adddefault").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("slot", StringArgumentType.word()).suggests(APPAREL_SLOTS)
                        .executes(ctx -> apparelAdd(ctx, true))))
                .then(Commands.literal("removeapparel").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("key", StringArgumentType.word()).suggests(APPAREL_KEYS)
                        .executes(ArcadeCommand::apparelRemove)))
                .then(Commands.literal("edit").executes(ArcadeCommand::loadoutEdit))
                .then(Commands.literal("show").executes(ArcadeCommand::loadoutShow));
    }

    private static int apparelAdd(CommandContext<CommandSourceStack> ctx, boolean asDefault)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String slotRaw = StringArgumentType.getString(ctx, "slot");
        ApparelSlot slot = ApparelSlot.byName(slotRaw);
        if (slot == null) {
            ctx.getSource().sendFailure(Component.literal("§c未知服饰槽位：" + slotRaw));
            return 0;
        }
        int price = asDefault ? 0 : optIntOr(ctx, "price", 0);
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c请手持要上架的服饰。"));
            return 0;
        }
        String key = "apparel." + slot.name().toLowerCase(java.util.Locale.ROOT) + "." + deriveKey(stack);
        CompoundTag full = new CompoundTag();
        stack.save(full);

        ApparelCatalogIO.ApparelEntryDto dto = new ApparelCatalogIO.ApparelEntryDto();
        dto.key = key;
        dto.name = stack.getHoverName().getString();
        dto.slot = slot.name();
        dto.price = price;
        dto.isDefault = asDefault;
        dto.snbt = full.toString();
        try {
            ApparelCatalogIO.appendEntry(services().apparelConfigDir(), dto);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c写入服饰库失败：" + e.getMessage()));
            return 0;
        }
        int count = services().reloadApparel();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncApparel(online);
            }
        }
        String tag = asDefault ? "§b默认服饰" : "§e售价 " + price;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已上架服饰 §f" + dto.name + " §7(" + key + ") §7[" + slot.displayName() + "] " + tag), true);
        return 1;
    }

    private static int apparelRemove(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean removed;
        try {
            removed = ApparelCatalogIO.removeEntry(services().apparelConfigDir(), key);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c删除失败：" + e.getMessage()));
            return 0;
        }
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c服饰库中没有该条目：" + key));
            return 0;
        }
        int count = services().reloadApparel();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncApparel(online);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已下架服饰：" + key), true);
        return 1;
    }

    private static Loadout ownLoadout(CommandSourceStack source, ServerPlayer player) {
        ArcadeLoadoutStore store = ArcadeLoadoutStore.get(source.getServer());
        return store.getOrCreate(player.getUUID(),
                DefaultLoadoutCatalog.defaultLoadout(services().registry()));
    }

    private static int loadoutSetClass(CommandContext<CommandSourceStack> ctx, PlayerClassType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!services().matches().canChangeLoadout(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§c战斗中无法修改配装。"));
            return 0;
        }
        Loadout loadout = ownLoadout(ctx.getSource(), player);
        loadout.setClassType(type);
        ArcadeLoadoutStore.get(ctx.getSource().getServer()).save(player.getUUID(), loadout);
        ctx.getSource().sendSuccess(() -> Component.literal("§a职业已设为 §e" + type.name()), false);
        return 1;
    }

    private static int loadoutSetSlot(CommandContext<CommandSourceStack> ctx, LoadoutSlot slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!services().matches().canChangeLoadout(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§c战斗中无法修改配装。"));
            return 0;
        }
        String itemKey = StringArgumentType.getString(ctx, "item");
        LoadoutRegistry registry = services().registry();
        Optional<LoadoutItem> item = registry.find(itemKey);
        if (item.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("未知装备 key：" + itemKey));
            return 0;
        }
        if (item.get().slot() != slot) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c装备 " + itemKey + " 属于槽位 " + item.get().slot().name() + "，不是 " + slot.name()));
            return 0;
        }
        Loadout loadout = ownLoadout(ctx.getSource(), player);
        loadout.setSlot(slot, itemKey);
        ArcadeLoadoutStore.get(ctx.getSource().getServer()).save(player.getUUID(), loadout);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a" + slot.name() + " §a已设为 §e" + item.get().displayName()), false);
        return 1;
    }

    private static int loadoutClearSlot(CommandContext<CommandSourceStack> ctx, LoadoutSlot slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!services().matches().canChangeLoadout(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§c战斗中无法修改配装。"));
            return 0;
        }
        Loadout loadout = ownLoadout(ctx.getSource(), player);
        loadout.setSlot(slot, null);
        ArcadeLoadoutStore.get(ctx.getSource().getServer()).save(player.getUUID(), loadout);
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清空槽位 §e" + slot.name()), false);
        return 1;
    }

    private static int loadoutEdit(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!services().matches().canChangeLoadout(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§c战斗中无法打开配装。"));
            return 0;
        }
        ArcadeNetwork.openLoadout(player);
        return 1;
    }

    private static int loadoutShow(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Loadout loadout = ownLoadout(ctx.getSource(), player);
        LoadoutRegistry registry = services().registry();
        StringBuilder sb = new StringBuilder("§e当前配装（职业 " + loadout.classType().name() + "）：");
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            String key = loadout.slotItemKey(slot).orElse(null);
            String name = key == null ? "§7—"
                    : "§f" + registry.find(key).map(LoadoutItem::displayName).orElse(key);
            sb.append("\n §7").append(slot.name()).append("§7: ").append(name);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // ---------------- match ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildMatchBranch() {
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal("start")
                .requires(src -> src.hasPermission(2));
        for (String mode : List.of("duel_1v1", "duel_2v2", "team_deathmatch", "hot_zone", "free_for_all", "jump_sniper")) {
            start.then(Commands.literal(mode)
                    .then(Commands.argument("arena", StringArgumentType.string()).suggests(ARENA_IDS)
                            .then(Commands.argument("players", EntityArgument.players())
                                    .executes(ctx -> matchStart(ctx, mode)))));
        }

        return start
                .then(Commands.literal("stop")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ArcadeCommand::matchStop)))
                .then(Commands.literal("list")
                        .executes(ArcadeCommand::matchList));
    }

    private static int matchStart(CommandContext<CommandSourceStack> ctx, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String arenaId = StringArgumentType.getString(ctx, "arena");
        Collection<ServerPlayer> selected = EntityArgument.getPlayers(ctx, "players");

        List<UUID> players = new ArrayList<>();
        for (ServerPlayer p : selected) {
            players.add(p.getUUID());
        }

        MatchLauncher.Result result = MatchLauncher.start(services(), server, mode, arenaId, players);
        if (!result.isOk()) {
            ctx.getSource().sendFailure(Component.literal("§c" + result.message()));
            return 0;
        }
        MatchSettings settings = MatchLauncher.buildSettings(mode, players.size());
        String label = settings != null ? settings.displayName() : mode;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已开始 §e" + label + " §a对局 §7(" + result.message() + ")"), true);
        return 1;
    }


    private static int matchStop(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ArcadeMatch match = services().matches().get(id);
        if (match == null) {
            ctx.getSource().sendFailure(Component.literal("对局不存在：" + id));
            return 0;
        }
        match.abort();
        ctx.getSource().sendSuccess(() -> Component.literal("§a已中止对局：" + id), true);
        return 1;
    }

    private static int matchList(CommandContext<CommandSourceStack> ctx) {
        int count = services().matches().activeCount();
        ctx.getSource().sendSuccess(() -> Component.literal("§e进行中的对局数：§f" + count), false);
        return count;
    }

    // ---------------- armory (管理员上架) ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildArmoryBranch() {
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add")
                .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORY_KEYS)
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .then(Commands.argument("ammo", IntegerArgumentType.integer(0))
                                        .executes(ctx -> armoryAdd(ctx, false)))));

        LiteralArgumentBuilder<CommandSourceStack> addDefault = Commands.literal("adddefault")
                .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORY_KEYS)
                        .then(Commands.argument("ammo", IntegerArgumentType.integer(0))
                                .executes(ctx -> armoryAdd(ctx, true))));

        return Commands.literal("armory")
                .requires(src -> src.hasPermission(2))
                .then(add)
                .then(addDefault)
                .then(Commands.literal("remove")
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(ITEM_KEYS)
                                .executes(ArcadeCommand::armoryRemove)))
                .then(Commands.literal("list").executes(ArcadeCommand::armoryList))
                .then(Commands.literal("addattach")
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .executes(ctx -> armoryAddAttach(ctx, false))))
                .then(Commands.literal("adddefaultattach")
                        .executes(ctx -> armoryAddAttach(ctx, true)))
                .then(Commands.literal("removeattach")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ArcadeCommand::armoryRemoveAttach)))
                .then(Commands.literal("listattach").executes(ArcadeCommand::armoryListAttach));
    }

    private static int armoryAdd(CommandContext<CommandSourceStack> ctx, boolean asDefault)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String catRaw = StringArgumentType.getString(ctx, "category");
        WeaponCategory category = WeaponCategory.byName(catRaw);
        if (category == null) {
            ctx.getSource().sendFailure(Component.literal("§c未知武器分类：" + catRaw));
            return 0;
        }
        int ammo = IntegerArgumentType.getInteger(ctx, "ammo");
        int price = asDefault ? 0 : IntegerArgumentType.getInteger(ctx, "price");

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c请手持要上架的武器。"));
            return 0;
        }

        String key = deriveKey(stack);
        CompoundTag full = new CompoundTag();
        stack.save(full);

        LoadoutCatalogIO.CatalogEntryDto dto = new LoadoutCatalogIO.CatalogEntryDto();
        dto.key = key;
        dto.name = stack.getHoverName().getString();
        dto.category = category.name();
        dto.classes = new ArrayList<>();
        dto.price = price;
        dto.ammo = ammo;
        dto.isDefault = asDefault;
        dto.snbt = full.toString();

        try {
            LoadoutCatalogIO.appendEntry(services().loadoutConfigDir(), dto);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c写入武器库失败：" + e.getMessage()));
            return 0;
        }

        int count = services().reloadCatalog();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncCatalog(online, services().registry());
            }
        }

        String tag = asDefault ? "§b默认武器" : "§e售价 " + price;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已上架 §f" + dto.name + " §7(" + key + ") §7[" + category.displayName() + "] " + tag), true);
        return 1;
    }

    private static int armoryRemove(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean removed;
        try {
            removed = LoadoutCatalogIO.removeEntry(services().loadoutConfigDir(), key);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c删除失败：" + e.getMessage()));
            return 0;
        }
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c武器库中没有该条目：" + key));
            return 0;
        }
        int count = services().reloadCatalog();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncCatalog(online, services().registry());
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已下架：" + key), true);
        return 1;
    }

    private static int armoryList(CommandContext<CommandSourceStack> ctx) {
        LoadoutRegistry registry = services().registry();
        if (registry.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7武器库为空。"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("§e武器库（" + registry.all().size() + "）：");
        for (LoadoutItem item : registry.all().values()) {
            String price = item.isDefault() ? "§b默认" : "§e" + item.price();
            sb.append("\n §f").append(item.displayName())
                    .append(" §7(").append(item.key()).append(") §7[")
                    .append(item.category().displayName()).append("] ").append(price);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return registry.all().size();
    }

    // ---------------- armory 配件（管理员上架） ----------------

    private static int armoryAddAttach(CommandContext<CommandSourceStack> ctx, boolean asDefault)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!TaczBridge.isAvailable()) {
            ctx.getSource().sendFailure(Component.literal("§c当前服务器未启用枪械改装（缺少 TaCZ）。"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !TaczBridge.isAttachment(stack)) {
            ctx.getSource().sendFailure(Component.literal("§c请手持要上架的配件。"));
            return 0;
        }
        String attachmentId = TaczBridge.attachmentId(stack);
        AttachmentSlotType slot = TaczBridge.attachmentType(stack);
        if (attachmentId == null || attachmentId.isEmpty() || slot == null) {
            ctx.getSource().sendFailure(Component.literal("§c无法识别该配件的类型或 ID。"));
            return 0;
        }
        int price = asDefault ? 0 : IntegerArgumentType.getInteger(ctx, "price");

        String key = "attach." + attachmentId.replace(':', '.');
        CompoundTag full = new CompoundTag();
        stack.save(full);

        AttachmentCatalogIO.AttachmentEntryDto dto = new AttachmentCatalogIO.AttachmentEntryDto();
        dto.key = key;
        dto.attachmentId = attachmentId;
        dto.name = stack.getHoverName().getString();
        dto.slot = slot.name();
        dto.price = price;
        dto.isDefault = asDefault;
        dto.snbt = full.toString();

        try {
            AttachmentCatalogIO.appendEntry(services().attachmentConfigDir(), dto);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c写入配件库失败：" + e.getMessage()));
            return 0;
        }

        int count = services().reloadAttachments();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncAttachmentCatalog(online);
            }
        }

        String tag = asDefault ? "§b默认配件" : "§e售价 " + price;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已上架配件 §f" + dto.name + " §7(" + key + ") §7[" + slot.displayName() + "] " + tag), true);
        return 1;
    }

    private static int armoryRemoveAttach(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean removed;
        try {
            removed = AttachmentCatalogIO.removeEntry(services().attachmentConfigDir(), key);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§c删除失败：" + e.getMessage()));
            return 0;
        }
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c配件库中没有该条目：" + key));
            return 0;
        }
        int count = services().reloadAttachments();
        if (count >= 0) {
            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ArcadeNetwork.syncAttachmentCatalog(online);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已下架配件：" + key), true);
        return 1;
    }

    private static int armoryListAttach(CommandContext<CommandSourceStack> ctx) {
        var attachments = services().attachments();
        if (attachments.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7配件库为空。"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("§e配件库（" + attachments.all().size() + "）：");
        for (var att : attachments.all().values()) {
            String price = att.isDefault() ? "§b默认" : "§e" + att.price();
            sb.append("\n §f").append(att.displayName())
                    .append(" §7(").append(att.key()).append(") §7[")
                    .append(att.slot().displayName()).append("] ").append(price);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return attachments.all().size();
    }

    // ---------------- buy (玩家购买解锁) ----------------

    private static int buy(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(ctx, "item");
        LoadoutRegistry registry = services().registry();
        UUID uuid = player.getUUID();
        MinecraftServer server = ctx.getSource().getServer();
        ArcadeEconomy economy = EconomyManager.economy(server);

        Optional<LoadoutItem> found = registry.find(key);
        if (found.isEmpty()) {
            Optional<ApparelItem> apparel = services().apparel().find(key);
            if (apparel.isPresent()) {
                return buyApparel(ctx, player, apparel.get(), key, economy, uuid, server);
            }
            ctx.getSource().sendFailure(Component.literal("§c没有这件武器或服饰。"));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.ERROR, economy.balance(uuid));
            return 0;
        }
        LoadoutItem item = found.get();
        ArcadePlayerUnlocks unlocks = ArcadePlayerUnlocks.get(server);

        if (item.isDefault() || unlocks.isUnlocked(uuid, key)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a你已拥有 §f" + item.displayName()), false);
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.ALREADY_OWNED, economy.balance(uuid));
            return 1;
        }

        int price = item.price();
        if (!economy.isAvailable()) {
            ctx.getSource().sendFailure(Component.literal("§c当前无法购买。"));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.UNAVAILABLE, 0);
            return 0;
        }
        if (!economy.has(uuid, price) || !economy.withdraw(uuid, price)) {
            ctx.getSource().sendFailure(Component.literal("§c余额不足，需要 §e" + price));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.INSUFFICIENT, economy.balance(uuid));
            return 0;
        }
        unlocks.unlock(uuid, key);
        ArcadeNetwork.syncUnlocks(player);
        ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.SUCCESS, economy.balance(uuid));
        ctx.getSource().sendSuccess(() -> Component.literal("§a已解锁 §f" + item.displayName()), false);
        return 1;
    }

    private static int buyApparel(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ApparelItem item,
                                  String key, ArcadeEconomy economy, UUID uuid, MinecraftServer server) {
        ArcadePlayerUnlocks unlocks = ArcadePlayerUnlocks.get(server);
        if (item.isDefault() || unlocks.isUnlocked(uuid, key)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a你已拥有服饰 §f" + item.displayName()), false);
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.ALREADY_OWNED, economy.balance(uuid));
            return 1;
        }
        int price = item.price();
        if (!economy.isAvailable()) {
            ctx.getSource().sendFailure(Component.literal("§c当前无法购买。"));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.UNAVAILABLE, 0);
            return 0;
        }
        if (!economy.has(uuid, price) || !economy.withdraw(uuid, price)) {
            ctx.getSource().sendFailure(Component.literal("§c余额不足，需要 §e" + price));
            ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.INSUFFICIENT, economy.balance(uuid));
            return 0;
        }
        unlocks.unlock(uuid, key);
        ArcadeNetwork.syncUnlocks(player);
        ArcadeNetwork.syncApparel(player);
        ArcadeNetwork.sendBuyResult(player, key, BuyOutcome.SUCCESS, economy.balance(uuid));
        ctx.getSource().sendSuccess(() -> Component.literal("§a已解锁服饰 §f" + item.displayName()), false);
        return 1;
    }

    // ---------------- money (内置钱包/经济) ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildMoneyBranch() {
        return Commands.literal("money")
                .executes(ArcadeCommand::moneySelf)
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ArcadeCommand::moneyGive))))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ArcadeCommand::moneySet))))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(src -> src.hasPermission(2))
                        .executes(ArcadeCommand::moneyQuery));
    }

    private static int moneySelf(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double bal = EconomyManager.economy(ctx.getSource().getServer()).balance(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("§e余额：§f" + fmt(bal)), false);
        return 1;
    }

    private static int moneyQuery(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        double bal = EconomyManager.economy(ctx.getSource().getServer()).balance(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§e" + target.getGameProfile().getName() + " §7余额：§f" + fmt(bal)), false);
        return 1;
    }

    private static int moneyGive(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ArcadeEconomy economy = EconomyManager.economy(ctx.getSource().getServer());
        if (!economy.deposit(target.getUUID(), amount)) {
            ctx.getSource().sendFailure(Component.literal("§c发放失败（经济后端不可写）。"));
            return 0;
        }
        double bal = economy.balance(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已给 §e" + target.getGameProfile().getName() + " §a发放 §e" + amount + " §7（余额 " + fmt(bal) + "）"), true);
        return 1;
    }

    private static int moneySet(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        MinecraftServer server = ctx.getSource().getServer();
        ArcadeEconomy economy = EconomyManager.economy(server);
        // 用存入/扣款的差额逼近目标值（对内置钱包与 Vault 均适用）
        double current = economy.balance(target.getUUID());
        double diff = amount - current;
        if (diff >= 0) {
            economy.deposit(target.getUUID(), diff);
        } else {
            economy.withdraw(target.getUUID(), -diff);
        }
        double bal = economy.balance(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已将 §e" + target.getGameProfile().getName() + " §a余额设为 §e" + fmt(bal)), true);
        return 1;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /**
     * 从手持物品推导稳定的武器 key。
     *
     * <p>TaCZ 枪械优先使用 {@code GunId}，便于同一把枪重复上架时覆盖旧定义。非枪械物品则加入完整
     * {@link ItemStack#save(CompoundTag)} 的短哈希，避免一批近战/模型物品共享同一个注册名时互相覆盖，
     * 造成“无论添加多少个近战，最后只剩一个”。
     */
    private static String deriveKey(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("GunId")) {
            String gunId = tag.getString("GunId");
            if (!gunId.isEmpty()) {
                return "gun." + gunId.replace(':', '.');
            }
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = id != null ? id.toString().replace(':', '.') : "item." + stack.getItem();
        CompoundTag full = new CompoundTag();
        stack.save(full);
        return base + "." + shortHash(full.toString());
    }

    private static String shortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 5 && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i] & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    // ---------------- helpers ----------------

    private static SpawnPoint spawnAt(ServerPlayer player) {
        String dim = player.level().dimension().location().toString();
        return new SpawnPoint(dim, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }
}
