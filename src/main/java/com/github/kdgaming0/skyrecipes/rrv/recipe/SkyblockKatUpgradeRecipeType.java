package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
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
 *
 * <p><b>Edit slot positions below:</b></p>
 */
public class SkyblockKatUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockKatUpgradeRecipeType INSTANCE = new SkyblockKatUpgradeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "kat_upgrade");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/kat.png");

    /* ═══════════════════════════════════════════════════════════════
     *  EDIT SLOT POSITIONS HERE
     *  All coordinates are relative to the 152×96 background.
     * ═══════════════════════════════════════════════════════════════ */

    /** Slot 0 — input pet (18×18 centred in the 28×28 box at (9, 4)). */
    private static final int INPUT_X = 7;
    private static final int INPUT_Y = 13;

    /** Slots 1-6 — material costs + coin (3 cols × 2 rows, 18×18 each). */
    private static final int MATERIAL_ORIGIN_X = 45;
    private static final int MATERIAL_ORIGIN_Y = 4;
    private static final int MATERIAL_COLS = 3;
    private static final int MATERIAL_SPACING = 18;

    /** Slot 7 — output pet (18×18 centred in the 28×28 box at (124, 9)). */
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 13;

    @Override
    public Component getDisplayName() {
        return Component.literal("Kat Upgrade");
    }

    @Override
    public int getDisplayWidth() {
        return 152;
    }

    @Override
    public int getDisplayHeight() {
        return 96;
    }

    @Override
    public Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 8; // 1 input + 6 materials/coin + 1 output
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
        return new ItemStack(Items.EGG);
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
