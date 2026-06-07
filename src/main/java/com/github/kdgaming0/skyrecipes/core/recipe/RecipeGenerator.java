package com.github.kdgaming0.skyrecipes.core.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutation;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutationRegistry;
import com.github.kdgaming0.skyrecipes.core.recipe.parsers.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockGardenMutationClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockReforgeClientRecipe;
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
        NpcInfoRegistry.clear();

        List<ReliableClientRecipe> recipes = new ArrayList<>();
        RecipeIndex.Builder indexBuilder = new RecipeIndex.Builder();

        GardenMutationRegistry.load();

        for (NeuItem item : itemRegistry.getAllItems()) {
            // Crafting recipe
            if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
                ReliableClientRecipe recipe = CraftingRecipeParser.parse(item, crafting, itemRegistry);
                if (recipe != null) {
                    recipes.add(recipe);
                    indexRecipe(recipe, item, crafting.grid().values(), indexBuilder);
                }
            }

            // Wiki info recipes (skip NPCs — they get a unified NPC info card instead)
            String internalName = item.internalName();
            boolean isNpc = internalName != null && internalName.endsWith("_NPC");
            if (!isNpc && item.infoType() != null && !item.infoType().isEmpty() && item.info() != null && !item.info().isEmpty()) {
                List<ReliableClientRecipe> wikiRecipes = WikiInfoRecipeBuilder.build(item);
                for (ReliableClientRecipe recipe : wikiRecipes) {
                    recipes.add(recipe);
                    indexRecipe(recipe, item, List.of(), indexBuilder);
                }
            }

            // NPC info recipes
            ReliableClientRecipe npcInfoRecipe = NpcInfoRecipeBuilder.build(item);
            if (npcInfoRecipe != null) {
                recipes.add(npcInfoRecipe);
                indexRecipe(npcInfoRecipe, item, List.of(), indexBuilder);
            }

            // Other recipe types
            if (item.recipes() != null) {
                for (NeuRecipe recipeData : item.recipes()) {
                    ReliableClientRecipe recipe = switch (recipeData) {
                        case NeuRecipe.CraftingRecipe crafting ->
                                CraftingRecipeParser.parse(item, crafting, itemRegistry);
                        case NeuRecipe.ForgeRecipe forge -> ForgeRecipeParser.parse(item, forge, itemRegistry);
                        case NeuRecipe.KatGradeRecipe kat -> KatUpgradeRecipeParser.parse(item, kat, itemRegistry);
                        case NeuRecipe.NpcShopRecipe shop -> NpcShopRecipeParser.parse(item, shop, itemRegistry);
                        case NeuRecipe.DropsRecipe drops -> DropsRecipeParser.parse(item, drops, itemRegistry);
                        case NeuRecipe.TradeRecipe trade -> TradeRecipeParser.parse(item, trade, itemRegistry);
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
            try {
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
            } catch (Exception e) {
                LOGGER.error("Failed to generate essence upgrade recipes", e);
            }

            // Reforge recipes (from constants)
            try {
                List<ReliableClientRecipe> reforgeRecipes = ReforgeRecipeGenerator.generateAll(constantsRegistry, itemRegistry);
                for (ReliableClientRecipe recipe : reforgeRecipes) {
                    recipes.add(recipe);
                    if (recipe instanceof SkyblockReforgeClientRecipe reforge) {
                        // Index by every item this reforge applies to
                        for (String resultName : reforge.getResultInternalNames()) {
                            if (!resultName.isEmpty()) {
                                indexBuilder.addResult(resultName, recipe.getId());
                            }
                        }
                        // Index stone as ingredient
                        String stoneName = reforge.getStoneInternalName();
                        if (!stoneName.isEmpty()) {
                            indexBuilder.addIngredient(stoneName, recipe.getId());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to generate reforge recipes", e);
            }

            // Garden mutation recipes (from built-in resource)
            try {
                for (GardenMutation mutation : GardenMutationRegistry.all()) {
                    Identifier recipeId = IdentifierUtil.skyRecipeId("garden_mutation/", mutation.id());
                    List<String> wikiUrls = itemRegistry.getByInternalName(mutation.id())
                            .filter(item -> "WIKI_URL".equals(item.infoType()))
                            .map(NeuItem::info)
                            .orElse(List.of());
                    SkyblockGardenMutationClientRecipe recipe =
                            new SkyblockGardenMutationClientRecipe(recipeId, mutation, itemRegistry, wikiUrls);
                    recipes.add(recipe);
                    indexBuilder.addResult(mutation.id(), recipeId);
                    // Index ingredients
                    for (int row = 0; row < mutation.gridSize(); row++) {
                        for (int col = 0; col < mutation.gridSize(); col++) {
                            if (mutation.isIngredient(row, col)) {
                                String ingId = mutation.ingredientIdAt(row, col);
                                if (!ingId.isEmpty()) {
                                    indexBuilder.addIngredient(ingId, recipeId);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to generate garden mutation recipes", e);
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
            case NeuRecipe.TradeRecipe t -> t.cost().isEmpty() ? List.of() : List.of(t.cost());
        };
    }

    /**
     * Immutable result of recipe generation.
     */
    public record RecipeResult(List<ReliableClientRecipe> recipes, RecipeIndex index) {
    }
}
