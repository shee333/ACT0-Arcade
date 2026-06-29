package org.shee33.act0.arcade;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.shee33.act0.arcade.command.ArcadeCommand;
import org.shee33.act0.arcade.hologram.ArcadeEntranceHolograms;
import org.shee33.act0.arcade.match.ArcadeServices;
import org.shee33.act0.arcade.network.ArcadeNetwork;
import org.shee33.act0.arcade.storage.ArcadeGlobalSettings;
import org.slf4j.Logger;

/**
 * ACT0-Arcade 主模组类。
 *
 * <p>街机对战玩法（统一配装、1v1、2v2、团队死斗、个人乱斗）的 Forge 入口。
 * 详见 {@code ACT0DEV/docs/ARCADE_MODES_DESIGN.md}。
 *
 * <p>本模组为独立、可编译、可单测的全新实现，配装与多玩法框架为干净自研，
 * 作为未来与战地共用的规范实现。
 */
@Mod(Act0Arcade.MODID)
public final class Act0Arcade {

    /** 模组 id，须与 {@code META-INF/mods.toml} 和 {@code gradle.properties} 中的 {@code mod_id} 一致。 */
    public static final String MODID = "act0_arcade";

    /** 服务端日志前缀，统一品牌标识。 */
    public static final String LOG_PREFIX = "[ACT/0/Arcade] ";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 全局街机服务（装备注册表 / 配装应用器 / 对局管理器）。 */
    private static final ArcadeServices SERVICES = new ArcadeServices();

    public Act0Arcade() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(SERVICES.matches());
    MinecraftForge.EVENT_BUS.register(ArcadeEntranceHolograms.INSTANCE);

        ArcadeNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ArcadeConfig.SPEC);
    }

    /** 全局街机服务入口，供命令/GUI 层使用。 */
    public static ArcadeServices services() {
        return SERVICES;
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{}common setup", LOG_PREFIX);
    }

    @SubscribeEvent
    public void onCommand(final CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (player.hasPermissions(2) || !SERVICES.matches().isInMatch(player.getUUID())) {
            return;
        }
        String raw = event.getParseResults().getReader().getString().trim();
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        if (cmd.equals("suicide") || cmd.startsWith("arcade leave") || cmd.startsWith("arcade quit")
            || cmd.startsWith("arcade room join")) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c对局中无法使用该指令，使用退出按钮可离开。"));
    }

    @SubscribeEvent
    public void onRegisterCommands(final RegisterCommandsEvent event) {
        ArcadeCommand.register(event.getDispatcher());
        LOGGER.info("{}registered /arcade command", LOG_PREFIX);
    }

    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event) {
        SERVICES.reloadCatalog();
        SERVICES.reloadApparel();
        SERVICES.reloadAttachments();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ArcadeGlobalSettings.get(player.server).applyHealth(player);
            ArcadeNetwork.syncCatalog(player, SERVICES.registry());
            ArcadeNetwork.syncApparel(player);
            ArcadeNetwork.syncAttachmentCatalog(player);
            // 若属于某进行中的对局，则重连归位
            SERVICES.matches().onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        SERVICES.queue().onPlayerLogout(event.getEntity().getUUID());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.getServer() != null) {
            SERVICES.rooms().onPlayerLogout(player.getServer(), player.getUUID());
        }
        SERVICES.matches().onPlayerLogout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event) {
        SERVICES.matches().abortAll();
        LOGGER.info("{}aborted all active matches on server stop", LOG_PREFIX);
    }
}
