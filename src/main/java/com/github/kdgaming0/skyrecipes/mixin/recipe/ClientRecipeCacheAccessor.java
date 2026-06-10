package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for RRV's {@link ClientRecipeCache#localCacheBuilt} flag.
 *
 * <p>SkyRecipes uses direct MethodHandle injection to populate the cache, bypassing
 * {@code buildRecipeCache(false)}. This leaves {@code localCacheBuilt} at {@code false},
 * causing RRV to trigger an expensive clear+rebuild on first recipe view open.
 * Setting the flag to {@code true} after injection completes prevents that redundant work.</p>
 */
@Mixin(value = ClientRecipeCache.class, remap = false)
public interface ClientRecipeCacheAccessor {

    @Accessor("localCacheBuilt")
    void skyrecipes$setLocalCacheBuilt(boolean value);
}
