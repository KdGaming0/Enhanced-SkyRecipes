package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;

import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;

import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockTradeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Parses NEU trade recipes (barter/trade exchanges).
 */
public final class TradeRecipeParser {

    private TradeRecipeParser() {
    }

    /**
     * Parse a trade recipe.
     */
    public static SkyblockTradeClientRecipe parse(NeuItem item, NeuRecipe.TradeRecipe recipe, ItemRegistry itemRegistry) {
        return ParserUtil.parseSafely(item.internalName(), "trade", () -> {
            Identifier recipeId = IdentifierUtil.skyRecipeId("trade/", item.internalName());

            // Parse cost
            ItemStack inputStack = ItemStack.EMPTY;
            SlotRefParser.IngredientRef ref = SlotRefParser.parse(recipe.cost());
            if (ref != null) {
                NeuItem inputItem = SlotRefParser.resolve(ref, itemRegistry);
                if (inputItem != null) {
                    // For variable trades, show the minimum cost on the stack;
                    // the exact range is rendered as overlay text.
                    int displayCount = (recipe.min() > 0 && recipe.max() > recipe.min())
                            ? recipe.min()
                            : ref.count();
                    inputStack = ItemStackBuilder.build(inputItem, displayCount);
                }
            }

            // Resolve output
            NeuItem outputItem = itemRegistry.getByInternalName(recipe.result()).orElse(item);
            ItemStack outputStack = ItemStackBuilder.build(outputItem, recipe.count());

            return new SkyblockTradeClientRecipe(
                    recipeId,
                    inputStack,
                    outputStack,
                    recipe.min(),
                    recipe.max(),
                    item.info()
            );
        });
    }
}
