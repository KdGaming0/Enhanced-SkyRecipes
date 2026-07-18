package com.github.kdgaming0.skyrecipes.rrv.recipe.type;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockRecipeType;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SkyBlock drop-recipe type using the custom {@code mob_drops.png} background.
 *
 * <p>168×151 display. 54 slots (6×9) start at (4,62). The 32×32 entity preview
 * area is at (69,18), with built-in space at the top for the centered mob name.</p>
 */
public final class SkyblockDropsRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockDropsRecipeType INSTANCE = new SkyblockDropsRecipeType();

    private SkyblockDropsRecipeType() {
        super("drops", "SkyBlock Mob Drops", 168, 151,
                IdentifierUtil.skyRecipes("textures/gui/type/mob_drops.png"),
                54, new ItemStack(Items.ZOMBIE_HEAD), 2);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        slotDefinition.setHighlightWithoutContents(false);

        placeGrid(slotDefinition, 0, 6, 9, 4, 62, 18);
    }
}
