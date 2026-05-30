package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockTradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockTradeRecipeType INSTANCE = new SkyblockTradeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "trade");

    @Override
    public Component getDisplayName() {
        return Component.literal("Trade");
    }

    @Override
    public int getDisplayWidth() {
        return 100;
    }

    @Override
    public int getDisplayHeight() {
        return 40;
    }

    @Override
    public Identifier getGuiTexture() {
        return Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    }

    @Override
    public int getSlotCount() {
        return 2;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 4, 11);   // Input
        slotDefinition.addItemSlot(1, 76, 11);  // Output
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.GOLD_INGOT);
    }

    @Override
    public int getPriority() {
        return 6;
    }
}
