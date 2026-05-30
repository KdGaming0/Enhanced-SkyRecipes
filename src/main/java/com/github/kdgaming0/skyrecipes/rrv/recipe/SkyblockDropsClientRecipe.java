package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockDropsClientRecipe implements ReliableClientRecipe {

    private final Identifier id;
    private final List<DropEntry> drops;

    public SkyblockDropsClientRecipe(Identifier id, List<DropEntry> drops) {
        this.id = id;
        this.drops = drops;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockDropsRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < drops.size() && i < 9; i++) {
            ctx.bindSlot(i, SlotContent.of(drops.get(i).stack()));
        }
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (DropEntry drop : drops) {
            list.add(SlotContent.of(drop.stack()));
        }
        return list;
    }

    @Override
    public List<SlotContent> getResults() {
        List<SlotContent> list = new ArrayList<>();
        for (DropEntry drop : drops) {
            list.add(SlotContent.of(drop.stack()));
        }
        return list;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    public List<DropEntry> getDrops() {
        return drops;
    }

    public record DropEntry(ItemStack stack, String internalName, String chance) {}
}
