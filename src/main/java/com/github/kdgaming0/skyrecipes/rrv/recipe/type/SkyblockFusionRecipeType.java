package com.github.kdgaming0.skyrecipes.rrv.recipe.type;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockRecipeType;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV recipe type for attribute shard fusions.
 *
 * <p>152×80 custom background. Only the output (slot 0, at 112,20) is a bound RRV
 * slot; the two fusion inputs are custom-rendered by
 * {@code SkyblockFusionClientRecipe} so every displayed pair is a valid
 * combination (two independently cycling slots would show pairs that don't exist).</p>
 */
public class SkyblockFusionRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockFusionRecipeType INSTANCE = new SkyblockFusionRecipeType();

    public static final int OUTPUT_SLOT_X = 112;
    public static final int OUTPUT_SLOT_Y = 20;

    private SkyblockFusionRecipeType() {
        super("fusion", "Shard Fusion", 152, 80,
                IdentifierUtil.skyRecipes("textures/gui/type/fusion.png"),
                1, new ItemStack(Items.AMETHYST_SHARD), 6);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y);
    }
}
