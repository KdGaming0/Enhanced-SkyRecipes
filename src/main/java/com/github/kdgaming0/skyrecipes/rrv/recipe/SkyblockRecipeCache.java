package com.github.kdgaming0.skyrecipes.rrv.recipe;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parallel recipe index keyed by SkyBlock internal ID ({@code ExtraAttributes.id}).
 *
 * <p>RRV's native {@code ClientRecipeCache} indexes recipes by vanilla {@link net.minecraft.world.item.Item}
 * type, which causes collisions when thousands of SkyBlock items share the same base item
 * (e.g. {@code minecraft:player_head}). This cache rebuilds a second index keyed by the
 * SkyBlock-specific ID extracted from each recipe's ingredient/result stacks.</p>
 *
 * <p>A mixin into {@code ClientRecipeCache} short-circuits lookups to this index whenever the
 * clicked stack carries a SkyBlock ID, falling back to RRV's native path for vanilla items.</p>
 */
public final class SkyblockRecipeCache {

    private static volatile Map<String, List<ReliableClientRecipe>> byIngredientId = Map.of();
    private static volatile Map<String, List<ReliableClientRecipe>> byResultId = Map.of();
    private static volatile FamilyResolver familyResolver;

    private SkyblockRecipeCache() {}

    /**
     * Set the family resolver used for result-lookup expansion.
     * Must be called before {@link #rebuild(List)} on the main thread.
     */
    public static void setFamilyResolver(FamilyResolver resolver) {
        familyResolver = resolver;
    }

    /**
     * Rebuild the parallel index from the given recipe list.
     *
     * <p>Must be called on the main thread (during RRV injection). The volatile maps ensure
     * visibility to the render thread that services R/U key lookups.</p>
     *
     * @param recipes the full list of SkyRecipes client recipes (already config-filtered)
     */
    public static void rebuild(List<ReliableClientRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            byIngredientId = Map.of();
            byResultId = Map.of();
            return;
        }

        Map<String, LinkedHashSet<ReliableClientRecipe>> byIngredient = new HashMap<>();
        Map<String, LinkedHashSet<ReliableClientRecipe>> byResult = new HashMap<>();

        for (ReliableClientRecipe recipe : recipes) {
            // Index ingredients
            for (SlotContent slot : recipe.getIngredients()) {
                for (ItemStack stack : slot.getValidContents()) {
                    String id = SkyblockIdExtractor.extract(stack);
                    if (id != null) {
                        byIngredient.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(recipe);
                    }
                }
            }

            // Index results
            for (SlotContent slot : recipe.getResults()) {
                for (ItemStack stack : slot.getValidContents()) {
                    String id = SkyblockIdExtractor.extract(stack);
                    if (id != null) {
                        byResult.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(recipe);
                    }
                }
            }
        }

        byIngredientId = byIngredient.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> List.copyOf(e.getValue())
            ));
        byResultId = byResult.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> List.copyOf(e.getValue())
            ));
    }

    /**
     * Look up recipes that use the given stack as an ingredient.
     *
     * @return a <b>mutable</b> list of matching recipes, or {@code null} if the stack is not
     *         a SkyBlock item (caller should fall back to RRV's native lookup).
     */
    public static List<ReliableClientRecipe> getRecipesForIngredient(ItemStack stack) {
        String id = SkyblockIdExtractor.extract(stack);
        if (id == null) {
            return null;
        }
        List<ReliableClientRecipe> list = byIngredientId.get(id);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * Look up recipes that produce the given stack as a result.
     *
     * @return a <b>mutable</b> list of matching recipes, or {@code null} if the stack is not
     *         a SkyBlock item (caller should fall back to RRV's native lookup).
     */
    public static List<ReliableClientRecipe> getRecipesForResult(ItemStack stack) {
        String id = SkyblockIdExtractor.extract(stack);
        if (id == null) {
            return null;
        }

        if (!SkyRecipesConfig.familyExpansionEnabled || familyResolver == null) {
            List<ReliableClientRecipe> list = byResultId.get(id);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        }

        Set<String> familyIds = familyResolver.getFamilyMembers(id);
        LinkedHashSet<ReliableClientRecipe> merged = new LinkedHashSet<>();
        for (String familyId : familyIds) {
            List<ReliableClientRecipe> list = byResultId.get(familyId);
            if (list != null) {
                merged.addAll(list);
            }
        }
        return new ArrayList<>(merged);
    }
}
