package com.github.kdgaming0.skyrecipes.core.model;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * SkyBlock item rarity tiers, in ascending tier order.
 */
public enum SkyblockRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC,
    DIVINE,
    SPECIAL,
    VERY_SPECIAL,
    ULTIMATE,
    ADMIN;

    /**
     * Longest display name first, so "UNCOMMON" wins over "COMMON" and
     * "VERY SPECIAL" over "SPECIAL" when both are substrings of the line.
     */
    private static final List<SkyblockRarity> BY_NAME_LENGTH_DESC =
            Arrays.stream(values())
                    .sorted(Comparator.comparingInt((SkyblockRarity r) -> r.displayName.length()).reversed())
                    .toList();

    private final String displayName;

    SkyblockRarity() {
        this.displayName = name().replace('_', ' ');
    }

    /**
     * Parses a rarity from its lore string representation.
     * e.g. "§9§lRARE SWORD" -> RARE
     *
     * @return the rarity, or {@code COMMON} if no rarity word is present
     */
    public static SkyblockRarity fromLore(String loreLine) {
        SkyblockRarity rarity = fromLoreOrNull(loreLine);
        return rarity != null ? rarity : COMMON;
    }

    /**
     * Like {@link #fromLore(String)} but returns {@code null} when the line
     * contains no rarity word, so callers can fall back to another source.
     */
    public static @Nullable SkyblockRarity fromLoreOrNull(String loreLine) {
        if (loreLine == null || loreLine.isEmpty()) {
            return null;
        }
        String upper = loreLine.toUpperCase();
        for (SkyblockRarity rarity : BY_NAME_LENGTH_DESC) {
            if (upper.contains(rarity.displayName)) {
                return rarity;
            }
        }
        return null;
    }

    /**
     * Picks the tier to display when a reforge's data does not cover a stack's
     * exact rarity: the highest available tier not exceeding {@code target}, so a
     * DIVINE item falls back to MYTHIC when the data stops there. If every
     * available tier out-ranks {@code target} (item rarer than the data's floor
     * is impossible in practice), the lowest available tier is returned instead.
     *
     * @return the chosen tier, or {@code null} when {@code available} is empty
     */
    public static @Nullable SkyblockRarity highestAtMost(Collection<SkyblockRarity> available,
                                                         SkyblockRarity target) {
        SkyblockRarity bestAtMost = null;
        SkyblockRarity lowest = null;
        for (SkyblockRarity r : available) {
            if (lowest == null || r.ordinal() < lowest.ordinal()) {
                lowest = r;
            }
            if (r.ordinal() <= target.ordinal()
                    && (bestAtMost == null || r.ordinal() > bestAtMost.ordinal())) {
                bestAtMost = r;
            }
        }
        return bestAtMost != null ? bestAtMost : lowest;
    }
}
