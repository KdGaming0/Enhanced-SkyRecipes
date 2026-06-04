package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Enhances RRV's item list filtering for SkyBlock items.
 *
 * <p><b>Vanilla item removal:</b> Removes vanilla Minecraft base items from the RRV
 * item list while preserving SkyBlock stack-sensitives. SkyBlock items are created as
 * stack-sensitives on top of vanilla base items (e.g. {@code minecraft:player_head}).
 * {@code ItemView.excludeItem()} operates on the base {@code Item}, so excluding a vanilla
 * item also hides all SkyBlock items that use it. This mixin filters the final result list
 * instead, using the presence of a {@code CUSTOM_NAME} component as the discriminator.</p>
 *
 * <p><b>SkyBlock search integration:</b> Injects into {@code defaultFilter} to use
 * {@link com.github.kdgaming0.skyrecipes.core.search.SkyblockSearchIndex} when data is
 * loaded. This replaces RRV's naive substring search with token-based AND search across
 * display names, internal names, lore, stats, rarity, type, and reforge names.</p>
 */
@Mixin(ItemFilters.class)
public class ItemFiltersMixin {

    @Inject(method = "fullStackList", at = @At("RETURN"))
    private static void removeVanillaBaseItems(CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> results = cir.getReturnValue();
        results.removeIf(stack -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return "minecraft".equals(id.getNamespace()) && !stack.has(DataComponents.CUSTOM_NAME);
        });
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
