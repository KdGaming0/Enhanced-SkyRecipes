package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.common.recipe.stackgroup.data.IdentifierStackGroup;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps SkyBlock items out of RRV's identifier/tag/component/regex stack groups.
 *
 * <p>Those groups discriminate only by the vanilla item type, and every SkyBlock item is
 * a vanilla item plus NBT — so without this, RRV's shipped "Enchanted Books" group
 * swallows all ~1900 SkyBlock enchantment books into one stack, its "Swords" tag group
 * absorbs SkyBlock swords, and so on. Stacks carrying a SkyBlock ID are grouped only by
 * {@code SkyblockFamilyStackGroup}, which does not extend this class.</p>
 */
@Mixin(value = IdentifierStackGroup.class, remap = false)
public class IdentifierStackGroupMixin {

    @Inject(method = "match", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$excludeSkyblockItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SkyblockIdExtractor.extract(stack) != null) {
            cir.setReturnValue(false);
        }
    }
}
