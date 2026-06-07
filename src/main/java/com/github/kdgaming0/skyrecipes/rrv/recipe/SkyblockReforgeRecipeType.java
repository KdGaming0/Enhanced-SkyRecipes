package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV client recipe type for SkyBlock reforges.
 *
 * <p>Tall card (120×146) with a custom background texture. Uses one slot at
 * (10,10) — centred inside the 28×28 item box at (5,5). For blacksmith reforges
 * the slot is left empty and a villager entity is rendered in the 24×36 NPC area
 * at (5,5); for stone reforges the stone item occupies the slot.</p>
 */
public class SkyblockReforgeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockReforgeRecipeType INSTANCE = new SkyblockReforgeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "reforge");

    // 18×18 slot centred in the 28×28 box at (5,5)
    private static final int SLOT_X = 9;
    private static final int SLOT_Y = 9;

    @Override
    public Component getDisplayName() {
        return Component.literal("Reforge");
    }

    @Override
    public int getDisplayWidth() {
        return 120;
    }

    @Override
    public int getDisplayHeight() {
        return 146;
    }

    @Override
    public Identifier getGuiTexture() {
        return null; // rendered manually per-recipe in SkyblockReforgeClientRecipe
    }

    @Override
    public int getSlotCount() {
        return 1;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, SLOT_X, SLOT_Y);
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
