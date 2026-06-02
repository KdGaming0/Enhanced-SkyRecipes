package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockTradeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses NEU trade recipes (barter/trade exchanges).
 */
public final class TradeRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeRecipeParser.class);

    private TradeRecipeParser() {}

    /**
     * Parse a trade recipe.
     */
    public static SkyblockTradeClientRecipe parse(NeuItem item, NeuRecipe.TradeRecipe recipe, ItemRegistry itemRegistry) {
        try {
            Identifier recipeId = IdentifierUtil.skyRecipeId("trade/", item.internalName());

            // Parse input (single ingredient in "cost" field, stored as inputs list)
            ItemStack inputStack = ItemStack.EMPTY;
            if (!recipe.inputs().isEmpty()) {
                SlotRefParser.IngredientRef ref = SlotRefParser.parse(recipe.inputs().get(0));
                if (ref != null) {
                    NeuItem inputItem = SlotRefParser.resolve(ref, itemRegistry);
                    if (inputItem != null) {
                        inputStack = ItemStackBuilder.build(inputItem, ref.count());
                    }
                }
            }

            // Resolve output
            NeuItem outputItem = itemRegistry.getByInternalName(recipe.output()).orElse(item);
            ItemStack outputStack = ItemStackBuilder.build(outputItem, recipe.count());

            return new SkyblockTradeClientRecipe(
                recipeId,
                inputStack,
                outputStack
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse trade recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
