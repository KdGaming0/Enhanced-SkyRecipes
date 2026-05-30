package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockNpcShopRecipeType implements ReliableClientRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "npc_shop");

    @Override
    public Component getDisplayName() {
        return Component.literal("NPC Shop");
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
        return 4; // Up to 3 costs + 1 result
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 4, 22);   // Cost 1
        slotDefinition.addItemSlot(1, 26, 22);  // Cost 2
        slotDefinition.addItemSlot(2, 48, 22);  // Cost 3
        slotDefinition.addItemSlot(3, 98, 22);  // Result
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.EMERALD);
    }

    @Override
    public int getPriority() {
        return 4;
    }
}
