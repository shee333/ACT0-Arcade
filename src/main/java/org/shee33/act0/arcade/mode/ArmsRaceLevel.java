package org.shee33.act0.arcade.mode;

import org.shee33.act0.arcade.loadout.WeaponCategory;

/**
 * 军备竞赛中的一个武器等级：定义了该等级使用的武器分类与需要击杀多少次才能晋级。
 *
 * @param category  该等级发放的武器分类
 * @param killsToAdvance 需要击杀几次才能晋级到下一级
 */
public record ArmsRaceLevel(WeaponCategory category, int killsToAdvance) {

    public ArmsRaceLevel {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        killsToAdvance = Math.max(1, Math.min(10, killsToAdvance));
    }

    /** 默认武器晋级路线。 */
    public static java.util.List<ArmsRaceLevel> defaultProgression() {
        return java.util.List.of(
                new ArmsRaceLevel(WeaponCategory.PISTOL, 2),
                new ArmsRaceLevel(WeaponCategory.SMG, 2),
                new ArmsRaceLevel(WeaponCategory.SHOTGUN, 2),
                new ArmsRaceLevel(WeaponCategory.RIFLE, 2),
                new ArmsRaceLevel(WeaponCategory.LMG, 2),
                new ArmsRaceLevel(WeaponCategory.MARKSMAN, 2),
                new ArmsRaceLevel(WeaponCategory.SNIPER, 2),
                new ArmsRaceLevel(WeaponCategory.MELEE, 1));
    }

    /** 该等级的中文简述。 */
    public String displayName() {
        return category.displayName() + " ×" + killsToAdvance;
    }

    /** 序列化为紧凑字符串格式 "CATEGORY:kills"。 */
    public String serialize() {
        return category.name() + ":" + killsToAdvance;
    }

    /** 从紧凑字符串格式解析。 */
    public static ArmsRaceLevel deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            WeaponCategory cat = WeaponCategory.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
            int kills = Integer.parseInt(parts[1].trim());
            return new ArmsRaceLevel(cat, kills);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 序列化列表。 */
    public static String serializeList(java.util.List<ArmsRaceLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(levels.get(i).serialize());
        }
        return sb.toString();
    }

    /** 反序列化列表。 */
    public static java.util.List<ArmsRaceLevel> deserializeList(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultProgression();
        }
        java.util.List<ArmsRaceLevel> levels = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            ArmsRaceLevel level = deserialize(part);
            if (level != null) {
                levels.add(level);
            }
        }
        return levels.isEmpty() ? defaultProgression() : java.util.List.copyOf(levels);
    }
}
