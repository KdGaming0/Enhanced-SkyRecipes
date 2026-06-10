package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.common.ReliableRecipeViewer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockTradeRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockTradeRecipeType INSTANCE = new SkyblockTradeRecipeType();

    private SkyblockTradeRecipeType() {
        super("trade", "Trade", 80, 24,
                ReliableRecipeViewer.of("textures/gui/type/stonecutter.png"),
                2, new ItemStack(Items.GOLD_INGOT), 8);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 4, 4);   // Input
        slotDefinition.addItemSlot(1, 60, 4);  // Output
    }
}
