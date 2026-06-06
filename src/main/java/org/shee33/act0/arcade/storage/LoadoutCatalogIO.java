package org.shee33.act0.arcade.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.shee33.act0.arcade.loadout.LoadoutItem;
import org.shee33.act0.arcade.loadout.LoadoutRegistry;
import org.shee33.act0.arcade.loadout.PlayerClassType;
import org.shee33.act0.arcade.loadout.WeaponCategory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 装备目录的 JSON 数据驱动读写。
 *
 * <p>目录文件存放于 {@code config/act0_arcade/loadout/*.json}，每个文件包含一组装备条目。
 * <b>新增枪包/装备只需新增或编辑 JSON，无需改动代码或重新编译</b>：游戏内 {@code /arcade reload}
 * 或重启服务端即可生效。
 *
 * <p>条目格式：
 * <pre>
 * {
 *   "entries": [
 *     {
 *       "key": "rifle.ak47",
 *       "name": "AK-47",
 *       "category": "RIFLE",            // 武器分类，槽位由分类推导
 *       "classes": ["ASSAULT"],          // 省略或为空表示全职业可用
 *       "price": 1500,                    // 解锁价格（默认武器为 0）
 *       "ammo": 90,                       // 初始备弹（元子弹）
 *       "isDefault": false,               // 是否默认初始武器
 *       "snbt": "{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:ak47\"}}"
 *     }
 *   ]
 * }
 * </pre>
 */
public final class LoadoutCatalogIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern GUN_ID_PATTERN = Pattern.compile("GunId:\\s*\\\"([^\\\"]+)\\\"");

    private LoadoutCatalogIO() {
    }

    /** 单个装备条目的可序列化数据传输对象。 */
    public static final class CatalogEntryDto {
        public String key;
        public String name;
        public String category;
        public List<String> classes;
        public int price;
        public int ammo;
        public boolean isDefault;
        public String snbt;
    }

    /** 一个 JSON 文件的根结构。 */
    public static final class CatalogFileDto {
        public List<CatalogEntryDto> entries = new ArrayList<>();
    }

    /**
     * 从配置目录加载全部 JSON 装备条目并填充注册表。若目录为空（无任何 JSON），
     * 会先把传入的 {@code seedDefaults} 写为 {@code default.json} 作为可编辑模板。
     *
     * @param registry     目标注册表（会先被清空）
     * @param loadoutDir   {@code config/act0_arcade/loadout} 目录
     * @param seedDefaults 目录为空时写入的默认条目（来自代码内置占位目录）
     * @return 实际加载的条目数
     */
    public static int loadInto(LoadoutRegistry registry, Path loadoutDir, List<CatalogEntryDto> seedDefaults) throws IOException {
        Files.createDirectories(loadoutDir);

        if (!hasJsonFiles(loadoutDir)) {
            CatalogFileDto seed = new CatalogFileDto();
            seed.entries = new ArrayList<>(seedDefaults);
            writeFile(loadoutDir.resolve("default.json"), seed);
        }

        registry.clear();
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(loadoutDir, "*.json")) {
            for (Path file : stream) {
                CatalogFileDto dto = readFile(file);
                if (dto == null || dto.entries == null) {
                    continue;
                }
                for (CatalogEntryDto entry : dto.entries) {
                    if (registerEntry(registry, entry)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 把注册表里的全部装备序列化为 DTO 列表（用于写默认文件 / 同步到客户端）。 */
    public static List<CatalogEntryDto> toDtos(LoadoutRegistry registry) {
        List<CatalogEntryDto> list = new ArrayList<>();
        for (LoadoutItem item : registry.all().values()) {
            list.add(toDto(item));
        }
        return list;
    }

    public static CatalogEntryDto toDto(LoadoutItem item) {
        CatalogEntryDto dto = new CatalogEntryDto();
        dto.key = item.key();
        dto.name = item.displayName();
        dto.category = item.category().name();
        dto.classes = new ArrayList<>();
        for (PlayerClassType type : item.allowedClasses()) {
            dto.classes.add(type.name());
        }
        dto.price = item.price();
        dto.ammo = item.initialAmmo();
        dto.isDefault = item.isDefault();
        dto.snbt = item.itemSnbt();
        return dto;
    }

    /** 把单个 DTO 注册进注册表；非法条目跳过并返回 {@code false}。 */
    public static boolean registerEntry(LoadoutRegistry registry, CatalogEntryDto entry) {
        if (entry == null || entry.key == null || entry.category == null) {
            return false;
        }
        WeaponCategory category = parseCategory(entry.category);
        if (category == null) {
            return false;
        }
        LoadoutItem.Builder builder = LoadoutItem.builder(entry.key, category)
            .displayName(displayNameFor(entry))
                .price(entry.price)
                .initialAmmo(entry.ammo)
                .isDefault(entry.isDefault);
        if (entry.snbt != null) {
            builder.itemSnbt(entry.snbt);
        }
        PlayerClassType[] classes = parseClasses(entry.classes);
        if (classes.length > 0) {
            builder.allowedClasses(classes);
        }
        try {
            registry.register(builder.build());
            return true;
        } catch (IllegalStateException duplicate) {
            return false;
        }
    }

    /** 武器库文件名（管理员上架的武器存于此，与可手编的 default.json 等分开）。 */
    public static final String ARMORY_FILE = "armory.json";

    /** 向武器库文件追加一个条目并落盘；key 重复则替换。 */
    public static void appendEntry(Path loadoutDir, CatalogEntryDto entry) throws IOException {
        Files.createDirectories(loadoutDir);
        Path file = loadoutDir.resolve(ARMORY_FILE);
        CatalogFileDto dto = Files.exists(file) ? readFile(file) : null;
        if (dto == null) {
            dto = new CatalogFileDto();
        }
        if (dto.entries == null) {
            dto.entries = new ArrayList<>();
        }
        dto.entries.removeIf(e -> e != null && entry.key.equals(e.key));
        dto.entries.add(entry);
        writeFile(file, dto);
    }

    /** 从武器库文件删除指定 key 的条目；返回是否确有删除。 */
    public static boolean removeEntry(Path loadoutDir, String key) throws IOException {
        Path file = loadoutDir.resolve(ARMORY_FILE);
        if (!Files.exists(file)) {
            return false;
        }
        CatalogFileDto dto = readFile(file);
        if (dto == null || dto.entries == null) {
            return false;
        }
        boolean removed = dto.entries.removeIf(e -> e != null && key.equals(e.key));
        if (removed) {
            writeFile(file, dto);
        }
        return removed;
    }

    private static boolean hasJsonFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            return stream.iterator().hasNext();
        }
    }

    private static CatalogFileDto readFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, CatalogFileDto.class);
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    private static void writeFile(Path file, CatalogFileDto dto) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(dto, writer);
        }
    }

    private static WeaponCategory parseCategory(String raw) {
        return WeaponCategory.byName(raw == null ? null : raw.trim());
    }

    private static String displayNameFor(CatalogEntryDto entry) {
        String name = entry.name != null ? entry.name.trim() : "";
        if (!name.isEmpty() && !name.startsWith("item.")) {
            return name;
        }
        String gunId = gunIdFromSnbt(entry.snbt);
        if (gunId != null && !gunId.isBlank()) {
            int colon = gunId.indexOf(':');
            return humanize(colon >= 0 ? gunId.substring(colon + 1) : gunId);
        }
        if (entry.key != null && entry.key.startsWith("gun.")) {
            return humanize(entry.key.substring(entry.key.lastIndexOf('.') + 1));
        }
        if (!name.isEmpty()) {
            return humanize(name.substring(name.lastIndexOf('.') + 1));
        }
        return entry.key != null ? entry.key : "未命名";
    }

    private static String gunIdFromSnbt(String snbt) {
        if (snbt == null) {
            return null;
        }
        Matcher matcher = GUN_ID_PATTERN.matcher(snbt);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未命名";
        }
        String[] parts = raw.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.isEmpty() ? raw : out.toString();
    }

    private static PlayerClassType[] parseClasses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new PlayerClassType[0];
        }
        List<PlayerClassType> types = new ArrayList<>();
        for (String s : raw) {
            try {
                types.add(PlayerClassType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 跳过未知职业
            }
        }
        return types.toArray(new PlayerClassType[0]);
    }
}
