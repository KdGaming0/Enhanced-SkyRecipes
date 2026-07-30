package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.rrv.overlay.ItemExclusionCache;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhances RRV's item list filtering for SkyBlock items.
 *
 * <p><b>Vanilla base item filtering:</b> Injects into {@code fullStackList} at RETURN to
 * remove plain vanilla {@code ItemStack}s (those with no custom components) while keeping
 * stack-sensitives intact. This avoids the problem with {@code ItemView.excludeItems()},
 * which excludes by base {@code Item} and therefore also hides stack-sensitives built on
 * that same base item.</p>
 *
 * <p><b>SkyBlock search integration:</b> Injects into {@code defaultFilter} to use
 * {@link com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex} when data is
 * loaded. This replaces RRV's naive substring search with token-based AND search across
 * display names, internal names, lore, stats, rarity, type, and reforge names.</p>
 */
@Mixin(value = ItemFilters.class, remap = false)
public class ItemFiltersMixin {

    /**
     * Filters out plain vanilla base items from RRV's {@code fullStackList()}.
     *
     * <p>SkyBlock items are registered as stack-sensitives and always carry at least
     * {@code CUSTOM_NAME} (and usually {@code CUSTOM_DATA}, {@code PROFILE}, etc.).
     * Plain vanilla items created via {@code new ItemStack(item)} have none of these
     * components. By filtering at RETURN we only affect the final list — stack-sensitives
     * remain untouched.</p>
     *
     * <p>Applies even when the pipeline failed: an empty list plus the failure notice
     * (see {@code ItemViewOverlayMixin}) is deliberate — vanilla items are meaningless
     * for SkyBlock and would only look like wrong data.</p>
     */
    @Inject(method = "fullStackList", at = @At("RETURN"), cancellable = true, remap = false)
    private static void skyrecipes$removeVanillaBaseItems(CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> original = cir.getReturnValue();
        List<ItemStack> filtered = new ArrayList<>(original.size());
        for (ItemStack stack : original) {
            // Keep items that have SkyBlock-specific custom components.
            // Plain vanilla base items have none of these.
            if (stack.has(DataComponents.CUSTOM_NAME)
                    || stack.has(DataComponents.CUSTOM_DATA)
                    || stack.has(DataComponents.PROFILE)
                    || stack.has(DataComponents.DYED_COLOR)
                    || stack.has(DataComponents.UNBREAKABLE)) {
                filtered.add(stack);
            }
        }
        cir.setReturnValue(filtered);
    }

    // -- Single-entry filter memo ---------------------------------------------
    // RRV's updateQuery has no unchanged-query early-out and re-fires on every container
    // open, resize, and widget rebuild (see GroupingResultCache for the full chain), so the
    // identical filter is recomputed constantly. Grouping was already memoized; this covers
    // the filter that feeds it. The index reference is part of the key, so a reload that
    // republishes the index can never serve stale results. Render-thread only, like every
    // caller of defaultFilter (the side panel uses advancedFilter, off this path).
    @Unique
    private static String skyrecipes$memoQuery;
    @Unique
    private static SkyblockItemCategory skyrecipes$memoCategory;
    @Unique
    private static Object skyrecipes$memoIndex;
    @Unique
    private static List<ItemStack> skyrecipes$memoResult;

    @Inject(method = "defaultFilter", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skyrecipes$skyblockSearchFilter(String query,
                                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        var index = SkyRecipesClientPlugin.getSearchIndex();
        if (index == null) {
            return;
        }
        SkyblockItemCategory category = CategoryState.getButtonCategory();

        if (skyrecipes$memoResult != null
                && skyrecipes$memoIndex == index
                && skyrecipes$memoCategory == category
                && java.util.Objects.equals(skyrecipes$memoQuery, query)) {
            cir.setReturnValue(skyrecipes$memoResult);
            return;
        }

        // Safe to hand the same list back on a hit for the same reason filter("") may return
        // the immutable master list: RRV consumes the result via filteredItems.addAll(...)
        // and never mutates it (verified against 8.6.4).
        List<ItemStack> result = category != null
                ? index.filter(query, category, null)
                : index.filter(query);

        skyrecipes$memoQuery = query;
        skyrecipes$memoCategory = category;
        skyrecipes$memoIndex = index;
        skyrecipes$memoResult = result;
        cir.setReturnValue(result);
    }

    /**
     * RRV's own "the item index is stale" signal — fired on world change, resource reload,
     * and index-source changes. It is the only invalidation channel for the tag-driven half
     * of {@link ItemExclusionCache} ({@code rrv:excluded_potions} /
     * {@code rrv:excluded_enchantments} membership changes on a resource reload without
     * touching the exclusion collections' sizes), so the memo can never outlive the list it
     * describes. The filter memo above is keyed on the search-index identity instead, which
     * is independent of RRV's stack cache — but it costs nothing to drop it here too.
     */
    @Inject(method = "clearCaches", at = @At("TAIL"), remap = false)
    private static void skyrecipes$invalidateOnCacheClear(CallbackInfo ci) {
        ItemExclusionCache.invalidate();
        skyrecipes$memoQuery = null;
        skyrecipes$memoResult = null;
        skyrecipes$memoIndex = null;
    }
}
