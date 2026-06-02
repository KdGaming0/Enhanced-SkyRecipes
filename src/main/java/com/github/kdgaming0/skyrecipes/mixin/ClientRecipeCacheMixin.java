package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.client.recipe.InternalRecipeManager;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips redundant RRV client recipe cache rebuilds on world join.
 *
 * <p>RRV calls {@code buildRecipeCache(true)} on every {@code ClientRecipeSynchronizedEvent},
 * which fires on every world join / server switch. For Hypixel SkyBlock players who switch
 * lobbies frequently, this causes expensive full-cache rebuilds even though SkyRecipes'
 * client-only recipes never change between joins.</p>
 *
 * <p>This mixin cancels {@code buildRecipeCache(true)} when SkyRecipes recipes are already
 * loaded and have not been invalidated. The {@code InternalRecipeManager.setRecipesSynced(true)}
 * call is preserved so RRV still knows vanilla recipes have been synchronized.</p>
 *
 * <p><b>RRV API gap:</b> RRV provides no public API to suppress automatic cache rebuilds
 * or to mark a {@code ClientRecipeProvider} as cache-persistent. The stable API only
 * offers {@code ItemView.addClientRecipeProvider()}, which is re-invoked on every rebuild.</p>
 *
 * <p><b>Upstream feature request:</b> Not yet filed. Remove this mixin if RRV adds a
 * public mechanism to skip rebuilds (e.g., a provider flag or a rebuild-guard API).</p>
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
}
