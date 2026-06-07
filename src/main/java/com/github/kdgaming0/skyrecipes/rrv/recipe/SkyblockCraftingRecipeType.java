package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.ReliableRecipeViewer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock crafting recipes.
 *
 * <p>Uses the same 3×3 + output slot layout as RRV's {@code CraftingClientRecipeType},
 * but with a {@code skyrecipes:crafting} ID so it is not caught by the vanilla-category
 * disable mixin.</p>
 */
public class SkyblockCraftingRecipeType implements ReliableClientRecipeType {

    public static final SkyblockCraftingRecipeType INSTANCE = new SkyblockCraftingRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "crafting");
    private static final Identifier BACKGROUND = ReliableRecipeViewer.of("textures/gui/type/crafting_bordered.png");

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Crafting");
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
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 10;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                slotDefinition.addItemSlot(x + y * 3, 4 + x * 18, 4 + y * 18);
            }
        }
        slotDefinition.addItemSlot(9, 98, 22);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
