package com.github.kdgaming0.skyrecipes.core.recipe.builders;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockInfoClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds SkyBlock info recipe cards for items with WIKI_URL infoType.
 *
 * <p>Shows useful item info (requirements, slayer level) and places a wiki button.</p>
 */
public final class WikiInfoRecipeBuilder {

    private WikiInfoRecipeBuilder() {
    }

    /**
     * Build info recipe(s) for an item.
     *
     * @return a list (usually one) of info recipes, or empty if the item has no wiki data
     */
    public static List<ReliableClientRecipe> build(NeuItem item) {
        List<ReliableClientRecipe> recipes = new ArrayList<>();
        if (item.info() == null || item.info().isEmpty()) {
            return recipes;
        }

        try {
            Identifier id = IdentifierUtil.skyRecipeId("wiki/", item.internalName());

            List<Component> infoLines = buildInfoLines(item);

            recipes.add(new SkyblockInfoClientRecipe(
                    id,
                    item,
                    item.displayName(),
                    infoLines,
                    item.info()
            ));

        } catch (Exception e) {
            // parseSafely isn't used here because we return a partially-filled list
            org.slf4j.LoggerFactory.getLogger(WikiInfoRecipeBuilder.class)
                    .warn("Failed to build wiki info for {}", item.internalName(), e);
        }

        return recipes;
    }

    private static List<Component> buildInfoLines(NeuItem item) {
        List<Component> lines = new ArrayList<>();

        String formattedCraft = RecipeUiHelper.formatCraftText(item.craftText());
        if (!formattedCraft.isEmpty()) {
            lines.add(Component.literal("Req: " + formattedCraft));
        }

        String formattedSlayer = RecipeUiHelper.formatSlayerReq(item.slayerReq());
        if (!formattedSlayer.isEmpty()) {
            lines.add(Component.literal("Slayer: " + formattedSlayer));
        }

        return lines;
    }
}
