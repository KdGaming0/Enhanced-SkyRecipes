package com.github.kdgaming0.skyrecipes.mixin.accessor;

import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Exposes RRV's private {@code StackGroupManager.sortByGroupOrder} so the background
 * prewarm in {@code StackGroupItemsCache} can apply the exact same ordering (saved
 * per-group order, falling back to registry order) when publishing precomputed group
 * contents. Must be invoked on the render thread only: the fallback path lazily builds
 * RRV's {@code registryOrderCache}, which is not thread-safe.
 */
@Mixin(value = StackGroupManager.class, remap = false)
public interface StackGroupManagerAccessor {

    @Invoker("sortByGroupOrder")
    static void skyrecipes$sortByGroupOrder(List<ItemStack> items, Identifier groupId) {
        throw new AssertionError();
    }
}
