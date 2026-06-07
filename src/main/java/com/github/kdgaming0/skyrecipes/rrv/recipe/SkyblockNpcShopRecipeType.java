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

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/npc_shop.png");

    @Override
    public Component getDisplayName() {
        return Component.literal("NPC Shop");
    }

    @Override
    public int getDisplayWidth() {
        return 156;
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
        return 9; // 8 costs + 1 result
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        // 2 rows × 4 columns of cost slots
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                slotDefinition.addItemSlot(row * 4 + col, 29 + col * 18, 14 + row * 18);
            }
        }
        // Result slot — 18×18 centered in 28×28 box at (128, 21)
        slotDefinition.addItemSlot(8, 133, 24);
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
