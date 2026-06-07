package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.ReliableRecipeViewer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * RRV recipe type for SkyBlock info cards.
 *
 * <p>Uses the same 120×120 layout as RRV's built-in {@code InfoClientRecipeType}
 * so the info background texture and single top-centred slot line up.</p>
 */
public class SkyblockInfoRecipeType implements ReliableClientRecipeType {

    public static final SkyblockInfoRecipeType INSTANCE = new SkyblockInfoRecipeType();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("skyrecipes", "info");
    private static final Identifier BACKGROUND = ReliableRecipeViewer.of("textures/gui/type/info.png");

    @Override
    public Component getDisplayName() {
        return Component.literal("SkyBlock Info");
    }

    @Override
    public int getDisplayWidth() {
        return 120;
    }

    @Override
    public int getDisplayHeight() {
        return 120;
    }

    @Override
    public @Nullable Identifier getGuiTexture() {
        return BACKGROUND;
    }

    @Override
    public int getSlotCount() {
        return 1;
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 53, 3);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.BOOK.getDefaultInstance();
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
