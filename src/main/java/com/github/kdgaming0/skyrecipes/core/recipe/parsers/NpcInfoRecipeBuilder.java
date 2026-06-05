package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.builtin.info.InfoClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Builds RRV info recipes for NPC items (internal names ending in {@code _NPC}).
 *
 * <p>Displays the NPC's head, island location, and coordinates as an info card.
 * Uses RRV's built-in {@link InfoClientRecipe} so no custom recipe type is needed.</p>
 */
public final class NpcInfoRecipeBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcInfoRecipeBuilder.class);

    /**
     * NEU island codes → human-readable names (from constants/islands.json).
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

        try {
            ItemStack stack = ItemStackBuilder.build(item);
            Identifier id = IdentifierUtil.skyRecipeId("npc_info/", internalName);

            StringBuilder text = new StringBuilder();
            text.append("§e").append(item.displayName()).append("\n\n");

            String island = formatIsland(item.island());
            if (!island.isEmpty()) {
                text.append("§7Island: §f").append(island).append("\n");
            }

            if (item.x() != 0 || item.y() != 0 || item.z() != 0) {
                text.append("§7Location: §f")
                        .append(item.x()).append(", ")
                        .append(item.y()).append(", ")
                        .append(item.z()).append("\n");
            }

            text.append("\n");

            if (item.infoType() != null && !item.infoType().isEmpty()
                    && item.info() != null && !item.info().isEmpty()) {
                text.append("§eWiki Links:\n");
                for (String url : item.info()) {
                    text.append("§b").append(url).append("\n");
                }
            }

            return new InfoClientRecipe(id, SlotContent.of(stack), Component.literal(text.toString()));

        } catch (Exception e) {
            LOGGER.warn("Failed to build NPC info for {}: {}", internalName, e.getMessage());
            return null;
        }
    }

    private static String formatIsland(String code) {
        if (code == null || code.isEmpty()) return "";
        return ISLAND_NAMES.getOrDefault(code, code);
    }
}
