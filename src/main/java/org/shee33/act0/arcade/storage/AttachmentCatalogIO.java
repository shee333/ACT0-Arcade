package org.shee33.act0.arcade.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.shee33.act0.arcade.loadout.ArcadeAttachment;
import org.shee33.act0.arcade.loadout.AttachmentRegistry;
import org.shee33.act0.arcade.loadout.AttachmentSlotType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配件目录的 JSON 数据驱动读写，与 {@link LoadoutCatalogIO}（武器）平行。
 *
 * <p>目录文件存放于 {@code config/act0_arcade/attachment/*.json}。管理员经
 * {@code /arcade armory addattach} 上架的配件写入 {@code armory.json}；亦可手编其它 JSON。
 *
 * <p>条目格式：
 * <pre>
 * {
 *   "entries": [
 *     {
 *       "key": "attach.tacz.scope_acog_ta31",
 *       "attachmentId": "tacz:scope_acog_ta31",
 *       "name": "ACOG TA31 瞄准镜",
 *       "slot": "SCOPE",
 *       "price": 800,
 *       "isDefault": false,
 *       "snbt": "{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:scope_acog_ta31\"}}"
 *     }
 *   ]
 * }
 * </pre>
 */
public final class AttachmentCatalogIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 配件库文件名（管理员上架的配件存于此）。 */
    public static final String ARMORY_FILE = "armory.json";

    private AttachmentCatalogIO() {
    }

    /** 单个配件条目的可序列化数据传输对象。 */
    public static final class AttachmentEntryDto {
        public String key;
        public String attachmentId;
        public String name;
        public String slot;
        public int price;
        public boolean isDefault;
        public String snbt;
    }

    /** 一个 JSON 文件的根结构。 */
    public static final class AttachmentFileDto {
        public List<AttachmentEntryDto> entries = new ArrayList<>();
    }

    /**
     * 从配置目录加载全部 JSON 配件条目并填充注册表。目录为空时不写任何模板（配件为可选玩法）。
     *
     * @param registry      目标注册表（会先被清空）
     * @param attachmentDir {@code config/act0_arcade/attachment} 目录
     * @return 实际加载的条目数
     */
    public static int loadInto(AttachmentRegistry registry, Path attachmentDir) throws IOException {
        Files.createDirectories(attachmentDir);
        registry.clear();
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(attachmentDir, "*.json")) {
            for (Path file : stream) {
                AttachmentFileDto dto = readFile(file);
                if (dto == null || dto.entries == null) {
                    continue;
                }
                for (AttachmentEntryDto entry : dto.entries) {
                    if (registerEntry(registry, entry)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 把注册表里全部配件序列化为 DTO 列表（用于同步到客户端）。 */
    public static List<AttachmentEntryDto> toDtos(AttachmentRegistry registry) {
        List<AttachmentEntryDto> list = new ArrayList<>();
        for (ArcadeAttachment a : registry.all().values()) {
            list.add(toDto(a));
        }
        return list;
    }

    public static AttachmentEntryDto toDto(ArcadeAttachment a) {
        AttachmentEntryDto dto = new AttachmentEntryDto();
        dto.key = a.key();
        dto.attachmentId = a.attachmentId();
        dto.name = a.displayName();
        dto.slot = a.slot().name();
        dto.price = a.price();
        dto.isDefault = a.isDefault();
        dto.snbt = a.itemSnbt();
        return dto;
    }

    /** 把单个 DTO 注册进注册表；非法条目跳过并返回 {@code false}。 */
    public static boolean registerEntry(AttachmentRegistry registry, AttachmentEntryDto entry) {
        if (entry == null || entry.key == null || entry.attachmentId == null || entry.slot == null) {
            return false;
        }
        AttachmentSlotType slot = AttachmentSlotType.byName(entry.slot);
        if (slot == null) {
            return false;
        }
        ArcadeAttachment.Builder builder = ArcadeAttachment.builder(entry.key, entry.attachmentId, slot)
                .displayName(entry.name != null ? entry.name : entry.key)
                .price(entry.price)
                .isDefault(entry.isDefault);
        if (entry.snbt != null) {
            builder.itemSnbt(entry.snbt);
        }
        try {
            registry.register(builder.build());
            return true;
        } catch (IllegalStateException duplicate) {
            return false;
        }
    }

    /** 向配件库文件追加一个条目并落盘；key 重复则替换。 */
    public static void appendEntry(Path attachmentDir, AttachmentEntryDto entry) throws IOException {
        Files.createDirectories(attachmentDir);
        Path file = attachmentDir.resolve(ARMORY_FILE);
        AttachmentFileDto dto = Files.exists(file) ? readFile(file) : null;
        if (dto == null) {
            dto = new AttachmentFileDto();
        }
        if (dto.entries == null) {
            dto.entries = new ArrayList<>();
        }
        dto.entries.removeIf(e -> e != null && entry.key.equals(e.key));
        dto.entries.add(entry);
        writeFile(file, dto);
    }

    /** 从配件库文件删除指定 key 的条目；返回是否确有删除。 */
    public static boolean removeEntry(Path attachmentDir, String key) throws IOException {
        Path file = attachmentDir.resolve(ARMORY_FILE);
        if (!Files.exists(file)) {
            return false;
        }
        AttachmentFileDto dto = readFile(file);
        if (dto == null || dto.entries == null) {
            return false;
        }
        boolean removed = dto.entries.removeIf(e -> e != null && key.equals(e.key));
        if (removed) {
            writeFile(file, dto);
        }
        return removed;
    }

    private static AttachmentFileDto readFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, AttachmentFileDto.class);
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    private static void writeFile(Path file, AttachmentFileDto dto) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(dto, writer);
        }
    }
}
