package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.common.ReliableRecipeViewer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock crafting recipes.
 *
 * <p>Uses the same 3×3 + output slot layout as RRV's {@code CraftingClientRecipeType},
 * but with a {@code skyrecipes:crafting} ID so it is not caught by the vanilla-category
 * disable mixin.</p>
 */
public class SkyblockCraftingRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockCraftingRecipeType INSTANCE = new SkyblockCraftingRecipeType();

    private SkyblockCraftingRecipeType() {
        super("crafting", "SkyBlock Crafting", 122, 60,
                ReliableRecipeViewer.of("textures/gui/type/crafting_bordered.png"),
                10, new ItemStack(Items.CRAFTING_TABLE), 1);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                slotDefinition.addItemSlot(x + y * 3, 4 + x * 18, 4 + y * 18);
            }
        }
        slotDefinition.addItemSlot(9, 98, 22);
    }
}
