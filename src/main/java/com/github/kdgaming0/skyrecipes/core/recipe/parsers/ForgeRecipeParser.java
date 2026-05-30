package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockForgeClientRecipe;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU forge recipes into {@link SkyblockForgeClientRecipe} instances.
 */
public final class ForgeRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeRecipeParser.class);

    private ForgeRecipeParser() {}

    /**
     * Parse a forge recipe.
     *
     * @param item         The parent NeuItem
     * @param recipe       The forge recipe data
     * @param itemRegistry For resolving ingredient and output items
     * @return A SkyblockForgeClientRecipe, or null if parsing fails
     */
    public static SkyblockForgeClientRecipe parse(NeuItem item, NeuRecipe.ForgeRecipe recipe, ItemRegistry itemRegistry) {
        try {
            Identifier recipeId = Identifier.fromNamespaceAndPath("skyrecipes", "forge/" + item.internalName().toLowerCase());

            // Resolve output item
            NeuItem outputItem = item;
            if (recipe.overrideOutputId() != null && !recipe.overrideOutputId().isEmpty()) {
                outputItem = itemRegistry.getByInternalName(recipe.overrideOutputId()).orElse(item);
            }

            // Parse inputs
            List<SkyblockForgeClientRecipe.ForgeIngredient> inputs = new ArrayList<>();
            for (String inputStr : recipe.inputs()) {
                SlotRefParser.IngredientRef ref = SlotRefParser.parse(inputStr);
                if (ref == null) continue;

                NeuItem ingredientItem = SlotRefParser.resolve(ref, itemRegistry);
                if (ingredientItem != null) {
                    inputs.add(new SkyblockForgeClientRecipe.ForgeIngredient(
                        ItemStackBuilder.build(ingredientItem, ref.count()),
                        ref.internalName(),
                        ref.count()
                    ));
                } else {
                    LOGGER.debug("Unknown forge ingredient '{}' for recipe {}", ref.internalName(), recipeId);
                }
            }

            return new SkyblockForgeClientRecipe(
                recipeId,
                inputs,
                ItemStackBuilder.build(outputItem, recipe.count()),
                recipe.duration()
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse forge recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
