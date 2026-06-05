package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.render.MobPreviewController;
import com.github.kdgaming0.skyrecipes.core.render.MobPreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockDropsClientRecipe extends AbstractSkyblockClientRecipe {

    private final List<DropEntry> drops;
    private final String mobId;
    private LivingEntity mobEntity;

    public SkyblockDropsClientRecipe(Identifier id, List<DropEntry> drops) {
        super(id);
        this.drops = drops;
        this.mobId = drops.isEmpty() ? "" : drops.get(0).internalName();
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

    public List<DropEntry> getDrops() {
        return drops;
    }

    @Override
    public void initRecipe() {
        if (mobEntity == null) {
            mobEntity = MobPreviewController.createEntity(mobId);
        }
    }

    @Override
    public void fadeRecipe() {
        if (mobEntity != null) {
            MobPreviewController.disposeEntity(mobEntity);
            mobEntity = null;
        }
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (mobEntity != null) {
            int x = pos.left() + pos.width() - 30;
            int y = pos.top() + pos.height() / 2;
            MobPreviewRenderer.render(graphics, mobEntity, x, y, 28.0f, MobPreviewRenderer.getRotationAngle());
        }
    }

    public record DropEntry(ItemStack stack, String internalName, String chance) {
    }
}
