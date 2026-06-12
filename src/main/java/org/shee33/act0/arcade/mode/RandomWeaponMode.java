package org.shee33.act0.arcade.mode;

import org.shee33.act0.arcade.loadout.WeaponCategory;

/** 随机武器细分模式。 */
public enum RandomWeaponMode {
    OFF("off", "关闭", null, false),
    ALL("all", "全部随机", null, false),
    PISTOL_ONLY("pistol", "仅手枪+近战", null, true),
    RIFLE("rifle", "随机步枪", WeaponCategory.RIFLE, false),
    SNIPER("sniper", "随机狙击枪", WeaponCategory.SNIPER, false),
    SMG("smg", "随机冲锋枪", WeaponCategory.SMG, false),
    SHOTGUN("shotgun", "随机霰弹枪", WeaponCategory.SHOTGUN, false),
    LMG("lmg", "随机机枪", WeaponCategory.LMG, false),
    MARKSMAN("marksman", "随机精确射手", WeaponCategory.MARKSMAN, false);

    private final String id;
    private final String displayName;
    private final WeaponCategory primaryCategory;
    private final boolean pistolOnly;

    RandomWeaponMode(String id, String displayName, WeaponCategory primaryCategory, boolean pistolOnly) {
        this.id = id;
        this.displayName = displayName;
        this.primaryCategory = primaryCategory;
        this.pistolOnly = pistolOnly;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public WeaponCategory primaryCategory() {
        return primaryCategory;
    }

    public boolean pistolOnly() {
        return pistolOnly;
    }

    public boolean enabled() {
        return this != OFF;
    }

    public static RandomWeaponMode byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFF;
        }
        String n = raw.trim();
        if ("true".equalsIgnoreCase(n) || "on".equalsIgnoreCase(n) || "yes".equalsIgnoreCase(n)) {
            return ALL;
        }
        if ("false".equalsIgnoreCase(n) || "off".equalsIgnoreCase(n) || "no".equalsIgnoreCase(n)) {
            return OFF;
        }
        for (RandomWeaponMode mode : values()) {
            if (mode.id.equalsIgnoreCase(n) || mode.name().equalsIgnoreCase(n) || mode.displayName.equals(n)) {
                return mode;
            }
        }
        return OFF;
    }
}
