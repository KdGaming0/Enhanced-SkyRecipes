package com.github.kdgaming0.skyrecipes.core.recipe.generators;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ReforgeTypeResolver;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockReforgeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates per-rarity reforge recipes from NEU constants.
 *
 * <p>One client recipe is created per reforge (carrying every rarity's stats). The
 * card matches an item by type only; the clicked item's rarity is resolved at render
 * time so it shows that item's rarity (clamped to the highest tier the data covers).</p>
 *
 * <p>Both blacksmith reforges ({@code reforges.json}) and reforge stones
 * ({@code reforgestones.json}) are expanded across their {@code requiredRarities}.
 * Result names and seed stacks (one per distinct vanilla item, uncapped — RRV's
 * recipe cache is keyed by vanilla item, so a missing seed hides the recipe for
 * that item entirely) are computed once per reforge and shared across its
 * rarity variants.</p>
 */
public final class ReforgeRecipeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReforgeRecipeGenerator.class);

    private ReforgeRecipeGenerator() {
    }

    /**
     * Generate all reforge recipes.
     */
    public static List<ReliableClientRecipe> generateAll(ConstantsRegistry constantsRegistry, ItemRegistry itemRegistry) {
        List<ReliableClientRecipe> recipes = new ArrayList<>();
        if (constantsRegistry == null || itemRegistry == null) {
            return recipes;
        }

        // Pre-build reverse indexes once for fast result matching.
        Map<String, List<NeuItem>> itemsByLoreType = new HashMap<>();
        Map<String, NeuItem> itemsByInternalName = new HashMap<>();
        Map<String, List<NeuItem>> itemsByVanillaId = new HashMap<>();
        for (NeuItem item : itemRegistry.getAllItems()) {
            itemsByInternalName.put(item.internalName(), item);
            String loreType = ReforgeTypeResolver.extractLoreType(item);
            if (loreType != null) {
                itemsByLoreType.computeIfAbsent(loreType, _ -> new ArrayList<>()).add(item);
            }
            String vanillaId = item.itemId();
            if (vanillaId != null && !vanillaId.isEmpty()) {
                itemsByVanillaId.computeIfAbsent(vanillaId, _ -> new ArrayList<>()).add(item);
            }
        }

        // Build Malik skin stack once for blacksmith recipes
        ItemStack malikStack = ItemStack.EMPTY;
        var malikOpt = itemRegistry.getByInternalName("MALIK_NPC");
        if (malikOpt.isPresent()) {
            malikStack = ItemStackBuilder.build(malikOpt.get());
        } else {
            LOGGER.warn("MALIK_NPC not found in registry; blacksmith recipes will not show skin preview");
        }

        // Blacksmith reforges — one card per reforge, carrying every rarity's data.
        for (ReforgeData reforge : constantsRegistry.getAllReforges().values()) {
            Set<String> resultNames = computeResultNames(
                    reforge.itemTypes(), itemsByLoreType, itemsByInternalName, itemsByVanillaId);
            List<ItemStack> seedStacks = buildSeedStacks(resultNames, itemsByInternalName);

            Identifier recipeId = IdentifierUtil.skyRecipeId(
                    "reforge/blacksmith/", sanitizeId(reforge.reforgeName()));

            recipes.add(new SkyblockReforgeClientRecipe(
                    recipeId,
                    ItemStack.EMPTY,
                    malikStack,
                    true,
                    reforge.reforgeName(),
                    resultNames,
                    seedStacks,
                    reforge.requiredRarities(),
                    reforge.statsPerRarity(),
                    reforge.reforgeAbility(),
                    toIntCosts(reforge.reforgeCosts()),
                    List.of()
            ));
        }

        // Reforge stones — one card per stone, carrying every rarity's data.
        for (ReforgeStoneData stone : constantsRegistry.getAllReforgeStones().values()) {
            Set<String> resultNames = computeResultNames(
                    stone.itemTypes(), itemsByLoreType, itemsByInternalName, itemsByVanillaId);
            List<ItemStack> seedStacks = buildSeedStacks(resultNames, itemsByInternalName);
            ItemStack stoneStack = buildStoneStack(stone.internalName(), itemRegistry);

            Identifier recipeId = IdentifierUtil.skyRecipeId(
                    "reforge/stone/", sanitizeId(stone.internalName()));

            recipes.add(new SkyblockReforgeClientRecipe(
                    recipeId,
                    stoneStack,
                    ItemStack.EMPTY,
                    false,
                    stone.reforgeName(),
                    resultNames,
                    seedStacks,
                    stone.requiredRarities(),
                    stone.reforgeStats(),
                    stone.reforgeAbility(),
                    toIntCosts(stone.reforgeCosts()),
                    List.of()
            ));
        }

        LOGGER.info("Generated {} reforge recipes", recipes.size());
        return recipes;
    }

    /**
     * Computes the internal names of all items matching the given reforge criteria.
     *
     * <p>{@code itemTypes} is split by comma and slash. Each token is tried as a
     * reforge type resolved through lore types first, then as an internal name,
     * then as a vanilla item id (NEU's {@code {"itemId": [...]}} form — contains
     * a colon, so the slash split never breaks it).</p>
     */
    private static Set<String> computeResultNames(String itemTypes,
                                                  Map<String, List<NeuItem>> itemsByLoreType,
                                                  Map<String, NeuItem> itemsByInternalName,
                                                  Map<String, List<NeuItem>> itemsByVanillaId) {
        if (itemTypes == null || itemTypes.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        // Vanilla ids contain a slash-free "namespace:path", so splitting on '/'
        // only separates compound reforge types like "SWORD/ROD".
        String[] tokens = itemTypes.split("[,/]");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;

            // Known reforge type → lore types → items. Must run before the
            // internal-name lookup: "BOW" is both a reforge type and a NEU
            // internal name (the vanilla Bow), and the item match would
            // otherwise swallow every bow.
            List<String> loreTypes = ReforgeTypeResolver.getLoreTypesForReforgeType(t);
            if (!loreTypes.isEmpty()) {
                for (String loreType : loreTypes) {
                    List<NeuItem> items = itemsByLoreType.get(loreType);
                    if (items != null) {
                        for (NeuItem i : items) {
                            names.add(i.internalName());
                        }
                    }
                }
                continue;
            }

            // Specific internal name
            NeuItem item = itemsByInternalName.get(t);
            if (item != null) {
                names.add(t);
                continue;
            }

            // Vanilla item id: all NEU items with that base item
            if (t.indexOf(':') >= 0) {
                List<NeuItem> byVanilla = itemsByVanillaId.get(t);
                if (byVanilla != null) {
                    for (NeuItem i : byVanilla) {
                        names.add(i.internalName());
                    }
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Builds one representative stack per distinct vanilla item among the results.
     * These seed RRV's item-keyed recipe cache; the redirect checks do the exact
     * matching, so one stack per vanilla item is enough and keeps memory small.
     */
    private static List<ItemStack> buildSeedStacks(Set<String> resultNames,
                                                   Map<String, NeuItem> itemsByInternalName) {
        if (resultNames.isEmpty()) {
            return List.of();
        }
        Set<String> seenVanillaIds = new HashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        int failures = 0;
        for (String name : resultNames) {
            NeuItem item = itemsByInternalName.get(name);
            if (item == null) continue;
            String vanillaId = item.itemId();
            if (vanillaId == null || vanillaId.isEmpty() || !seenVanillaIds.add(vanillaId)) {
                continue;
            }
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            } catch (Exception e) {
                // A broken representative must not hide the reforge for other items
                seenVanillaIds.remove(vanillaId);
                failures++;
            }
        }
        if (failures > 0) {
            LOGGER.debug("Failed to build {} seed stack(s) for reforge results", failures);
        }
        return List.copyOf(stacks);
    }

    private static ItemStack buildStoneStack(String internalName, ItemRegistry registry) {
        if (internalName == null || internalName.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var opt = registry.getByInternalName(internalName);
        if (opt.isPresent()) {
            return ItemStackBuilder.build(opt.get());
        }
        return ItemStack.EMPTY;
    }

    /**
     * Narrows a rarity → coin-cost map from {@link Number} to {@code int}, which is
     * all the reforge card needs (SkyBlock reforge costs are whole coins).
     */
    private static Map<String, Integer> toIntCosts(Map<String, Number> costs) {
        if (costs == null || costs.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>(costs.size());
        for (Map.Entry<String, Number> e : costs.entrySet()) {
            if (e.getValue() != null) {
                result.put(e.getKey(), e.getValue().intValue());
            }
        }
        return result;
    }

    private static String sanitizeId(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
