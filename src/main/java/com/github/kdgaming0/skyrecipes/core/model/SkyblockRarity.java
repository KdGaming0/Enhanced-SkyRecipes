package com.github.kdgaming0.skyrecipes.core.model;

/**
 * SkyBlock item rarity tiers.
 */
public enum SkyblockRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC,
    SPECIAL,
    VERY_SPECIAL,
    ULTIMATE,
    ADMIN;

    /**
     * Parses a rarity from its lore string representation.
     * e.g. "§9§lRARE SWORD" -> RARE
     */
    public static SkyblockRarity fromLore(String loreLine) {
        if (loreLine == null || loreLine.isEmpty()) {
            return COMMON;
        }
        String upper = loreLine.toUpperCase();
        for (SkyblockRarity rarity : values()) {
            if (upper.contains(rarity.name().replace("_", " "))) {
                return rarity;
            }
        }
        return COMMON;
    }
}
