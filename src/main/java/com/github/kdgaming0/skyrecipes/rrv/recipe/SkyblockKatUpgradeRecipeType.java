package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock Kat pet rarity upgrades.
 *
 * <p>Layout (152×96 custom asset):</p>
 * <ul>
 *   <li>Slot 0: input pet — 18×18 centred in 28×28 box at (9, 4)</li>
 *   <li>Slots 1-6: material costs + coin — 3×2 grid of 18×18 slots starting at (45, 4)</li>
 *   <li>Slot 7: output pet — 18×18 centred in 28×28 box at (124, 9)</li>
 * </ul>
 */
public class SkyblockKatUpgradeRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockKatUpgradeRecipeType INSTANCE = new SkyblockKatUpgradeRecipeType();

    private static final int INPUT_X = 7;
    private static final int INPUT_Y = 13;
    private static final int MATERIAL_ORIGIN_X = 45;
    private static final int MATERIAL_ORIGIN_Y = 4;
    private static final int MATERIAL_COLS = 3;
    private static final int MATERIAL_SPACING = 18;
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 13;

    private SkyblockKatUpgradeRecipeType() {
        super("kat_upgrade", "Kat Upgrade", 152, 96,
                Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/kat.png"),
                8, new ItemStack(Items.EGG), 5);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, INPUT_X, INPUT_Y);

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < MATERIAL_COLS; col++) {
                int slotId = 1 + row * MATERIAL_COLS + col;
                int x = MATERIAL_ORIGIN_X + col * MATERIAL_SPACING;
                int y = MATERIAL_ORIGIN_Y + row * MATERIAL_SPACING;
                slotDefinition.addItemSlot(slotId, x, y);
            }
        }

        slotDefinition.addItemSlot(7, OUTPUT_X, OUTPUT_Y);
    }
}
