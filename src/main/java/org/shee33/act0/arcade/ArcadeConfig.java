package org.shee33.act0.arcade;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * ACT0-Arcade 通用配置。
 *
 * <p>目前仅放全局开关；各玩法模式的对局参数（BoN、目标击杀、时限、复活策略、出生点等）
 * 由房间/竞技场配置承载，不在此处。
 */
@Mod.EventBusSubscriber(modid = Act0Arcade.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArcadeConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue VERBOSE_LOGGING = BUILDER
            .comment("是否输出街机玩法的详细调试日志")
            .define("verboseLogging", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 是否输出详细调试日志。 */
    public static boolean verboseLogging;

    private ArcadeConfig() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        verboseLogging = VERBOSE_LOGGING.get();
    }
}
