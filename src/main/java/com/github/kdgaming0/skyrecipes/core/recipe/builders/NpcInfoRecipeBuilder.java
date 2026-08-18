package com.github.kdgaming0.skyrecipes.core.recipe.builders;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIslandNames;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockInfoClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds info recipe cards for NPC items (internal names ending in {@code _NPC}).
 *
 * <p>Displays the NPC's head, island location, and coordinates as an info card.</p>
 */
public final class NpcInfoRecipeBuilder {

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

        String island = SkyblockIslandNames.displayName(item.island());
        if (!island.isEmpty()) {
            lines.add(RecipeUiHelper.label("Island:"));
            lines.add(Component.literal("  " + island));
        }

        if (item.x() != 0 || item.y() != 0 || item.z() != 0) {
            String coords = item.x() + ", " + item.y() + ", " + item.z();
            lines.add(RecipeUiHelper.label("Location:"));
            lines.add(Component.literal("  " + coords));
        }

        return lines;
    }

}
