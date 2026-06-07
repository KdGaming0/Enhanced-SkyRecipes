package com.github.kdgaming0.skyrecipes.core.hypixel;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * In-memory registry for Hypixel API item data.
 *
 * <p>All accessors are thread-safe via volatile publish-on-write. The registry starts empty
 * and is populated after a successful fetch or cache load.</p>
 */
public final class HypixelItemsRegistry {

    private static volatile Map<String, Map<String, Integer>> baseStats = Map.of();
    private static volatile Map<String, Map<String, int[]>> tieredStats = Map.of();

    private HypixelItemsRegistry() {
    }

    /**
     * Replaces all stored data atomically.
     */
    public static void load(HypixelItemsSnapshot snapshot) {
        baseStats = snapshot.baseStats();
        tieredStats = snapshot.tieredStats();
    }

    /**
     * Clears all data.
     */
    public static void clear() {
        baseStats = Map.of();
        tieredStats = Map.of();
    }

    /**
     * Returns {@code true} if the registry has been populated.
     */
    public static boolean isLoaded() {
        return !tieredStats.isEmpty() || !baseStats.isEmpty();
    }

    /**
     * Returns base stats for {@code itemId}, or {@code null} if unavailable.
     * Keys are UPPER_SNAKE (e.g. {@code "DAMAGE"}).
     */
    @Nullable
    public static Map<String, Integer> getBaseStats(String itemId) {
        return baseStats.get(itemId);
    }

    /**
     * Returns tiered stats for {@code itemId}, or {@code null} if unavailable.
     * The returned array is indexed by {@code star - 1} (index 0 = ★1).
     */
    @Nullable
    public static Map<String, int[]> getTieredStats(String itemId) {
        return tieredStats.get(itemId);
    }
}
