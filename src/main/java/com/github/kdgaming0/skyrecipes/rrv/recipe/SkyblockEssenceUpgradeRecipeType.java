package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockEssenceUpgradeRecipeType implements ReliableClientRecipeType {

    public static final SkyblockEssenceUpgradeRecipeType INSTANCE = new SkyblockEssenceUpgradeRecipeType();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "essence_upgrade");

    @Override
    public Component getDisplayName() {
        return Component.literal("Essence Upgrade");
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
        return Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    }

    @Override
    public int getSlotCount() {
        return 4; // Item, essence, extra1, extra2
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 4, 22);   // Base item
        slotDefinition.addItemSlot(1, 30, 22);  // Essence
        slotDefinition.addItemSlot(2, 56, 22);  // Extra item 1
        slotDefinition.addItemSlot(3, 98, 22);  // Result (same item, upgraded)
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
