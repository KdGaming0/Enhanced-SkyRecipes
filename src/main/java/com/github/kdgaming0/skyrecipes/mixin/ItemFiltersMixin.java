package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
@Mixin(ItemFilters.class)
public class ItemFiltersMixin {

    /**
     * Filters out plain vanilla base items from RRV's {@code fullStackList()}.
     *
     * <p>SkyBlock items are registered as stack-sensitives and always carry at least
     * {@code CUSTOM_NAME} (and usually {@code CUSTOM_DATA}, {@code PROFILE}, etc.).
     * Plain vanilla items created via {@code new ItemStack(item)} have none of these
     * components. By filtering at RETURN we only affect the final list — stack-sensitives
     * remain untouched.</p>
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

    @Inject(method = "defaultFilter", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skyrecipes$skyblockSearchFilter(String query,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        var index = SkyRecipesClientPlugin.getSearchIndex();
        if (index == null) {
            return;
        }
        SkyblockItemCategory category = CategoryState.getButtonCategory();
        if (category != null) {
            cir.setReturnValue(index.filter(query, category, null));
        } else {
            cir.setReturnValue(index.filter(query));
        }
    }
}
