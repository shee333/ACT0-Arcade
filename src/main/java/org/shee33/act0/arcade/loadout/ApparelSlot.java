package org.shee33.act0.arcade.loadout;

/** 全配装共享的服饰槽位。 */
public enum ApparelSlot {
    HELMET("头盔"),
    CHESTPLATE("护甲"),
    LEGGINGS("护腿"),
    BOOTS("靴子");

    private final String displayName;

    ApparelSlot(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ApparelSlot byName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        for (ApparelSlot slot : values()) {
            if (slot.name().equalsIgnoreCase(n) || slot.displayName.equals(n)) {
                return slot;
            }
        }
        return switch (n.toLowerCase(java.util.Locale.ROOT)) {
            case "head" -> HELMET;
            case "helmet" -> HELMET;
            case "armor", "chest", "chestplate" -> CHESTPLATE;
            case "legs", "pants", "leggings" -> LEGGINGS;
            case "feet", "shoes", "boots" -> BOOTS;
            default -> null;
        };
    }
}
