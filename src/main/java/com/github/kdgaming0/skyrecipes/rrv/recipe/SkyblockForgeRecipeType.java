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
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/forge.png");

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Forge");
    }

    @Override
    public int getDisplayWidth() {
        return 129;
    }

    @Override
    public int getDisplayHeight() {
        return 58;
    }

    @Override
    public Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 9; // 8 inputs + 1 output
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        // Row 1: 4 input slots
        for (int i = 0; i < 4; i++) {
            slotDefinition.addItemSlot(i, 4 + i * 18, 4);
        }
        // Row 2: 4 input slots
        for (int i = 0; i < 4; i++) {
            slotDefinition.addItemSlot(4 + i, 4 + i * 18, 22);
        }
        // Output
        slotDefinition.addItemSlot(8, 105, 13);
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
