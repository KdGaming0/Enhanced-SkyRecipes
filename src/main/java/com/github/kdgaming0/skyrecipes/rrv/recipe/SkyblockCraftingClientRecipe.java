package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * SkyBlock crafting recipe that reuses RRV's {@link CraftingClientRecipeType}
 * for the 3×3 grid layout, but adds craft-text requirement rendering.
 */
public class SkyblockCraftingClientRecipe extends AbstractSkyblockClientRecipe {

    private final Map<Integer, SlotContent> ingredients;
    private final SlotContent result;
    private final String craftText;

    public SkyblockCraftingClientRecipe(Identifier id, Map<Integer, SlotContent> ingredients,
                                        SlotContent result, String craftText) {
        super(id);
        this.ingredients = Map.copyOf(ingredients);
        this.result = result;
        this.craftText = craftText;
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockCraftingRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (Map.Entry<Integer, SlotContent> e : ingredients.entrySet()) {
            ctx.bindSlot(e.getKey(), e.getValue());
        }
        ctx.bindSlot(9, result);
    }

    @Override
    public List<SlotContent> getIngredients() {
        return ingredients.values().stream().toList();
    }

    @Override
    public List<SlotContent> getResults() {
        return List.of(result);
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (craftText == null || craftText.isEmpty()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        String text = "§cReq: §e" + craftText;
        int textWidth = font.width(text);
        int x = pos.left() + (pos.width() - textWidth) / 2;
        int y = pos.top() + pos.height() - 10;
        graphics.text(font, text, x, y, 0xFFFFFFFF, true);
    }
}
