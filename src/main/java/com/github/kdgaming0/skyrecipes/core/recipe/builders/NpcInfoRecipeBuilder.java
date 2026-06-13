package com.github.kdgaming0.skyrecipes.core.recipe.builders;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockInfoClientRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds info recipe cards for NPC items (internal names ending in {@code _NPC}).
 *
 * <p>Displays the NPC's head, island location, and coordinates as an info card.</p>
 */
public final class NpcInfoRecipeBuilder {

    /**
     * NEU island codes → human-readable names.
     */
    private static final Map<String, String> ISLAND_NAMES = Map.ofEntries(
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
            Map.entry("rift", "Rift"),
            Map.entry("kuudra", "Kuudra's Hollow"),
            Map.entry("mineshaft", "Glacite Mineshafts"),
            Map.entry("fishing_1", "Backwater Bayou"),
            Map.entry("foraging_2", "Galatea"),
            Map.entry("lotus_atoll", "Lotus Atoll")
    );

    private NpcInfoRecipeBuilder() {
    }

    /**
     * Build an NPC info recipe for the given item, or {@code null} if the item is not an NPC.
     *
     * @param item the NEU item data
     * @return an info recipe, or null
     */
    public static ReliableClientRecipe build(NeuItem item) {
        String internalName = item.internalName();
        if (internalName == null || !internalName.endsWith("_NPC")) {
            return null;
        }

        return ParserUtil.parseSafely(internalName, "npc_info", () -> {
            Identifier id = IdentifierUtil.skyRecipeId("npc_info/", internalName);

            List<Component> infoLines = buildNpcInfoLines(item);

            // Collect wiki URLs if present
            List<String> wikiUrls = (item.info() != null && !item.info().isEmpty())
                    ? item.info()
                    : List.of();

            SkyblockInfoClientRecipe recipe = new SkyblockInfoClientRecipe(
                    id,
                    item,
                    item.displayName(),
                    infoLines,
                    wikiUrls,
                    true,
                    item.displayName()
            );
            NpcInfoRegistry.register(internalName, recipe);
            return recipe;
        });
    }

    private static List<Component> buildNpcInfoLines(NeuItem item) {
        List<Component> lines = new ArrayList<>();

        String island = formatIsland(item.island());
        if (!island.isEmpty()) {
            lines.add(Component.literal("Island:"));
            lines.add(Component.literal("  " + island));
        }

        if (item.x() != 0 || item.y() != 0 || item.z() != 0) {
            String coords = item.x() + ", " + item.y() + ", " + item.z();
            lines.add(Component.literal("Location:"));
            lines.add(Component.literal("  " + coords));
        }

        return lines;
    }

    private static String formatIsland(String code) {
        if (code == null || code.isEmpty()) return "";
        return ISLAND_NAMES.getOrDefault(code, code);
    }
}
