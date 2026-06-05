package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockEssenceUpgradeClientRecipe extends AbstractSkyblockClientRecipe {

    private final ItemStack baseItem;
    private final ItemStack essenceStack;
    private final int starLevel;
    private final String essenceType;
    private final List<ItemStack> extraItems;

    public SkyblockEssenceUpgradeClientRecipe(Identifier id, ItemStack baseItem, ItemStack essenceStack,
                                              int starLevel, String essenceType, List<ItemStack> extraItems) {
        super(id);
        this.baseItem = baseItem;
        this.essenceStack = essenceStack;
        this.starLevel = starLevel;
        this.essenceType = essenceType;
        this.extraItems = extraItems;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockEssenceUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(baseItem));
        ctx.bindSlot(1, SlotContent.of(essenceStack));
        for (int i = 0; i < extraItems.size() && i < 2; i++) {
            ctx.bindSlot(2 + i, SlotContent.of(extraItems.get(i)));
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        list.add(SlotContent.of(baseItem));
        list.add(SlotContent.of(essenceStack));
        for (ItemStack stack : extraItems) {
            list.add(SlotContent.of(stack));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(baseItem));
    }

    public int getStarLevel() {
        return starLevel;
    }

    public Component getStarText() {
        return Component.literal("Star: " + starLevel);
    }

    public Component getEssenceText() {
        return Component.literal(essenceType + " Essence x" + essenceStack.getCount());
    }
}
