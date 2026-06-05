package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockNpcShopClientRecipe extends AbstractSkyblockClientRecipe {

    private final String npcName;
    private final List<ShopCost> costs;
    private final ItemStack result;

    public SkyblockNpcShopClientRecipe(Identifier id, String npcName, List<ShopCost> costs, ItemStack result) {
        super(id);
        this.npcName = npcName;
        this.costs = costs;
        this.result = result;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockNpcShopRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < costs.size() && i < 3; i++) {
            ctx.bindSlot(i, SlotContent.of(costs.get(i).stack()));
        }
        ctx.bindSlot(3, SlotContent.of(result));
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (ShopCost cost : costs) {
            list.add(SlotContent.of(cost.stack()));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(result));
    }

    public String getNpcName() {
        return npcName;
    }

    public Component getNpcText() {
        return Component.literal("NPC: " + (npcName.isEmpty() ? "Unknown" : npcName));
    }

    public record ShopCost(ItemStack stack, String internalName, int count, boolean isCoins) {
    }
}
