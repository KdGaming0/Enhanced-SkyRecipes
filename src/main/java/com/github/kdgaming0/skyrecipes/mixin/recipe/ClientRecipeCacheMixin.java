package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.client.recipe.InternalRecipeManager;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Skips redundant RRV client recipe cache rebuilds on world join and short-circuits
 * recipe lookups for SkyBlock items to avoid dropper/skull collisions.
 *
 * <p><b>Rebuild suppression:</b> RRV calls {@code buildRecipeCache(true)} on every
 * {@code ClientRecipeSynchronizedEvent}, which fires on every world join / server switch.
 * For Hypixel SkyBlock players who switch lobbies frequently, this causes expensive full-cache
 * rebuilds even though SkyRecipes' client-only recipes never change between joins.
 * Suppression also covers the batched-injection window: on servers without RRV,
 * {@code ItemViewOverlay.openRecipeView} triggers {@code buildRecipeCache(false)}, whose
 * {@code clear()} is quadratic over the partially injected entries (a multi-second
 * render-thread freeze) and silently drops every recipe injected so far.</p>
 *
 * <p><b>Lookup short-circuit:</b> RRV indexes recipes by vanilla {@code Item} type. Because
 * ~8,000 SkyBlock items map to ~200 base items (mostly {@code minecraft:player_head}),
 * pressing R/U on any skull shows recipes for <em>all</em> skulls. SkyRecipes builds a
 * parallel index keyed by {@code ExtraAttributes.id} and intercepts lookups here.</p>
 *
 * <p><b>RRV API gaps:</b></p>
 * <ul>
 *   <li>No public API to suppress automatic cache rebuilds or mark a provider as persistent.</li>
 *   <li>No public API to customize recipe index keys or register an alternative lookup provider.
 *     {@code ReliableClientRecipe.redirectsAsIngredient} is the only hook, but it is a
 *     post-filter (O(n) bucket scan), not an index replacement.</li>
 * </ul>
 *
 * <p><b>Upstream feature request:</b> Not yet filed. Remove these injects if RRV adds a
 * {@code RecipeIndexCustomizer}, {@code IngredientRedirector}, or rebuild-guard API.</p>
 */
@Mixin(value = ClientRecipeCache.class, remap = false)
public class ClientRecipeCacheMixin {

    @Shadow
    @Final
    private HashMap<Identifier, ReliableClientRecipe> recipeMap;
    @Shadow
    @Final
    private HashMap<Identifier, Identifier> clientEntryMap;
    @Shadow
    @Final
    private HashMap<Identifier, List<Identifier>> multiRecipeMap;
    @Shadow
    @Final
    private HashMap<Item, List<Identifier>> byItemIngredient;
    @Shadow
    @Final
    private HashMap<Item, List<Identifier>> byItemResult;

    /**
     * Replaces RRV's quadratic {@code clear()} with a set-based sweep.
     *
     * <p>RRV's implementation iterates every {@code byItemIngredient}/{@code byItemResult}
     * bucket once <em>per client entry</em>, each with a linear {@code List.remove} —
     * O(entries × buckets × bucket size), a multi-second render-thread freeze at
     * SkyBlock scale (~16k entries). This version collects the entry ids into a set
     * and sweeps each bucket exactly once with {@code removeIf}.</p>
     *
     * <p>It additionally clears {@code clientEntryMap} and prunes {@code multiRecipeMap},
     * which RRV's clear() leaves behind: stale ids made every subsequent clear slower
     * and grew both maps unboundedly across reloads. Both maps are private to
     * {@code ClientRecipeCache} (verified in RRV 8.3.0 sources), and lookups via
     * {@code multiRecipeMap} already null-filter against {@code recipeMap}, so pruning
     * is observationally identical.</p>
     */
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$fastClear(CallbackInfo ci) {
        ci.cancel();
        if (clientEntryMap.isEmpty()) {
            return;
        }
        Set<Identifier> removed = new HashSet<>(clientEntryMap.keySet());
        clientEntryMap.clear();
        recipeMap.keySet().removeAll(removed);
        byItemIngredient.values().forEach(ids -> ids.removeIf(removed::contains));
        byItemIngredient.values().removeIf(List::isEmpty);
        byItemResult.values().forEach(ids -> ids.removeIf(removed::contains));
        byItemResult.values().removeIf(List::isEmpty);
        multiRecipeMap.values().forEach(ids -> ids.removeIf(removed::contains));
        multiRecipeMap.values().removeIf(List::isEmpty);
    }

