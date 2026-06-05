package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock Garden mutations.
 *
 * <p>Uses a fixed 6×6 grid (36 slots) so all mutation layouts fit.
 * Smaller layouts are centered inside the grid.</p>
 */
public class SkyblockGardenMutationRecipeType implements ReliableClientRecipeType {

    public static final SkyblockGardenMutationRecipeType INSTANCE = new SkyblockGardenMutationRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "garden_mutation");

    private static final int SLOT_SIZE = 18;
    private static final int GRID_SIZE = 6;
    private static final int PADDING = 6;

    @Override
    public Component getDisplayName() {
        return Component.literal("Garden Mutation");
    }

    @Override
    public int getDisplayWidth() {
        return PADDING * 2 + GRID_SIZE * SLOT_SIZE;
    }

    @Override
    public int getDisplayHeight() {
        return 140;
    }

    @Override
    public Identifier getGuiTexture() {
        return Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    }

    @Override
    public int getSlotCount() {
        return GRID_SIZE * GRID_SIZE;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        int id = 0;
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int x = PADDING + col * SLOT_SIZE;
                int y = PADDING + row * SLOT_SIZE;
                slotDefinition.addItemSlot(id++, x, y);
            }
        }
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.WHEAT_SEEDS);
    }

    @Override
    public int getPriority() {
        return 7;
    }
}
