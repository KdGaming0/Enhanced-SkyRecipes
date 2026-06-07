package com.github.kdgaming0.skyrecipes.core.hypixel;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of the Hypixel Public API {@code /v2/resources/skyblock/items} response.
 *
 * <p>Only the fields needed for essence upgrade stat accuracy are retained:</p>
 * <ul>
 *   <li>{@code baseStats} — item ID → stat name → base value (from API {@code stats})</li>
 *   <li>{@code tieredStats} — item ID → stat name → per-star values (from API {@code tiered_stats})</li>
 * </ul>
 */
public record HypixelItemsSnapshot(
        Map<String, Map<String, Integer>> baseStats,
        Map<String, Map<String, int[]>> tieredStats
) {
    public HypixelItemsSnapshot {
        baseStats = baseStats != null
                ? Collections.unmodifiableMap(baseStats)
                : Map.of();
        tieredStats = tieredStats != null
                ? Collections.unmodifiableMap(tieredStats)
                : Map.of();
    }
}
