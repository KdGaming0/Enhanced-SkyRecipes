package com.github.kdgaming0.skyrecipes.core.recipe;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.parsers.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;

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
    private final ConstantsRegistry constantsRegistry;

    public RecipeGenerator(ItemRegistry itemRegistry, ConstantsRegistry constantsRegistry) {
        this.itemRegistry = itemRegistry;
        this.constantsRegistry = constantsRegistry;
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

            // Wiki info recipes
            if (item.infoType() != null && !item.infoType().isEmpty() && item.info() != null && !item.info().isEmpty()) {
                List<ReliableClientRecipe> wikiRecipes = WikiInfoRecipeBuilder.build(item);
                for (ReliableClientRecipe recipe : wikiRecipes) {
                    recipes.add(recipe);
                    indexRecipe(recipe, item, List.of(), indexBuilder);
                }
            }

            // Other recipe types
            if (item.recipes() != null) {
                for (NeuRecipe recipeData : item.recipes()) {
                    ReliableClientRecipe recipe = switch (recipeData) {
                        case NeuRecipe.ForgeRecipe forge ->
                            ForgeRecipeParser.parse(item, forge, itemRegistry);
                        case NeuRecipe.KatGradeRecipe kat ->
                            KatUpgradeRecipeParser.parse(item, kat, itemRegistry);
                        case NeuRecipe.NpcShopRecipe shop ->
                            NpcShopRecipeParser.parse(item, shop, itemRegistry);
                        case NeuRecipe.DropsRecipe drops ->
                            DropsRecipeParser.parse(item, drops, itemRegistry);
                        case NeuRecipe.TradeRecipe trade ->
                            TradeRecipeParser.parse(item, trade, itemRegistry);
                        default -> null;
                    };

                    if (recipe != null) {
                        recipes.add(recipe);
                        indexRecipe(recipe, item, extractIngredients(recipeData), indexBuilder);
                    }
                }
            }
        }

        // Essence upgrade recipes (from constants)
        if (constantsRegistry != null) {
            List<ReliableClientRecipe> essenceRecipes = EssenceUpgradeGenerator.generateAll(constantsRegistry, itemRegistry);
            for (ReliableClientRecipe recipe : essenceRecipes) {
                recipes.add(recipe);
                // Index by result item internal name
                if (recipe.getId() != null) {
                    String resultName = recipe.getId().getPath();
                    int lastSlash = resultName.lastIndexOf('/');
                    if (lastSlash != -1) {
                        resultName = resultName.substring(0, lastSlash);
                        int firstSlash = resultName.indexOf('/');
                        if (firstSlash != -1) {
                            resultName = resultName.substring(firstSlash + 1);
                        }
                    }
                    indexBuilder.addResult(resultName, recipe.getId());
                }
            }

            // Reforge recipes (from constants)
            List<ReliableClientRecipe> reforgeRecipes = ReforgeRecipeGenerator.generateAll(constantsRegistry, itemRegistry);
            for (ReliableClientRecipe recipe : reforgeRecipes) {
                recipes.add(recipe);
                if (recipe.getId() != null) {
                    indexBuilder.addResult("reforge", recipe.getId());
                }
            }
        }

        RecipeIndex index = indexBuilder.build();
        LOGGER.info("Generated {} recipes ({} result entries, {} ingredient entries)",
            recipes.size(), index.resultCount(), index.ingredientCount());

        return new RecipeResult(recipes, index);
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
