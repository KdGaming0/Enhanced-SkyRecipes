package com.github.kdgaming0.skyrecipes.rrv.recipe.type;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockRecipeType;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RRV client recipe type for SkyBlock forge (Dwarven Forge) recipes.
 */
public class SkyblockForgeRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockForgeRecipeType INSTANCE = new SkyblockForgeRecipeType();

    private SkyblockForgeRecipeType() {
        super("forge", "SkyBlock Forge", 129, 58,
                IdentifierUtil.skyRecipes("textures/gui/type/forge.png"),
                9, new ItemStack(Items.BLAST_FURNACE), 1);
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
}
