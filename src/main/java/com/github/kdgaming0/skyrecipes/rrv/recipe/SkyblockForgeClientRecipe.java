package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * RRV client recipe for SkyBlock forge (timed crafting) recipes.
 */
public class SkyblockForgeClientRecipe implements ReliableClientRecipe {

    private final Identifier id;
    private final List<ForgeIngredient> inputs;
    private final ItemStack output;
    private final int durationSeconds;

    public SkyblockForgeClientRecipe(Identifier id, List<ForgeIngredient> inputs,
                                     ItemStack output, int durationSeconds) {
        this.id = id;
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockForgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext slotFillContext) {
        for (int i = 0; i < inputs.size() && i < 9; i++) {
            slotFillContext.bindSlot(i, SlotContent.of(inputs.get(i).stack()));
        }
        slotFillContext.bindSlot(9, SlotContent.of(output));
    }

    @Override
    public List<SlotContent> getIngredients() {
        List<SlotContent> list = new ArrayList<>();
        for (ForgeIngredient input : inputs) {
            list.add(SlotContent.of(input.stack()));
        }
        return list;
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

    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Formats the forge duration as a human-readable string.
     */
    public Component getDurationText() {
        int seconds = durationSeconds % 60;
        int minutes = (durationSeconds / 60) % 60;
        int hours = durationSeconds / 3600;

        StringBuilder sb = new StringBuilder();
        sb.append("Duration: ");
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return Component.literal(sb.toString());
    }

    /**
     * A single forge input ingredient.
     */
    public record ForgeIngredient(ItemStack stack, String internalName, int count) {}
}