    /**
     * Skips the absent-element bucket scans in {@code handleClientRecipe} during
     * direct batched injection.
     *
     * <p>RRV dedups every index insert with {@code remove(uniqueId)} before
     * {@code add(uniqueId)}. On first insert the id is absent, so the remove is a
     * full O(n) scan that finds nothing — and SkyBlock's shared-base-item buckets
     * (thousands of {@code player_head} entries) make injection quadratic.</p>
     *
     * <p>During direct injection uniqueIds are globally unique within the batch
     * (see {@link SkyRecipesClientPlugin#isDirectInjectionInFlight()}), so a
     * re-encountered id can only be the one appended earlier in this same
     * {@code handleClientRecipe} call — and every later append in that call either
     * targets a different bucket or re-appends this id, leaving it at the tail.
     * Checking the tail is therefore an exact replacement for the full scan.
     * Outside the injection window the original scan runs unchanged.
     *
     * <p>The removes live in {@code handleClientRecipe}'s forEach lambdas, which
     * javac compiles to the synthetic methods targeted below (verified against the
     * RRV 8.3.0 jar: ingredients = $1, craft references = $2, results = $4).
     * {@code require = 0} because lambda numbering can shift on an RRV update —
     * if the targets vanish, this optimization silently drops out and RRV's
     * original scan runs, instead of failing the whole class transformation
     * (which would break every recipe lookup).</p></p>
     */
    @Redirect(
            method = {
                    "lambda$handleClientRecipe$1",
                    "lambda$handleClientRecipe$2",
                    "lambda$handleClientRecipe$4"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"),
            require = 0
    )
    private boolean skyrecipes$fastDedupRemove(List<Identifier> bucket, Object id) {
        if (SkyRecipesClientPlugin.isDirectInjectionInFlight()) {
            int last = bucket.size() - 1;
            if (last >= 0 && bucket.get(last).equals(id)) {
                bucket.remove(last);
                return true;
            }
            return false;
        }
        return bucket.remove(id);
    }

    @Inject(method = "buildRecipeCache", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$skipRedundantRebuild(boolean rebuildFromSynchronizedRecipes, CallbackInfo ci) {
        if (SkyRecipesClientPlugin.shouldSuppressRrvRebuild()) {
            if (rebuildFromSynchronizedRecipes) {
                InternalRecipeManager.INSTANCE.setRecipesSynced(true);
            }
            ci.cancel();
            org.slf4j.LoggerFactory.getLogger(ClientRecipeCacheMixin.class)
                    .debug("Cancelled RRV buildRecipeCache({}) — SkyRecipes recipes loaded or injection in flight",
                            rebuildFromSynchronizedRecipes);
        }
    }

    /**
     * Short-circuit ingredient lookups for SkyBlock items.
     *
     * <p>If the clicked stack carries a SkyBlock ID, return the pre-filtered list from
     * {@link SkyblockRecipeCache} instead of letting RRV scan every recipe that shares the
     * same vanilla base item.</p>
     */
    @Inject(method = "getRecipesForCraftingInput", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$fastIngredientLookup(ItemStack inputStack,
                                                 CallbackInfoReturnable<List<ReliableClientRecipe>> cir) {
        if (inputStack.isEmpty()) {
            return;
        }
        List<ReliableClientRecipe> recipes = SkyblockRecipeCache.getRecipesForIngredient(inputStack);
        if (recipes != null) {
            cir.setReturnValue(recipes);
        }
    }

    /**
     * Short-circuit result lookups for SkyBlock items.
     *
     * <p>If the clicked stack carries a SkyBlock ID, return the pre-filtered list from
     * {@link SkyblockRecipeCache} instead of letting RRV scan every recipe that shares the
     * same vanilla base item.</p>
     */
    @Inject(method = "getRecipesForCraftingOutput", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$fastResultLookup(ItemStack outputStack,
                                             CallbackInfoReturnable<List<ReliableClientRecipe>> cir) {
        if (outputStack.isEmpty()) {
            return;
        }
        List<ReliableClientRecipe> recipes = SkyblockRecipeCache.getRecipesForResult(outputStack);
        if (recipes != null) {
            cir.setReturnValue(recipes);
        }
    }
}
