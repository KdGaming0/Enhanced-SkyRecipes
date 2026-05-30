package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockReforgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockReforgeRecipeType INSTANCE = new SkyblockReforgeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "reforge");

    @Override
    public Component getDisplayName() {
        return Component.literal("Reforge");
    }

    @Override
    public int getDisplayWidth() {
        return 100;
    }

    @Override
    public int getDisplayHeight() {
        return 60;
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
        slotDefinition.addItemSlot(0, 4, 11);   // Item to reforge
        slotDefinition.addItemSlot(1, 62, 11);  // Reforge stone
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public int getPriority() {
        return 8;
    }
}
