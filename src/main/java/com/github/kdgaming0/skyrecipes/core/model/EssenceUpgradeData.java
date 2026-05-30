package com.github.kdgaming0.skyrecipes.core.model;

import java.util.List;
import java.util.Map;

/**
 * Essence upgrade costs for a single item.
 *
 * @param essenceType The essence type (e.g. "Wither", "Undead")
 * @param costsPerStar Map of star level (1-10) to essence amount
 * @param extraItemsPerStar Map of star level to list of extra requirements (e.g. "SKYBLOCK_COIN:10000")
 */
public record EssenceUpgradeData(
    String essenceType,
    Map<Integer, Integer> costsPerStar,
    Map<Integer, List<String>> extraItemsPerStar
) {}
