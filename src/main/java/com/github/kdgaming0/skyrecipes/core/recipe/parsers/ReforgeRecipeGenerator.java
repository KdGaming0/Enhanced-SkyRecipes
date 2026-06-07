package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockReforgeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates per-rarity reforge recipes from NEU constants.
 *
 * <p>One client recipe is created per (reforge, rarity) pair. When a player
 * clicks a reforgable item in RRV, only recipes whose rarity matches the item's
 * rarity and whose item type applies to the item are shown.</p>
 *
 * <p>Both blacksmith reforges ({@code reforges.json}) and reforge stones
 * ({@code reforgestones.json}) are expanded across their {@code requiredRarities}.
 * Result item lists are pre-computed during generation so the client recipe never
 * has to scan the full {@link ItemRegistry}.</p>
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
        for (NeuItem item : itemRegistry.getAllItems()) {
            itemsByInternalName.put(item.internalName(), item);
            String loreType = ReforgeTypeResolver.extractLoreType(item);
            if (loreType != null) {
                itemsByLoreType.computeIfAbsent(loreType, k -> new ArrayList<>()).add(item);
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

        // Blacksmith reforges
        for (ReforgeData reforge : constantsRegistry.getAllReforges().values()) {
            List<String> resultNames = computeResultNames(reforge.itemTypes(), itemsByLoreType, itemsByInternalName);
            for (String rarity : reforge.requiredRarities()) {
                Map<String, Number> stats = reforge.statsPerRarity().getOrDefault(rarity, Map.of());
                String ability = pickAbility(reforge.reforgeAbility(), rarity);
                Number cost = reforge.reforgeCosts().get(rarity);

                Identifier recipeId = IdentifierUtil.skyRecipeId(
                        "reforge/blacksmith/", sanitizeId(reforge.reforgeName()) + "/" + rarity);

                recipes.add(new SkyblockReforgeClientRecipe(
                        recipeId,
                        ItemStack.EMPTY,
                        malikStack,
                        true,
                        reforge.reforgeName(),
                        rarity,
                        resultNames,
                        stats,
                        ability,
                        cost != null ? cost.intValue() : 0,
                        List.of()
                ));
            }
        }

        // Reforge stones
        for (ReforgeStoneData stone : constantsRegistry.getAllReforgeStones().values()) {
            List<String> resultNames = computeResultNames(stone.itemTypes(), itemsByLoreType, itemsByInternalName);
            ItemStack stoneStack = buildStoneStack(stone.internalName(), itemRegistry);

            for (String rarity : stone.requiredRarities()) {
                Map<String, Number> stats = stone.reforgeStats().getOrDefault(rarity, Map.of());
                String ability = pickAbility(stone.reforgeAbility(), rarity);
                Number cost = stone.reforgeCosts().get(rarity);

                Identifier recipeId = IdentifierUtil.skyRecipeId(
                        "reforge/stone/", sanitizeId(stone.internalName()) + "/" + rarity);

                recipes.add(new SkyblockReforgeClientRecipe(
                        recipeId,
                        stoneStack,
                        ItemStack.EMPTY,
                        false,
                        stone.reforgeName(),
                        rarity,
                        resultNames,
                        stats,
                        ability,
                        cost != null ? cost.intValue() : 0,
                        List.of()
                ));
            }
        }

        LOGGER.info("Generated {} reforge recipes", recipes.size());
        return recipes;
    }

    /**
     * Computes the internal names of all items matching the given reforge criteria.
     *
     * <p>{@code itemTypes} is split by comma and slash. Each token is tried as an
     * internal name first; if not found, it is treated as a reforge type and all
     * matching lore types are resolved.</p>
     */
    private static List<String> computeResultNames(String itemTypes,
                                                   Map<String, List<NeuItem>> itemsByLoreType,
                                                   Map<String, NeuItem> itemsByInternalName) {
        if (itemTypes == null || itemTypes.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        String[] tokens = itemTypes.split("[,/]");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;

            // Try as specific internal name first
            NeuItem item = itemsByInternalName.get(t);
            if (item != null) {
                names.add(t);
                continue;
            }

            // Try as reforge type → lore types → items
            List<String> loreTypes = ReforgeTypeResolver.getLoreTypesForReforgeType(t);
            for (String loreType : loreTypes) {
                List<NeuItem> items = itemsByLoreType.get(loreType);
                if (items != null) {
                    for (NeuItem i : items) {
                        names.add(i.internalName());
                    }
                }
            }
        }
        return List.copyOf(names);
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
     * Picks the ability text for the given rarity.
     * Falls back to the generic {@code "ability"} key when no per-rarity entry exists.
     */
    private static String pickAbility(Map<String, String> abilityMap, String rarity) {
        if (abilityMap == null || abilityMap.isEmpty()) {
            return "";
        }
        String perRarity = abilityMap.get(rarity);
        if (perRarity != null && !perRarity.isEmpty()) {
            return perRarity;
        }
        String generic = abilityMap.get("ability");
        return generic != null ? generic : "";
    }

    private static String sanitizeId(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
