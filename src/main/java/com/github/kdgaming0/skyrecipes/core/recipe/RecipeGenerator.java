package com.github.kdgaming0.skyrecipes.core.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutation;
import com.github.kdgaming0.skyrecipes.core.model.garden.GardenMutationRegistry;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.recipe.builders.NpcInfoRecipeBuilder;
import com.github.kdgaming0.skyrecipes.core.recipe.builders.ShardInfoRecipeBuilder;
import com.github.kdgaming0.skyrecipes.core.recipe.builders.WikiInfoRecipeBuilder;
import com.github.kdgaming0.skyrecipes.core.recipe.generators.EssenceUpgradeGenerator;
import com.github.kdgaming0.skyrecipes.core.recipe.generators.ReforgeRecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.recipe.generators.ShardFusionGenerator;
import com.github.kdgaming0.skyrecipes.core.recipe.parsers.*;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.NpcInfoRegistry;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockFusionClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockGardenMutationClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockReforgeClientRecipe;
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
        NpcInfoRegistry.beginCycle();
        GardenMutationRegistry.load();

        Batch batch = new Batch();

        for (NeuItem item : itemRegistry.getAllItems()) {
            generateCraftingRecipe(item, batch);
            AttributeShardData shard = generateShardInfoRecipe(item, batch);
            generateWikiInfoRecipes(item, shard, batch);
            generateNpcInfoRecipe(item, batch);
            generateTypedRecipes(item, batch);
        }

        if (constantsRegistry != null) {
            runGenerator("essence upgrades", batch.generatorFailures, () -> generateEssenceUpgrades(batch));
            runGenerator("reforges", batch.generatorFailures, () -> generateReforges(batch));
            runGenerator("garden mutations", batch.generatorFailures, () -> generateGardenMutations(batch));
            runGenerator("shard fusions", batch.generatorFailures, () -> generateShardFusions(batch));
        }

        RecipeIndex index = batch.indexBuilder.build();
        LOGGER.info("Generated {} recipes ({} result entries, {} ingredient entries) — parse: {} ok / {} failed",
                batch.recipes.size(), index.resultCount(), index.ingredientCount(),
                batch.parseAttempts - batch.parseFailures, batch.parseFailures);

        return new RecipeResult(batch.recipes, index, batch.parseAttempts, batch.parseFailures,
                List.copyOf(batch.generatorFailures));
    }

    // ---- Per-item recipe sources ----

    private void generateCraftingRecipe(NeuItem item, Batch batch) {
        if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
            batch.parseAttempts++;
            ReliableClientRecipe recipe = CraftingRecipeParser.parse(item, crafting, itemRegistry);
            if (recipe != null) {
                batch.recipes.add(recipe);
                indexRecipe(recipe, item, crafting.grid().values(), batch.indexBuilder);
            } else {
                batch.parseFailures++;
            }
        }
    }

    /**
     * Attribute shard info recipes (from constants) — shards otherwise have
     * no recipe or info data at all, so this is their only card.
     *
     * @return the shard data if this item is a shard, for the wiki-card skip below
     */
    private AttributeShardData generateShardInfoRecipe(NeuItem item, Batch batch) {
        AttributeShardData shard = constantsRegistry != null
                ? constantsRegistry.getAttributeShard(item.internalName())
                : null;
        if (shard != null) {
            ReliableClientRecipe shardRecipe = ShardInfoRecipeBuilder.build(item, shard);
            if (shardRecipe != null) {
                batch.recipes.add(shardRecipe);
                indexRecipe(shardRecipe, item, List.of(), batch.indexBuilder);
            }
        }
        return shard;
    }

    /**
     * Wiki info recipes (skip NPCs — they get a unified NPC info card instead;
     * skip shards — their card above already carries the wiki URLs).
     */
    private void generateWikiInfoRecipes(NeuItem item, AttributeShardData shard, Batch batch) {
        String internalName = item.internalName();
        boolean isNpc = internalName != null && internalName.endsWith("_NPC");
        if (!isNpc && shard == null && item.infoType() != null && !item.infoType().isEmpty()
                && item.info() != null && !item.info().isEmpty()) {
            List<ReliableClientRecipe> wikiRecipes = WikiInfoRecipeBuilder.build(item);
            for (ReliableClientRecipe recipe : wikiRecipes) {
                batch.recipes.add(recipe);
                indexRecipe(recipe, item, List.of(), batch.indexBuilder);
            }
        }
    }

    private void generateNpcInfoRecipe(NeuItem item, Batch batch) {
        ReliableClientRecipe npcInfoRecipe = NpcInfoRecipeBuilder.build(item);
        if (npcInfoRecipe != null) {
            batch.recipes.add(npcInfoRecipe);
            indexRecipe(npcInfoRecipe, item, List.of(), batch.indexBuilder);
        }
    }

    /** All recipe types carried in the item's "recipes" list. */
    private void generateTypedRecipes(NeuItem item, Batch batch) {
        if (item.recipes() == null) {
            return;
        }
        for (NeuRecipe recipeData : item.recipes()) {
            batch.parseAttempts++;
            ReliableClientRecipe recipe = switch (recipeData) {
                case NeuRecipe.CraftingRecipe crafting ->
                        CraftingRecipeParser.parse(item, crafting, itemRegistry);
                case NeuRecipe.ForgeRecipe forge -> ForgeRecipeParser.parse(item, forge, itemRegistry);
                case NeuRecipe.KatGradeRecipe kat -> KatUpgradeRecipeParser.parse(item, kat, itemRegistry);
                case NeuRecipe.NpcShopRecipe shop -> NpcShopRecipeParser.parse(item, shop, itemRegistry);
                case NeuRecipe.DropsRecipe drops -> DropsRecipeParser.parse(item, drops, itemRegistry);
                case NeuRecipe.TradeRecipe trade -> TradeRecipeParser.parse(item, trade, itemRegistry);
            };

            if (recipe != null) {
                batch.recipes.add(recipe);
                indexRecipe(recipe, item, extractIngredients(recipeData), batch.indexBuilder);
            } else {
                batch.parseFailures++;
            }
        }
    }

    // ---- Constants-driven categories ----

    private void generateEssenceUpgrades(Batch batch) {
        List<ReliableClientRecipe> essenceRecipes = EssenceUpgradeGenerator.generateAll(constantsRegistry, itemRegistry);
        for (ReliableClientRecipe recipe : essenceRecipes) {
            batch.recipes.add(recipe);
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
                batch.indexBuilder.addResult(resultName, recipe.getId());
            }
        }
    }

    private void generateReforges(Batch batch) {
        List<ReliableClientRecipe> reforgeRecipes = ReforgeRecipeGenerator.generateAll(constantsRegistry, itemRegistry);
        for (ReliableClientRecipe recipe : reforgeRecipes) {
            batch.recipes.add(recipe);
            if (recipe instanceof SkyblockReforgeClientRecipe reforge) {
                // Index by every item this reforge applies to
                for (String resultName : reforge.getResultInternalNames()) {
                    if (!resultName.isEmpty()) {
                        batch.indexBuilder.addResult(resultName, recipe.getId());
                    }
                }
                // Index stone as ingredient
                String stoneName = reforge.getStoneInternalName();
                if (!stoneName.isEmpty()) {
                    batch.indexBuilder.addIngredient(stoneName, recipe.getId());
                }
            }
        }
    }

    /** Garden mutation recipes (from built-in resource). */
    private void generateGardenMutations(Batch batch) {
        for (GardenMutation mutation : GardenMutationRegistry.all()) {
            Identifier recipeId = IdentifierUtil.skyRecipeId("garden_mutation/", mutation.id());
            List<String> wikiUrls = itemRegistry.getByInternalName(mutation.id())
                    .filter(item -> "WIKI_URL".equals(item.infoType()))
                    .map(NeuItem::info)
                    .orElse(List.of());
            SkyblockGardenMutationClientRecipe recipe =
                    new SkyblockGardenMutationClientRecipe(recipeId, mutation, itemRegistry, wikiUrls);
            batch.recipes.add(recipe);
            batch.indexBuilder.addResult(mutation.id(), recipeId);
            // Index ingredients
            for (int row = 0; row < mutation.gridSize(); row++) {
                for (int col = 0; col < mutation.gridSize(); col++) {
                    if (mutation.isIngredient(row, col)) {
                        String ingId = mutation.ingredientIdAt(row, col);
                        if (!ingId.isEmpty()) {
                            batch.indexBuilder.addIngredient(ingId, recipeId);
                        }
                    }
                }
            }
        }
    }

    /** Shard fusion recipes (from the SkyShards dataset, fetched at startup). */
    private void generateShardFusions(Batch batch) {
        List<ReliableClientRecipe> fusionRecipes = ShardFusionGenerator.generateAll(constantsRegistry, itemRegistry);
        for (ReliableClientRecipe recipe : fusionRecipes) {
            batch.recipes.add(recipe);
            if (recipe instanceof SkyblockFusionClientRecipe fusion) {
                batch.indexBuilder.addResult(fusion.getOutputInternalName(), recipe.getId());
                for (String inputName : fusion.getInputInternalNames()) {
                    batch.indexBuilder.addIngredient(inputName, recipe.getId());
                }
            }
        }
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

    /**
     * Runs a category generator, logging and recording a failure entry if it throws.
     * A whole category throwing is degraded-but-survivable, so it must never abort the
     * per-item generation loop.
     */
    private static void runGenerator(String label, List<String> failures, Runnable body) {
        try {
            body.run();
        } catch (Exception e) {
            LOGGER.error("Failed to generate {} recipes", label, e);
            failures.add(label);
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

    /** Mutable accumulator threaded through one generation pass. */
    private static final class Batch {
        final List<ReliableClientRecipe> recipes = new ArrayList<>();
        final RecipeIndex.Builder indexBuilder = new RecipeIndex.Builder();
        final List<String> generatorFailures = new ArrayList<>();
        int parseAttempts;
        int parseFailures;
    }

    /**
     * Immutable result of recipe generation. {@code parseAttempts} counts every
     * item entry that carried recipe data; {@code parseFailures} counts those
     * whose parser produced nothing — the ratio gates injection so a systemic
     * NEU format change can never silently publish an empty recipe set.
     * {@code generatorFailures} names whole categories (essence/reforge/garden)
     * that threw — a single increment could never trip the 5% gate, so these are
     * surfaced as a distinct DEGRADED error after injection instead.
     */
    public record RecipeResult(List<ReliableClientRecipe> recipes, RecipeIndex index,
                               int parseAttempts, int parseFailures, List<String> generatorFailures) {
    }
}
