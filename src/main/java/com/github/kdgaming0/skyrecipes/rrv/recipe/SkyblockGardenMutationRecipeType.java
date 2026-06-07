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
 * <p>146×156 custom background. Slot 0 at (4,4) shows the required surface block.
 * Slots 1–36 form a 6×6 grid starting at (24,20) with 18×18 cells.</p>
 */
public class SkyblockGardenMutationRecipeType implements ReliableClientRecipeType {

    public static final SkyblockGardenMutationRecipeType INSTANCE = new SkyblockGardenMutationRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "garden_mutation");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/garden_mutation.png");

    private static final int SLOT_SIZE = 18;
    private static final int GRID_SIZE = 6;

    // Surface block slot
    private static final int SURFACE_SLOT_X = 4;
    private static final int SURFACE_SLOT_Y = 4;

    // 6×6 grid origin
    private static final int GRID_ORIGIN_X = 20;
    private static final int GRID_ORIGIN_Y = 25;

    @Override
    public Component getDisplayName() {
        return Component.literal("Garden Mutation");
    }

    @Override
    public int getDisplayWidth() {
        return 146;
    }

    @Override
    public int getDisplayHeight() {
        return 156;
    }

    @Override
    public Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 37; // 1 surface + 6×6 grid
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        // Slot 0: surface block icon
        slotDefinition.addItemSlot(0, SURFACE_SLOT_X, SURFACE_SLOT_Y);

        // Slots 1–36: 6×6 grid
        int slotId = 1;
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int x = GRID_ORIGIN_X + col * SLOT_SIZE;
                int y = GRID_ORIGIN_Y + row * SLOT_SIZE;
                slotDefinition.addItemSlot(slotId++, x, y);
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
