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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds RRV info recipes for NPC items (internal names ending in {@code _NPC}).
 *
 * <p>Displays the NPC's head, island location, and coordinates as an info card.
 * Uses RRV's built-in {@link InfoClientRecipe} so no custom recipe type is needed.</p>
 */
public final class NpcInfoRecipeBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcInfoRecipeBuilder.class);

    private NpcInfoRecipeBuilder() {}

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

            // Island and coordinates from NEU JSON fields
            String island = extractIsland(item);
            if (!island.isEmpty()) {
                text.append("§7Island: §f").append(island).append("\n");
            }

            String coords = extractCoordinates(item);
            if (!coords.isEmpty()) {
                text.append("§7Location: §f").append(coords).append("\n");
            }

            text.append("\n");

            // Wiki links if present
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

    private static String extractIsland(NeuItem item) {
        // NEU stores island as a top-level JSON field. Since NeuItem doesn't have an island field,
        // we can't access it directly. The ItemStackBuilder parses the SNBT nbttag, but the island
        // is outside the SNBT in the raw JSON.
        //
        // For now, we skip island extraction because NeuItem does not store it.
        // If needed in the future, NeuItem can be extended with an optional island field.
        return "";
    }

    private static String extractCoordinates(NeuItem item) {
        // Same limitation as extractIsland — x/y/z are top-level JSON fields not stored in NeuItem.
        return "";
    }
}
