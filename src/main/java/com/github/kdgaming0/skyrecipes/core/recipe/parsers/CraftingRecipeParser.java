package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import cc.cassian.rrv.common.builtin.crafting.CraftingClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses NEU crafting recipes into RRV {@link CraftingClientRecipe} instances.
 */
public final class CraftingRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingRecipeParser.class);

    private static final Map<String, Integer> SLOT_INDEX = Map.of(
        "A1", 0, "A2", 1, "A3", 2,
        "B1", 3, "B2", 4, "B3", 5,
        "C1", 6, "C2", 7, "C3", 8
    );

    private CraftingRecipeParser() {}

    /**
     * Parse a crafting recipe into an RRV client recipe.
     *
     * @param item         The parent NeuItem (result item, unless overrideOutputId is set)
     * @param recipe       The crafting recipe data
     * @param itemRegistry For resolving ingredient and output items
     * @return A CraftingClientRecipe, or null if parsing fails
     */
    public static CraftingClientRecipe parse(NeuItem item, NeuRecipe.CraftingRecipe recipe, ItemRegistry itemRegistry) {
        try {
            Identifier recipeId = IdentifierUtil.skyRecipeId("crafting/", item.internalName());

            // Build ingredient map
            HashMap<Integer, SlotContent> ingredients = new HashMap<>();
            for (Map.Entry<String, String> entry : recipe.grid().entrySet()) {
                String slotKey = entry.getKey();
                String ingredientStr = entry.getValue();

                Integer slotIndex = SLOT_INDEX.get(slotKey);
                if (slotIndex == null) continue;

                SlotRefParser.IngredientRef ref = SlotRefParser.parse(ingredientStr);
                if (ref == null) continue;

                NeuItem ingredientItem = SlotRefParser.resolve(ref, itemRegistry);
                if (ingredientItem != null) {
                    ItemStack stack = ItemStackBuilder.build(ingredientItem, ref.count());
                    ingredients.put(slotIndex, SlotContent.of(stack));
                } else {
                    // Unknown ingredient — create an empty slot so the layout is preserved
                    LOGGER.debug("Unknown crafting ingredient '{}' for recipe {}", ref.internalName(), recipeId);
                }
            }

            // Resolve output item
            NeuItem outputItem = item;
            if (recipe.overrideOutputId() != null && !recipe.overrideOutputId().isEmpty()) {
                outputItem = itemRegistry.getByInternalName(recipe.overrideOutputId()).orElse(item);
            }

            ItemStack resultStack = ItemStackBuilder.build(outputItem, recipe.count());

            return new CraftingClientRecipe.Builder(recipeId, ingredients)
                .setResult(SlotContent.of(resultStack))
                .build();

        } catch (Exception e) {
            LOGGER.warn("Failed to parse crafting recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
