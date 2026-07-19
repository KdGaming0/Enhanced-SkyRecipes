package com.github.kdgaming0.skyrecipes.core.model;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
     * Individual words appearing in rarity display names ("VERY", "SPECIAL", …),
     * for tokenized lore scans that need to skip rarity prefixes.
     */
    public static final Set<String> RARITY_WORDS = Arrays.stream(values())
            .flatMap(r -> Arrays.stream(r.name().split("_")))
            .collect(Collectors.toUnmodifiableSet());

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
     * Section-format colour code for this rarity, e.g. {@code LEGENDARY → "§6"}.
     */
    public String colorCode() {
        return switch (this) {
            case COMMON -> "§f";
            case UNCOMMON -> "§a";
            case RARE -> "§9";
            case EPIC -> "§5";
            case LEGENDARY -> "§6";
            case MYTHIC -> "§d";
            case DIVINE -> "§b";
            case SPECIAL, VERY_SPECIAL -> "§c";
            case ULTIMATE, ADMIN -> "§4";
        };
    }

    /**
     * Parses a rarity from its name, accepting either underscore or space
     * separators ("VERY_SPECIAL" / "Very Special").
     *
     * @return the rarity, or {@code null} if the name matches no tier
     */
    public static @Nullable SkyblockRarity fromName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (SkyblockRarity r : values()) {
            if (r.name().equals(normalized)) {
                return r;
            }
        }
        return null;
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
