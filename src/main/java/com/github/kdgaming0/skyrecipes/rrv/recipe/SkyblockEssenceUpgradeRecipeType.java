package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock essence upgrades.
 *
 * <p>Layout (152×64 custom asset):</p>
 * <ul>
 *   <li>Slot 0 — input item, 18×18 centred in 28×28 box at (23, 4)</li>
 *   <li>Slots 1-6 — essence + extra items, 2×3 grid of 18×18 slots</li>
 *   <li>Slot 7 — output item, 18×18 centred in 28×28 box at (101, 4)</li>
 * </ul>
 */
public class SkyblockEssenceUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockEssenceUpgradeRecipeType INSTANCE = new SkyblockEssenceUpgradeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "essence_upgrade");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/essence_upgrade.png");

    /**
     * Slot 0 — input pet (18×18 centred in the 28×28 box at (9, 4)).
     */
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 27;

    /**
     * Slots 1-6 — material costs + coin (3 cols × 2 rows, 18×18 each).
     */
    private static final int MATERIAL_ORIGIN_X = 45;
    private static final int MATERIAL_ORIGIN_Y = 18;
    private static final int MATERIAL_COLS = 3;
    private static final int MATERIAL_SPACING = 18;

    /**
     * Slot 7 — output pet (18×18 centred in the 28×28 box at (124, 9)).
     */
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 27;

    @Override
    public Component getDisplayName() {
        return Component.literal("Essence Upgrade");
    }

    @Override
    public int getDisplayWidth() {
        return 152;
    }

    @Override
    public int getDisplayHeight() {
        return 64;
    }

    @Override
    public Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 8;
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

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public int getPriority() {
        return 7;
    }
}
