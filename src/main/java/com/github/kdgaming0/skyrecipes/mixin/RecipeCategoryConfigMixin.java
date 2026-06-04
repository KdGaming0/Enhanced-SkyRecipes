package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.config.instances.RecipeCategoryConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-disables vanilla Minecraft recipe categories in RRV's config.
 *
 * <p>Vanilla categories (smelting, brewing, smithing, etc.) are forced to {@code false}
 * regardless of what is stored in {@code recipe_categories.json}. This ensures that
 * even if a user previously enabled vanilla categories, they remain disabled while
 * SkyRecipes is active.</p>
 */
@Mixin(RecipeCategoryConfig.class)
public class RecipeCategoryConfigMixin {

    @Inject(method = "enabled", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$disableVanillaCategories(ReliableClientRecipeType reliableClientRecipeType,
            CallbackInfoReturnable<Boolean> cir) {
        if (reliableClientRecipeType != null
                && "minecraft".equals(reliableClientRecipeType.getId().getNamespace())) {
            cir.setReturnValue(false);
        }
    }
}
