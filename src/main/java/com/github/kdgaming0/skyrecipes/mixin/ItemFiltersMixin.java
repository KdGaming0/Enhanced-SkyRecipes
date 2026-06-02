package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
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
 * Removes vanilla Minecraft base items from the RRV item list while preserving
 * SkyBlock stack-sensitives.
 *
 * <p>SkyBlock items are created as stack-sensitives on top of vanilla base items
 * (e.g. {@code minecraft:player_head}). {@code ItemView.excludeItem()} operates on
 * the base {@code Item}, so excluding a vanilla item also hides all SkyBlock items
 * that use it. This mixin filters the final result list instead, using the presence
 * of a {@code CUSTOM_NAME} component as the discriminator: SkyBlock items all have
 * custom names (from NEU {@code display.Name}), while vanilla base items do not.</p>
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
}
