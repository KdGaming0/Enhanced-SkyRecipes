package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.common.recipe.stackgroup.data.RegexStackGroup;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps SkyBlock items out of RRV's regex stack groups — same rationale as
 * {@link IdentifierStackGroupMixin}: the regex tests the vanilla registry ID, which
 * thousands of SkyBlock items share.
 */
@Mixin(value = RegexStackGroup.class, remap = false)
public class RegexStackGroupMixin {

    @Inject(method = "match", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$excludeSkyblockItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SkyblockIdExtractor.extract(stack) != null) {
            cir.setReturnValue(false);
        }
    }
}
