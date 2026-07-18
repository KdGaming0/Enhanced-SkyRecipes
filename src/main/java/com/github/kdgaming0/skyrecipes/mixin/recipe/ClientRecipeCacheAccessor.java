package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashMap;
import java.util.List;

/**
 * Accessors for RRV's {@link ClientRecipeCache} private state.
 *
 * <p>{@code localCacheBuilt}: SkyRecipes uses direct MethodHandle injection to populate
 * the cache, bypassing {@code buildRecipeCache(false)}. This leaves the flag at
 * {@code false}, causing RRV to trigger an expensive clear+rebuild on first recipe view
 * open. Setting it to {@code true} after injection completes prevents that redundant work.</p>
 *
 * <p>{@code stackSensitives}: read by {@code StackGroupItemsCache.prewarm} to snapshot
 * all stack sensitives in one pass over the backing map (~200 populated base items)
 * instead of streaming {@code streamStackSensitives(item)} for every entry of the full
 * item registry on the render thread. Field name verified against RRV 8.6.3 sources.</p>
 */
@Mixin(value = ClientRecipeCache.class, remap = false)
public interface ClientRecipeCacheAccessor {

    @Accessor("localCacheBuilt")
    void skyrecipes$setLocalCacheBuilt(boolean value);

    @Accessor("stackSensitives")
    HashMap<Item, List<ItemView.StackSensitive>> skyrecipes$getStackSensitives();
}
