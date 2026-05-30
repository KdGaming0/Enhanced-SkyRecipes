package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockKatUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockKatUpgradeRecipeType INSTANCE = new SkyblockKatUpgradeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "kat_upgrade");

    @Override
    public Component getDisplayName() {
        return Component.literal("Kat Upgrade");
    }

    @Override
    public int getDisplayWidth() {
        return 122;
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
        return 3;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 4, 22);   // Input pet
        slotDefinition.addItemSlot(1, 62, 22);  // Output pet
        slotDefinition.addItemSlot(2, 98, 22);  // Extra cost
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.EGG);
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
