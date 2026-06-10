package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.common.ReliableRecipeViewer;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for SkyBlock info cards.
 *
 * <p>Uses the same 120×120 layout as RRV's built-in {@code InfoClientRecipeType}
 * so the info background texture and single top-centred slot line up.</p>
 */
public class SkyblockInfoRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockInfoRecipeType INSTANCE = new SkyblockInfoRecipeType();

    private SkyblockInfoRecipeType() {
        super("info", "SkyBlock Info", 120, 120,
                ReliableRecipeViewer.of("textures/gui/type/info.png"),
                1, Items.BOOK.getDefaultInstance(), 10);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 53, 3);
    }
}
