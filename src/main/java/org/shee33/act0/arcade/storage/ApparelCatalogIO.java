package org.shee33.act0.arcade.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.shee33.act0.arcade.loadout.ApparelItem;
import org.shee33.act0.arcade.loadout.ApparelRegistry;
import org.shee33.act0.arcade.loadout.ApparelSlot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 服饰目录 JSON 数据驱动读写。 */
public final class ApparelCatalogIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String ARMORY_FILE = "armory.json";

    private ApparelCatalogIO() {
    }

    public static final class ApparelEntryDto {
        public String key;
        public String name;
        public String slot;
        public int price;
        public boolean isDefault;
        public String snbt;
    }

    public static final class ApparelFileDto {
        public List<ApparelEntryDto> entries = new ArrayList<>();
    }

    public static int loadInto(ApparelRegistry registry, Path apparelDir) throws IOException {
        Files.createDirectories(apparelDir);
        registry.clear();
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(apparelDir, "*.json")) {
            for (Path file : stream) {
                ApparelFileDto dto = readFile(file);
                if (dto == null || dto.entries == null) {
                    continue;
                }
                for (ApparelEntryDto entry : dto.entries) {
                    if (registerEntry(registry, entry)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static boolean registerEntry(ApparelRegistry registry, ApparelEntryDto entry) {
        if (entry == null || entry.key == null || entry.slot == null) {
            return false;
        }
        ApparelSlot slot = ApparelSlot.byName(entry.slot);
        if (slot == null) {
            return false;
        }
        try {
            registry.register(new ApparelItem(entry.key, entry.name, slot, entry.price, entry.isDefault, entry.snbt));
            return true;
        } catch (IllegalStateException duplicate) {
            return false;
        }
    }

    public static List<ApparelEntryDto> toDtos(ApparelRegistry registry) {
        List<ApparelEntryDto> list = new ArrayList<>();
        for (ApparelItem item : registry.all().values()) {
            list.add(toDto(item));
        }
        return list;
    }

    public static ApparelEntryDto toDto(ApparelItem item) {
        ApparelEntryDto dto = new ApparelEntryDto();
        dto.key = item.key();
        dto.name = item.displayName();
        dto.slot = item.slot().name();
        dto.price = item.price();
        dto.isDefault = item.isDefault();
        dto.snbt = item.itemSnbt();
        return dto;
    }

    public static void appendEntry(Path apparelDir, ApparelEntryDto entry) throws IOException {
        Files.createDirectories(apparelDir);
        Path file = apparelDir.resolve(ARMORY_FILE);
        ApparelFileDto dto = Files.exists(file) ? readFile(file) : null;
        if (dto == null) {
            dto = new ApparelFileDto();
        }
        if (dto.entries == null) {
            dto.entries = new ArrayList<>();
        }
        dto.entries.removeIf(e -> e != null && entry.key.equals(e.key));
        dto.entries.add(entry);
        writeFile(file, dto);
    }

    public static boolean removeEntry(Path apparelDir, String key) throws IOException {
        Path file = apparelDir.resolve(ARMORY_FILE);
        if (!Files.exists(file)) {
            return false;
        }
        ApparelFileDto dto = readFile(file);
        if (dto == null || dto.entries == null) {
            return false;
        }
        boolean removed = dto.entries.removeIf(e -> e != null && key.equals(e.key));
        if (removed) {
            writeFile(file, dto);
        }
        return removed;
    }

    private static ApparelFileDto readFile(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, ApparelFileDto.class);
        }
    }

    private static void writeFile(Path path, ApparelFileDto dto) throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(dto, w);
        }
    }
}
