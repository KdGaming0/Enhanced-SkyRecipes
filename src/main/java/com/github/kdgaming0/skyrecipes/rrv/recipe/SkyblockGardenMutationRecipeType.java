package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock Garden mutations.
 *
 * <p>146×156 custom background. Slot 0 at (4,4) shows the required surface block.
 * Slots 1–36 form a 6×6 grid starting at (24,20) with 18×18 cells.</p>
 */
public class SkyblockGardenMutationRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockGardenMutationRecipeType INSTANCE = new SkyblockGardenMutationRecipeType();

    private static final int SLOT_SIZE = 18;
    private static final int GRID_SIZE = 6;
    private static final int SURFACE_SLOT_X = 4;
    private static final int SURFACE_SLOT_Y = 4;
    private static final int GRID_ORIGIN_X = 20;
    private static final int GRID_ORIGIN_Y = 25;

    private SkyblockGardenMutationRecipeType() {
        super("garden_mutation", "Garden Mutation", 146, 156,
                Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/garden_mutation.png"),
                37, new ItemStack(Items.WHEAT_SEEDS), 5);
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
}
