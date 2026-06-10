package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;

import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockForgeRecipeType;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import cc.cassian.rrv.common.recipe.rendering.AnimationTicker;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SkyblockForgeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final Identifier FLAME_TICKER_ID = Identifier.fromNamespaceAndPath("skyrecipes", "forge_flame");
    private static final Identifier RRV_WIDGETS = Identifier.fromNamespaceAndPath("rrv", "textures/gui/rrv_widgets.png");
    private static final int FLAME_X = 81;
    private static final int FLAME_Y = 14;
    private static final int FLAME_SIZE = 14;
    private static final int FLAME_HIT_W = 14;
    private static final int FLAME_HIT_H = 14;

    private final List<ForgeIngredient> inputs;
    private final ItemStack output;
    private final int durationSeconds;
    private final AnimationTicker flameTicker;

    public SkyblockForgeClientRecipe(Identifier id, List<ForgeIngredient> inputs,
                                     ItemStack output, int durationSeconds,
                                     List<String> wikiUrls, String craftText) {
        super(id, wikiUrls);
        this.inputs = inputs;
        this.output = output;
        this.durationSeconds = durationSeconds;
        setCraftText(craftText);
        this.flameTicker = AnimationTicker.create(FLAME_TICKER_ID, 80);
    }

    @Override
    public ReliableClientRecipeType getType() {
        return SkyblockForgeRecipeType.INSTANCE;
    }

    @Override
    public void bindSlots(RecipeViewMenu.SlotFillContext ctx) {
        for (int i = 0; i < inputs.size() && i < 8; i++) {
            ctx.bindSlot(i, SlotContent.of(inputs.get(i).stack()));
        }
        ctx.bindSlot(8, SlotContent.of(output));
        if (hasCraftText()) {
            ctx.addAdditionalStackModifier(8, this::appendRequirementTooltip);
        }
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
    public List<AnimationTicker> getAnimationTickers() {
        return List.of(flameTicker);
    }

    @Override
    public void renderRecipe(RecipeViewScreen screen, RecipePosition pos,
                             GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        int flameProgress = Math.round(flameTicker.getProgress() * FLAME_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, RRV_WIDGETS,
                FLAME_X, FLAME_Y + (FLAME_SIZE - flameProgress),
                0, FLAME_SIZE - flameProgress,
                FLAME_SIZE, flameProgress, 128, 128);

        var font = Minecraft.getInstance().font;
        Component text = RecipeUiHelper.formatDuration(durationSeconds, false, "Duration: ");
        int textWidth = font.width(text);
        graphics.text(font, text, (pos.width() - textWidth) / 2, 42, RecipeUiHelper.TEXT_WHITE, true);

        if (hasCraftText()) {
            graphics.text(font, "§c!", FLAME_X + 15, FLAME_Y - 3, RecipeUiHelper.TEXT_WHITE, false);
            if (mouseX >= FLAME_X && mouseX < FLAME_X + FLAME_HIT_W
                    && mouseY >= FLAME_Y && mouseY < FLAME_Y + FLAME_HIT_H) {
                graphics.setComponentTooltipForNextFrame(font,
                        List.of(requirementTooltipLine()),
                        pos.left() + mouseX,
                        pos.top() + mouseY);
            }
        }

        maintainButtons(screen, pos);
    }

    public record ForgeIngredient(ItemStack stack, String internalName, int count) {
    }
}
