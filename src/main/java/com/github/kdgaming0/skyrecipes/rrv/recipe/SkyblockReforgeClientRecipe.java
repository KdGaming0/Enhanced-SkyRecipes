package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public class SkyblockReforgeClientRecipe implements ReliableClientRecipe {

    private final Identifier id;
    private final ItemStack stoneStack;
    private final ItemStack sampleItem;
    private final String reforgeName;
    private final String itemTypes;
    private final Map<String, Number> costs;

    public SkyblockReforgeClientRecipe(Identifier id, ItemStack stoneStack, ItemStack sampleItem,
                                       String reforgeName, String itemTypes, Map<String, Number> costs) {
        this.id = id;
        this.stoneStack = stoneStack;
        this.sampleItem = sampleItem;
        this.reforgeName = reforgeName;
        this.itemTypes = itemTypes;
        this.costs = costs;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockReforgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(sampleItem));
        ctx.bindSlot(1, SlotContent.of(stoneStack));
    }

    @Override
    public List<SlotContent> getIngredients() {
        return List.of(SlotContent.of(stoneStack));
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(sampleItem));
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    public String getReforgeName() {
        return reforgeName;
    }

    public Component getReforgeText() {
        return Component.literal("Reforge: " + reforgeName);
    }

    public Component getTypeText() {
        return Component.literal("Applies to: " + itemTypes);
    }
}
