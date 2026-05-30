package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV client recipe type for SkyBlock forge (Dwarven Forge) recipes.
 */
public class SkyblockForgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockForgeRecipeType INSTANCE = new SkyblockForgeRecipeType();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "forge");

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Forge");
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
        // Reuse crafting background for MVP
        return Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    }

    @Override
    public int getSlotCount() {
        return 10; // 9 inputs + 1 output
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        // 3x3 input grid
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                slotDefinition.addItemSlot(x + y * 3, 4 + x * 18, 4 + y * 18);
            }
        }
        // Output slot
        slotDefinition.addItemSlot(9, 98, 22);
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
        return 2;
    }
}
