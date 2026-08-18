package com.github.kdgaming0.skyrecipes.core.util;

import java.util.Map;

/** Shared display names for Hypixel/NEU SkyBlock island mode identifiers. */
public final class SkyblockIslandNames {
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("dynamic", "Private Island"),
            Map.entry("hub", "Hub"),
            Map.entry("mining_1", "Gold Mine"),
            Map.entry("mining_2", "Deep Caverns"),
            Map.entry("mining_3", "Dwarven Mines"),
            Map.entry("combat_1", "Spider's Den"),
            Map.entry("crimson_isle", "Crimson Isle"),
            Map.entry("combat_3", "The End"),
            Map.entry("farming_1", "The Farming Islands"),
            Map.entry("foraging_1", "The Park"),
            Map.entry("winter", "Jerry's Workshop"),
            Map.entry("dungeon", "Dungeon"),
            Map.entry("dungeon_hub", "Dungeon Hub"),
            Map.entry("crystal_hollows", "Crystal Hollows"),
            Map.entry("garden", "The Garden"),
            Map.entry("rift", "The Rift"),
            Map.entry("kuudra", "Kuudra's Hollow"),
            Map.entry("mineshaft", "Glacite Mineshafts"),
            Map.entry("fishing_1", "Backwater Bayou"),
            Map.entry("foraging_2", "Galatea"),
            Map.entry("foraging_3", "Torrhus Canyon"),
            Map.entry("lotus_atoll", "Lotus Atoll"),
            Map.entry("safari", "Critter Safari"),
            Map.entry("dark_auction", "Dark Auction")
    );

    private SkyblockIslandNames() {
    }

    public static String displayName(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return DISPLAY_NAMES.getOrDefault(code, code);
    }
}
