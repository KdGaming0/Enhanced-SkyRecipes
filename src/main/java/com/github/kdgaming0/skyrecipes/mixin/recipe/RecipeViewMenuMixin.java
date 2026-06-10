package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Raises RRV's hard-coded recipe-viewer height limit so SkyRecipes' taller
 * drop recipe cards (168×151) can display without being collapsed to zero recipes.
 */
@Mixin(value = RecipeViewMenu.class, remap = false)
public class RecipeViewMenuMixin {

    @ModifyConstant(method = "calculateRecipesPerPage", constant = @Constant(intValue = 214))
    private int increaseMaxPossibleHeight(int original) {
        return 224;
    }
}
