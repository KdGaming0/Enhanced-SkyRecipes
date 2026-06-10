package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.client.recipe.InternalRecipeManager;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockRecipeCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Skips redundant RRV client recipe cache rebuilds on world join and short-circuits
 * recipe lookups for SkyBlock items to avoid dropper/skull collisions.
 *
 * <p><b>Rebuild suppression:</b> RRV calls {@code buildRecipeCache(true)} on every
 * {@code ClientRecipeSynchronizedEvent}, which fires on every world join / server switch.
 * For Hypixel SkyBlock players who switch lobbies frequently, this causes expensive full-cache
 * rebuilds even though SkyRecipes' client-only recipes never change between joins.</p>
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
@Mixin(ClientRecipeCache.class)
public class ClientRecipeCacheMixin {

    @Inject(method = "buildRecipeCache", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$skipRedundantRebuild(boolean rebuildFromSynchronizedRecipes, CallbackInfo ci) {
        if (rebuildFromSynchronizedRecipes && SkyRecipesClientPlugin.areRecipesReady()) {
            InternalRecipeManager.INSTANCE.setRecipesSynced(true);
            ci.cancel();
            org.slf4j.LoggerFactory.getLogger(ClientRecipeCacheMixin.class)
                    .debug("Cancelled redundant RRV buildRecipeCache(true) — SkyRecipes recipes already loaded");
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
