package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.render.BlacksmithPreviewRenderer;
import com.github.kdgaming0.skyrecipes.core.render.MobPreviewController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public class SkyblockReforgeClientRecipe extends AbstractSkyblockClientRecipe {

    private final ItemStack stoneStack;
    private final ItemStack sampleItem;
    private final String reforgeName;
    private final String itemTypes;
    private final Map<String, Number> costs;

    private LivingEntity blacksmithEntity;

    public SkyblockReforgeClientRecipe(Identifier id, ItemStack stoneStack, ItemStack sampleItem,
                                       String reforgeName, String itemTypes, Map<String, Number> costs) {
        super(id);
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

    public String getReforgeName() {
        return reforgeName;
    }

    public Component getReforgeText() {
        return Component.literal("Reforge: " + reforgeName);
    }

    public Component getTypeText() {
        return Component.literal("Applies to: " + itemTypes);
    }

    @Override
    public void initRecipe() {
        if (blacksmithEntity == null) {
            blacksmithEntity = MobPreviewController.createVillager();
        }
    }

    @Override
    public void fadeRecipe() {
        if (blacksmithEntity != null) {
            MobPreviewController.disposeEntity(blacksmithEntity);
            blacksmithEntity = null;
        }
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (blacksmithEntity != null) {
            int x = pos.left() + pos.width() / 2;
            int y = pos.top() + pos.height() - 20;
            BlacksmithPreviewRenderer.render(graphics, blacksmithEntity, x, y, 18.0f);
        }
    }
}
