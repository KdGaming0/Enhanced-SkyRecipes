package com.github.kdgaming0.skyrecipes.rrv.recipe.type;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockRecipeType;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkyblockNpcShopRecipeType extends AbstractSkyblockRecipeType {

    public static final SkyblockNpcShopRecipeType INSTANCE = new SkyblockNpcShopRecipeType();

    private SkyblockNpcShopRecipeType() {
        super("npc_shop", "NPC Shop", 156, 64,
                IdentifierUtil.skyRecipes("textures/gui/type/npc_shop.png"),
                9, new ItemStack(Items.EMERALD), 3);
    }

    @Override
    public void placeSlots(RecipeViewMenu.SlotDefinition slotDefinition) {
        // 2 rows × 4 columns of cost slots
        placeGrid(slotDefinition, 0, 2, 4, 29, 14, 18);
        // Result slot — 18×18 centered in 28×28 box at (128, 21)
        slotDefinition.addItemSlot(8, 133, 24);
    }
}
