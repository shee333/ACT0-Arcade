package org.shee33.act0.arcade.match;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.shee33.act0.arcade.Act0Arcade;
import org.shee33.act0.arcade.bot.BotDifficultyRegistry;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.AttachmentRegistry;
import org.shee33.act0.arcade.loadout.DefaultLoadoutCatalog;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.mc.GunModService;
import org.shee33.act0.arcade.loadout.mc.LoadoutApplier;
import org.shee33.act0.arcade.storage.ApparelCatalogIO;
import org.shee33.act0.arcade.storage.AttachmentCatalogIO;
import org.shee33.act0.arcade.storage.BotTuningIO;
import org.shee33.act0.arcade.storage.LoadoutCatalogIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 街机服务端运行时持有者：集中持有装备注册表、配装应用器与对局管理器，作为命令/GUI 层的统一入口。
 *
 * <p>由主模组在构造时创建一个全局单例（见 {@code Act0Arcade}）。装备目录由
 * {@code config/act0_arcade/loadout/*.json} 数据驱动：新增枪包/装备只需编辑 JSON 并
 * {@code /arcade reload} 或重启，无需改动代码。目录为空时会写出内置默认条目作为可编辑模板。
 */
public final class ArcadeServices {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LoadoutRegistry registry;
    private final ApparelRegistry apparel;
    private final AttachmentRegistry attachments;
    private final GunModService gunMod;
    private final LoadoutApplier applier;
    private final MatchManager matches;
    private final MatchQueue queue;
    private final RoomManager rooms;
    private final BotDifficultyRegistry botDifficulty;

    public ArcadeServices() {
        this.botDifficulty = new BotDifficultyRegistry();
        this.registry = new LoadoutRegistry();
        DefaultLoadoutCatalog.register(registry);
        this.apparel = new ApparelRegistry();
        this.attachments = new AttachmentRegistry();
        this.gunMod = new GunModService(registry, attachments);
        this.applier = new LoadoutApplier(registry, gunMod);
        this.matches = new MatchManager();
        this.queue = new MatchQueue(this);
        this.rooms = new RoomManager(this);
        this.matches.setEndListener(this.rooms::onMatchEnded);
    }

    public LoadoutRegistry registry() {
        return registry;
    }

    public AttachmentRegistry attachments() {
        return attachments;
    }

    /** bot 四档难度的生效参数；由 {@code config/act0_arcade/bot/difficulty.json} 覆盖。 */
    public BotDifficultyRegistry botDifficulty() {
        return botDifficulty;
    }

    /** {@code config/act0_arcade/bot} 目录。 */
    public Path botConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Act0Arcade.MODID).resolve("bot");
    }

    /**
     * 重载 bot 难度参数；返回成功应用覆盖的档位数，失败返回 {@code -1}。
     *
     * <p>违反"更高难度更强"的配置只告警不拒绝——该不变量在内置默认值上由单测强制保证，
     * 但管理员可能出于特殊玩法故意打破它。
     */
    public int reloadBotTuning() {
        try {
            BotTuningIO.LoadResult result = BotTuningIO.loadInto(botDifficulty, botConfigDir());
            if (result.seeded()) {
                LOGGER.info("{}wrote default bot difficulty template to {}",
                        Act0Arcade.LOG_PREFIX, botConfigDir().resolve(BotTuningIO.DIFFICULTY_FILE));
            } else {
                LOGGER.info("{}loaded {} bot difficulty overrides from {}",
                        Act0Arcade.LOG_PREFIX, result.loaded(), botConfigDir());
            }
            for (String problem : result.problems()) {
                LOGGER.warn("{}bot difficulty: {}", Act0Arcade.LOG_PREFIX, problem);
            }
            for (String violation : botDifficulty.monotonicityViolations()) {
                LOGGER.warn("{}bot difficulty 单调性告警: {}", Act0Arcade.LOG_PREFIX, violation);
            }
            return result.loaded();
        } catch (IOException e) {
            LOGGER.error("{}failed to load bot difficulty: {}", Act0Arcade.LOG_PREFIX, e.toString());
            return -1;
        }
    }

    public ApparelRegistry apparel() {
        return apparel;
    }

    public GunModService gunMod() {
        return gunMod;
    }

    public LoadoutApplier applier() {
        return applier;
    }

    public MatchManager matches() {
        return matches;
    }

    public MatchQueue queue() {
        return queue;
    }

    public RoomManager rooms() {
        return rooms;
    }

    /** {@code config/act0_arcade/loadout} 目录。 */
    public Path loadoutConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Act0Arcade.MODID).resolve("loadout");
    }

    /** {@code config/act0_arcade/attachment} 目录。 */
    public Path attachmentConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Act0Arcade.MODID).resolve("attachment");
    }

    /** {@code config/act0_arcade/apparel} 目录。 */
    public Path apparelConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Act0Arcade.MODID).resolve("apparel");
    }

    /** 从配置目录重新加载服饰目录。 */
    public int reloadApparel() {
        try {
            int count = ApparelCatalogIO.loadInto(apparel, apparelConfigDir());
            LOGGER.info("{}loaded {} apparel entries from {}",
                    Act0Arcade.LOG_PREFIX, count, apparelConfigDir());
            return count;
        } catch (IOException e) {
            LOGGER.error("{}failed to load apparel catalog: {}", Act0Arcade.LOG_PREFIX, e.toString());
            return -1;
        }
    }

    /**
     * 从配置目录重新加载配件目录。失败时保留当前注册表内容并记录日志。
     *
     * @return 加载的条目数；失败返回 {@code -1}
     */
    public int reloadAttachments() {
        try {
            int count = AttachmentCatalogIO.loadInto(attachments, attachmentConfigDir());
            LOGGER.info("{}loaded {} attachment entries from {}",
                    Act0Arcade.LOG_PREFIX, count, attachmentConfigDir());
            return count;
        } catch (IOException e) {
            LOGGER.error("{}failed to load attachment catalog: {}", Act0Arcade.LOG_PREFIX, e.toString());
            return -1;
        }
    }

    /**
     * 从配置目录重新加载装备目录。失败时保留当前注册表内容并记录日志。
     *
     * @return 加载的条目数；失败返回 {@code -1}
     */
    public int reloadCatalog() {
        LoadoutRegistry seed = new LoadoutRegistry();
        DefaultLoadoutCatalog.register(seed);
        try {
            int count = LoadoutCatalogIO.loadInto(registry, loadoutConfigDir(),
                    LoadoutCatalogIO.toDtos(seed));
            LOGGER.info("{}loaded {} loadout entries from {}",
                    Act0Arcade.LOG_PREFIX, count, loadoutConfigDir());
            return count;
        } catch (IOException e) {
            LOGGER.error("{}failed to load loadout catalog: {}", Act0Arcade.LOG_PREFIX, e.toString());
            return -1;
        }
    }
}
