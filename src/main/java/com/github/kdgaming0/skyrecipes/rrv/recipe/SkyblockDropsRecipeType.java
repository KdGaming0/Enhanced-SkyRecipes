package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SkyBlock drop-recipe type using the custom {@code mob_drops.png} background.
 *
 * <p>168×151 display. 54 slots (6×9) start at (4,62). The 32×32 entity preview
 * area is at (69,18), with built-in space at the top for the centered mob name.</p>
 */
public final class SkyblockDropsRecipeType implements ReliableClientRecipeType {

    public static final SkyblockDropsRecipeType INSTANCE = new SkyblockDropsRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "drops");
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("skyrecipes", "textures/gui/type/mob_drops.png");
    private static final ItemStack ICON = new ItemStack(Items.IRON_SWORD);

    private SkyblockDropsRecipeType() {}

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Mob Drops");
    }

    @Override
    public int getDisplayWidth() {
        return 168;
    }

    @Override
    public int getDisplayHeight() {
        return 151;
    }

    @Override
    public Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 54;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.setHighlightWithoutContents(false);

        for (int row = 0; row < 6; row++) {
            for (int i = 0; i < 9; i++) {
                slotDefinition.addItemSlot(row * 9 + i, i * 18 + 4, 62 + row * 18);
            }
        }
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return ICON;
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
