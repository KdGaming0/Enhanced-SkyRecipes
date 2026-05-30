package com.github.kdgaming0.skyrecipes.core.model;

/**
 * Item categories derived from NEU lore type strings.
 */
public enum SkyblockItemCategory {
    SWORD,
    BOW,
    ARMOR,
    ACCESSORY,
    PET,
    TOOL,
    MATERIAL,
    BLOCK,
    CONSUMABLE,
    MINION,
    ENCHANTED_BOOK,
    DUNGEON_ITEM,
    RIFT_ITEM,
    UNKNOWN;

    /**
     * Derives category from the last lore line which typically contains the rarity and type.
     * e.g. "§9§lRARE SWORD" -> SWORD
     */
    public static SkyblockItemCategory fromLore(String lastLoreLine) {
        if (lastLoreLine == null || lastLoreLine.isEmpty()) {
            return UNKNOWN;
        }
        String upper = lastLoreLine.toUpperCase();
        for (SkyblockItemCategory cat : values()) {
            if (cat == UNKNOWN) continue;
            if (upper.contains(cat.name().replace("_", " "))) {
                return cat;
            }
        }
        return UNKNOWN;
    }
}
