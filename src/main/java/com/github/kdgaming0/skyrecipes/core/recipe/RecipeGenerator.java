package com.github.kdgaming0.skyrecipes.core.recipe;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.parsers.CraftingRecipeParser;
import com.github.kdgaming0.skyrecipes.core.recipe.parsers.ForgeRecipeParser;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator that iterates all {@link NeuItem}s and generates RRV client recipes.
 */
public final class RecipeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeGenerator.class);

    private final ItemRegistry itemRegistry;

    public RecipeGenerator(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    /**
     * Generate all recipes and build the recipe index.
     *
     * @return The generation result containing recipes and indexes
     */
    public RecipeResult generate() {
        List<ReliableClientRecipe> recipes = new ArrayList<>();
        RecipeIndex.Builder indexBuilder = new RecipeIndex.Builder();

        for (NeuItem item : itemRegistry.getAllItems()) {
            // Crafting recipe
            if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
                ReliableClientRecipe recipe = CraftingRecipeParser.parse(item, crafting, itemRegistry);
                if (recipe != null) {
                    recipes.add(recipe);
                    indexRecipe(recipe, item, crafting.grid().values(), indexBuilder);
                }
            }

            // Other recipe types
            if (item.recipes() != null) {
                for (NeuRecipe recipeData : item.recipes()) {
                    ReliableClientRecipe recipe = switch (recipeData) {
                        case NeuRecipe.ForgeRecipe forge ->
                            ForgeRecipeParser.parse(item, forge, itemRegistry);
                        default -> null; // Drops, NpcShop, KatGrade, Trade deferred
                    };

                    if (recipe != null) {
                        recipes.add(recipe);
                        indexRecipe(recipe, item, extractIngredients(recipeData), indexBuilder);
                    }
                }
            }
        }

        LOGGER.info("Generated {} recipes ({} result entries, {} ingredient entries)",
            recipes.size(), indexBuilder.build().resultCount(), indexBuilder.build().ingredientCount());

        return new RecipeResult(recipes, indexBuilder.build());
    }

    private void indexRecipe(ReliableClientRecipe recipe, NeuItem item,
                             java.util.Collection<String> ingredientStrings,
                             RecipeIndex.Builder indexBuilder) {
        Identifier recipeId = recipe.getId();
        if (recipeId == null) return;

        // Index result
        indexBuilder.addResult(item.internalName(), recipeId);

        // Index ingredients
        for (String ingredientStr : ingredientStrings) {
            if (ingredientStr == null || ingredientStr.isEmpty()) continue;
            String name = ingredientStr;
            int colon = name.lastIndexOf(':');
            if (colon != -1) {
                name = name.substring(0, colon);
            }
            if (!name.isEmpty()) {
                indexBuilder.addIngredient(name, recipeId);
            }
        }
    }

    private java.util.Collection<String> extractIngredients(NeuRecipe recipe) {
        return switch (recipe) {
            case NeuRecipe.CraftingRecipe c -> c.grid().values();
            case NeuRecipe.ForgeRecipe f -> f.inputs();
            case NeuRecipe.KatGradeRecipe k -> k.items();
            case NeuRecipe.NpcShopRecipe n -> n.costs().stream().map(NeuRecipe.NpcShopRecipe.Cost::item).toList();
            case NeuRecipe.DropsRecipe d -> d.drops().stream().map(NeuRecipe.DropsRecipe.Drop::id).toList();
            case NeuRecipe.TradeRecipe t -> t.inputs();
        };
    }

    /**
     * Immutable result of recipe generation.
     */
    public record RecipeResult(List<ReliableClientRecipe> recipes, RecipeIndex index) {}
}
