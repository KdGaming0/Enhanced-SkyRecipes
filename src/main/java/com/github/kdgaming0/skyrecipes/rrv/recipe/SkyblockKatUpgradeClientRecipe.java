package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockKatUpgradeClientRecipe extends AbstractSkyblockClientRecipe {

    private final ItemStack input;
    private final ItemStack output;
    private final int coins;
    private final int timeSeconds;
    private final List<ItemStack> itemCosts;

    public SkyblockKatUpgradeClientRecipe(Identifier id, ItemStack input, ItemStack output,
                                          int coins, int timeSeconds, List<ItemStack> itemCosts) {
        super(id);
        this.input = input;
        this.output = output;
        this.coins = coins;
        this.timeSeconds = timeSeconds;
        this.itemCosts = itemCosts;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockKatUpgradeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        ctx.bindSlot(0, SlotContent.of(input));
        ctx.bindSlot(1, SlotContent.of(output));
        if (!itemCosts.isEmpty()) {
            ctx.bindSlot(2, SlotContent.of(itemCosts.getFirst()));
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        list.add(SlotContent.of(input));
        for (ItemStack stack : itemCosts) {
            list.add(SlotContent.of(stack));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(SlotContent.of(output));
    }

    public int getCoins() {
        return coins;
    }

    public int getTimeSeconds() {
        return timeSeconds;
    }

    public Component getDurationText() {
        int seconds = timeSeconds % 60;
        int minutes = (timeSeconds / 60) % 60;
        int hours = timeSeconds / 3600;
        int days = hours / 24;
        hours = hours % 24;

        StringBuilder sb = new StringBuilder();
        sb.append("Time: ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return Component.literal(sb.toString());
    }

    public Component getCoinText() {
        return Component.literal("Coins: " + String.format("%,d", coins));
    }
}
