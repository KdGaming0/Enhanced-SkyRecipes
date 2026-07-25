package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.family.FamilyResolver;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    /**
     * Recipes matched by ID predicate at lookup time ({@link SkyblockIdMatchingRecipe}),
     * e.g. reforges, whose applicability spans far more items than their exposed stacks.
     */
    private static volatile List<ReliableClientRecipe> idMatchingRecipes = List.of();
    private static volatile FamilyResolver familyResolver;

    private SkyblockRecipeCache() {
    }

    /**
     * Set the family resolver used for result-lookup expansion.
     * Must be called before {@link #rebuild(List)}.
     */
    public static void setFamilyResolver(FamilyResolver resolver) {
        familyResolver = resolver;
    }

    /**
     * Rebuild the parallel index from the given recipe list.
     *
     * <p>This method is thread-safe and may be called from a background thread. The
     * volatile maps ensure visibility to the render thread that services R/U key lookups.</p>
     *
     * <p>The build is parallelized across recipes and uses a shared per-rebuild cache for
     * {@link SkyblockIdExtractor#extract(ItemStack)} to avoid re-parsing NBT for
     * stacks that appear in multiple recipes.</p>
     *
     * @param recipes the full list of SkyRecipes client recipes (already config-filtered)
     */
    public static void rebuild(List<ReliableClientRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            byIngredientId = Map.of();
            byResultId = Map.of();
            idMatchingRecipes = List.of();
            return;
        }

        ConcurrentMap<String, Set<ReliableClientRecipe>> byIngredient = new ConcurrentHashMap<>();
        ConcurrentMap<String, Set<ReliableClientRecipe>> byResult = new ConcurrentHashMap<>();

        // Per-rebuild cache. ItemStack has no equals/hashCode override (MC 26.1.2),
        // so this only dedups repeated *instances* of the same stack, not equal copies.
        ConcurrentMap<ItemStack, String> idCache = new ConcurrentHashMap<>();

        recipes.parallelStream().forEach(recipe -> indexRecipe(recipe, byIngredient, byResult, idCache));

        // Result tiers drive the bucket sort below. Computing them inside the
        // comparator would re-extract NBT ids O(n log n) times per bucket and
        // once more per bucket the recipe appears in — precompute each exactly
        // once instead, reusing the same per-rebuild id cache.
        Map<ReliableClientRecipe, Integer> tiers = new IdentityHashMap<>(recipes.size() * 2);
        for (ReliableClientRecipe recipe : recipes) {
            tiers.put(recipe, computeResultTier(recipe, idCache));
        }
        Comparator<ReliableClientRecipe> comparator = (a, b) -> {
            int tierA = tiers.getOrDefault(a, 0);
            int tierB = tiers.getOrDefault(b, 0);
            if (tierA != tierB) {
                return Integer.compare(tierA, tierB);
            }
            return a.getId().toString().compareTo(b.getId().toString());
        };

        byIngredientId = byIngredient.entrySet().parallelStream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> sortRecipes(List.copyOf(e.getValue()), comparator)
                ));
        byResultId = byResult.entrySet().parallelStream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> sortRecipes(List.copyOf(e.getValue()), comparator)
                ));
        idMatchingRecipes = recipes.stream()
                .filter(r -> r instanceof SkyblockIdMatchingRecipe)
                .toList();
    }

    private static void indexRecipe(ReliableClientRecipe recipe,
                                    ConcurrentMap<String, Set<ReliableClientRecipe>> byIngredient,
                                    ConcurrentMap<String, Set<ReliableClientRecipe>> byResult,
                                    ConcurrentMap<ItemStack, String> idCache) {
        for (SlotContent slot : recipe.getIngredients()) {
            for (ItemStack stack : slot.getValidContents()) {
                String id = extractCached(stack, idCache);
                if (id != null) {
                    byIngredient.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(recipe);
                }
            }
        }

        for (SlotContent slot : recipe.getResults()) {
            for (ItemStack stack : slot.getValidContents()) {
                String id = extractCached(stack, idCache);
                if (id != null) {
                    byResult.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(recipe);
                }
            }
        }
    }

    private static String extractCached(ItemStack stack, ConcurrentMap<ItemStack, String> idCache) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String cached = idCache.get(stack);
        if (cached != null) {
            return cached;
        }
        // ConcurrentHashMap does not allow null values; only store non-null IDs.
        String id = SkyblockIdExtractor.extract(stack);
        if (id != null) {
            String existing = idCache.putIfAbsent(stack, id);
            return existing != null ? existing : id;
        }
        return null;
    }

    /**
     * Sorts recipes deterministically by result tier ascending, then by recipe ID.
     * This ensures family views display lower tiers before higher tiers.
     */
    private static List<ReliableClientRecipe> sortRecipes(List<ReliableClientRecipe> recipes,
                                                          Comparator<ReliableClientRecipe> comparator) {
        if (recipes.size() <= 1) {
            return recipes;
        }
        List<ReliableClientRecipe> sorted = new ArrayList<>(recipes);
        sorted.sort(comparator);
        return sorted;
    }

    private static int computeResultTier(ReliableClientRecipe recipe,
                                         ConcurrentMap<ItemStack, String> idCache) {
        for (SlotContent slot : recipe.getResults()) {
            for (ItemStack stack : slot.getValidContents()) {
                String id = extractCached(stack, idCache);
                if (id != null) {
                    int tier = FamilyResolver.extractTier(id);
                    if (tier > 0) {
                        return tier;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Look up recipes that use the given stack as an ingredient.
     *
     * @return a <b>mutable</b> list of matching recipes, or {@code null} if the stack is not
     * a SkyBlock item (caller should fall back to RRV's native lookup).
     */
    public static List<ReliableClientRecipe> getRecipesForIngredient(ItemStack stack) {
        String id = lookupId(stack);
        if (id == null) {
            return null;
        }
        List<ReliableClientRecipe> list = byIngredientId.get(id);
        List<ReliableClientRecipe> result = list == null ? new ArrayList<>() : new ArrayList<>(list);
        appendIdMatches(id, result);
        return result;
    }

    /**
     * Look up recipes that produce the given stack as a result.
     *
     * <p>When family expansion is enabled, recipes for all family members are included.
     * The clicked item's recipes are moved to the front of the list so they appear first
     * in the recipe view.</p>
     *
     * @return a <b>mutable</b> list of matching recipes, or {@code null} if the stack is not
     * a SkyBlock item (caller should fall back to RRV's native lookup).
     */
    public static List<ReliableClientRecipe> getRecipesForResult(ItemStack stack) {
        String id = lookupId(stack);
        if (id == null) {
            return null;
        }

        if (!SkyRecipesConfig.familyExpansionEnabled || familyResolver == null) {
            List<ReliableClientRecipe> list = byResultId.get(id);
            List<ReliableClientRecipe> result = list == null ? new ArrayList<>() : new ArrayList<>(list);
            appendIdMatches(id, result);
            return result;
        }

        Set<String> familyIds = familyResolver.getFamilyMembers(id);
        LinkedHashSet<ReliableClientRecipe> merged = new LinkedHashSet<>();
        for (String familyId : familyIds) {
            List<ReliableClientRecipe> list = byResultId.get(familyId);
            if (list != null) {
                merged.addAll(list);
            }
        }

        List<ReliableClientRecipe> result = new ArrayList<>(merged);

        // Move the clicked item's recipes to the front so it is displayed first
        int targetIdx = -1;
        for (int i = 0; i < result.size(); i++) {
            if (recipeContainsResultId(result.get(i), id)) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx > 0) {
            ReliableClientRecipe target = result.remove(targetIdx);
            result.add(0, target);
        }

        // After move-to-front so the clicked item's own recipe stays the default tab.
        appendIdMatches(id, result);
        return result;
    }

    /**
     * The SkyBlock id a clicked stack looks up under.
     *
     * <p>Lookup-time only — the {@link #rebuild(List)} path must keep using
     * {@link SkyblockIdExtractor} directly, because it runs on pipeline workers where no screen
     * exists and every stack it indexes carries a real id anyway.</p>
     *
     * @return the extracted id, the shard-menu fallback for the display-only stacks Hypixel sends
     * in the shard GUIs, or {@code null} for a vanilla item
     */
    private static String lookupId(ItemStack stack) {
        String id = SkyblockIdExtractor.extract(stack);
        return id != null ? id : ShardGuiResolver.resolveCurrent(stack);
    }

    /**
     * Appends every {@link SkyblockIdMatchingRecipe} that applies to {@code id} and is
     * not already present. ~1 set lookup per reforge card — cheap enough per keypress.
     */
    private static void appendIdMatches(String id, List<ReliableClientRecipe> out) {
        if (idMatchingRecipes.isEmpty()) {
            return;
        }
        Set<ReliableClientRecipe> present = Collections.newSetFromMap(new IdentityHashMap<>());
        present.addAll(out);
        for (ReliableClientRecipe recipe : idMatchingRecipes) {
            if (((SkyblockIdMatchingRecipe) recipe).matchesSkyblockId(id) && present.add(recipe)) {
                out.add(recipe);
            }
        }
    }

    private static boolean recipeContainsResultId(ReliableClientRecipe recipe, String targetId) {
        for (SlotContent slot : recipe.getResults()) {
            for (ItemStack candidate : slot.getValidContents()) {
                if (targetId.equals(SkyblockIdExtractor.extract(candidate))) {
                    return true;
                }
            }
        }
        return false;
    }
}
