package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Raises RRV's hard-coded recipe-viewer height limit so SkyRecipes' taller
 * drop recipe cards (168×151) can display without being collapsed to zero recipes.
 *
 * <p>Also makes the recipe view open on the tab that actually produces the clicked
 * SkyBlock item. Family expansion merges recipes of several categories into one view
 * (gemstones: Rough→Flawless are crafting, Perfect is forge), and RRV always starts
 * on the first tab by category priority — so clicking Perfect Gemstone landed on the
 * crafting tab. The hook runs after the menu is fully built and switches to the first
 * tab whose recipes have the clicked item as a result; explicit tab clicks
 * (a non-{@code NONE} {@code clientRecipeType}) and U-lookups are left alone.</p>
 */
@Mixin(value = RecipeViewMenu.class, remap = false)
public abstract class RecipeViewMenuMixin {

    @Shadow @Final private LinkedHashMap<ReliableClientRecipeType, List<ReliableClientRecipe>> sortedByType;
    @Shadow @Final private List<ReliableClientRecipeType> viewTypeOrder;

    @Shadow public abstract void setClientRecipeType(int typeId);

    @ModifyConstant(method = "calculateRecipesPerPage", constant = @Constant(intValue = 214))
    private int increaseMaxPossibleHeight(int original) {
        return 224;
    }

    @Inject(
            method = "<init>(Lnet/minecraft/client/gui/screens/Screen;ILnet/minecraft/world/entity/player/Inventory;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lcc/cassian/rrv/api/ActionType;Ljava/util/ArrayList;Lcc/cassian/rrv/api/recipe/ReliableClientRecipeType;)V",
            at = @At("RETURN")
    )
    private void skyrecipes$openOnOriginTab(Screen parentScreen, int containerId, Inventory inventory,
                                            List<? extends ReliableClientRecipe> recipes, ItemStack origin,
                                            ActionType originType, ArrayList<RecipeViewScreen> viewHistory,
                                            ReliableClientRecipeType clientRecipeType, CallbackInfo ci) {
        if (originType != ActionType.RESULT || !ReliableClientRecipeType.NONE.equals(clientRecipeType)) {
            return;
        }
        String originId = SkyblockIdExtractor.extract(origin);
        if (originId == null) {
            return;
        }
        for (int i = 0; i < viewTypeOrder.size(); i++) {
            List<ReliableClientRecipe> typeRecipes = sortedByType.get(viewTypeOrder.get(i));
            if (typeRecipes != null && anyRecipeProduces(typeRecipes, originId)) {
                if (i != 0) {
                    setClientRecipeType(i);
                }
                return;
            }
        }
    }

    private static boolean anyRecipeProduces(List<ReliableClientRecipe> recipes, String originId) {
        for (ReliableClientRecipe recipe : recipes) {
            for (SlotContent result : recipe.getResults()) {
                for (ItemStack stack : result.getValidContents()) {
                    if (originId.equals(SkyblockIdExtractor.extract(stack))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
