package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SkyblockTradeClientRecipe implements ReliableClientRecipe {

    private final Identifier id;
    private final ItemStack input;
    private final ItemStack output;

    public SkyblockTradeClientRecipe(Identifier id, ItemStack input, ItemStack output) {
        this.id = id;
        this.input = input;
        this.output = output;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockTradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(input));
        ctx.bindSlot(1, SlotContent.of(output));
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of(SlotContent.of(input));
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(output));
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }
}
