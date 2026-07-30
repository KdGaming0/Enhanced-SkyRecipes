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

        // Per-item generation is CPU-bound (SNBT parsing and stack building dominate) and
        // pure: every parser and builder is stateless, the registries are immutable, and the
        // one shared sink — NpcInfoRegistry's pending map — is a ConcurrentHashMap. So it
        // fans out across the common pool, like the stack build in SkyRecipesClientPlugin.
        // The index-addressed array preserves registry order and the serial merge replays it
        // exactly: recipe order is load-bearing, because the batched injector passes each
        // recipe's list index to RRV as its dedup id.
        List<NeuItem> items = List.copyOf(itemRegistry.getAllItems());
        ItemRecipes[] built = new ItemRecipes[items.size()];
        java.util.stream.IntStream.range(0, items.size()).parallel()
                .forEach(i -> built[i] = generateForItem(items.get(i)));

        for (ItemRecipes produced : built) {
            merge(produced, batch);
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

    /** All recipes one item contributes. Runs on a worker; touches no shared state. */
    private ItemRecipes generateForItem(NeuItem item) {
        ItemRecipes out = new ItemRecipes(item);
        generateCraftingRecipe(item, out);
        AttributeShardData shard = generateShardInfoRecipe(item, out);
        generateWikiInfoRecipes(item, shard, out);
        generateNpcInfoRecipe(item, out);
        generateTypedRecipes(item, out);
        return out;
    }

    /** Folds one item's contribution into the pass accumulator, in generation order. */
    private void merge(ItemRecipes produced, Batch batch) {
        batch.parseAttempts += produced.parseAttempts;
        batch.parseFailures += produced.parseFailures;
        for (int i = 0; i < produced.recipes.size(); i++) {
            ReliableClientRecipe recipe = produced.recipes.get(i);
            batch.recipes.add(recipe);
            indexRecipe(recipe, produced.item, produced.ingredients.get(i), batch.indexBuilder);
        }
    }

    private void generateCraftingRecipe(NeuItem item, ItemRecipes out) {
        if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
            out.parseAttempts++;
            ReliableClientRecipe recipe = CraftingRecipeParser.parse(item, crafting, itemRegistry);
            if (recipe != null) {
                out.add(recipe, crafting.grid().values());
            } else {
                out.parseFailures++;
            }
        }
    }

    /**
     * Attribute shard info recipes (from constants) — shards otherwise have
     * no recipe or info data at all, so this is their only card.
     *
     * @return the shard data if this item is a shard, for the wiki-card skip below
     */
    private AttributeShardData generateShardInfoRecipe(NeuItem item, ItemRecipes out) {
        AttributeShardData shard = constantsRegistry != null
                ? constantsRegistry.getAttributeShard(item.internalName())
                : null;
        if (shard != null) {
            ReliableClientRecipe shardRecipe = ShardInfoRecipeBuilder.build(item, shard);
            if (shardRecipe != null) {
                out.add(shardRecipe, List.of());
            }
        }
        return shard;
    }

    /**
     * Wiki info recipes (skip NPCs — they get a unified NPC info card instead;
     * skip shards — their card above already carries the wiki URLs).
     */
    private void generateWikiInfoRecipes(NeuItem item, AttributeShardData shard, ItemRecipes out) {
        String internalName = item.internalName();
        boolean isNpc = internalName != null && internalName.endsWith("_NPC");
        if (!isNpc && shard == null && item.infoType() != null && !item.infoType().isEmpty()
                && item.info() != null && !item.info().isEmpty()) {
            List<ReliableClientRecipe> wikiRecipes = WikiInfoRecipeBuilder.build(item);
            for (ReliableClientRecipe recipe : wikiRecipes) {
                out.add(recipe, List.of());
            }
        }
    }

    private void generateNpcInfoRecipe(NeuItem item, ItemRecipes out) {
        ReliableClientRecipe npcInfoRecipe = NpcInfoRecipeBuilder.build(item);
        if (npcInfoRecipe != null) {
            out.add(npcInfoRecipe, List.of());
        }
    }

    /** All recipe types carried in the item's "recipes" list. */
    private void generateTypedRecipes(NeuItem item, ItemRecipes out) {
        if (item.recipes() == null) {
            return;
        }
        for (NeuRecipe recipeData : item.recipes()) {
            out.parseAttempts++;
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
                out.add(recipe, extractIngredients(recipeData));
            } else {
                out.parseFailures++;
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

    /**
     * One item's contribution, collected off-thread. Parallel arrays rather than a map so
     * the serial merge can replay recipes and their ingredient strings in generation order.
     * Confined to a single worker thread until {@link #merge} folds it in.
     */
    private static final class ItemRecipes {
        final NeuItem item;
        final List<ReliableClientRecipe> recipes = new ArrayList<>(2);
        final List<java.util.Collection<String>> ingredients = new ArrayList<>(2);
        int parseAttempts;
        int parseFailures;

        ItemRecipes(NeuItem item) {
            this.item = item;
        }

        void add(ReliableClientRecipe recipe, java.util.Collection<String> ingredientStrings) {
            recipes.add(recipe);
            ingredients.add(ingredientStrings);
        }
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
