package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;

public class SkyblockTradeClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int RANGE_TEXT_Y = 26;
    private static final int WIKI_BUTTON_X_OFFSET = 64;
    private static final int WIKI_BUTTON_Y_OFFSET = 26;

    private final ItemStack input;
    private final ItemStack output;
    private final int min;
    private final int max;

    public SkyblockTradeClientRecipe(Identifier id, ItemStack input, ItemStack output,
                                     int min, int max, List<String> wikiUrls) {
        super(id, wikiUrls);
        this.input = input;
        this.output = output;
        this.min = min;
        this.max = max;
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
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (min > 0 && max > min) {
            var font = Minecraft.getInstance().font;
            String text = min + "-" + max;
            int textWidth = font.width(text);
            // Center under input slot (slot 0 is at 4,4; centre is 13)
            int x = 13 - textWidth / 2;
            graphics.text(font, Component.literal(text), x, RANGE_TEXT_Y, 0xFFFFFFFF, true);
        }
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        if (wikiUrls.isEmpty()) {
            return null;
        }
        String url = wikiUrls.stream()
                .filter(u -> u != null && !u.isEmpty())
                .findFirst()
                .orElse(null);
        if (url == null) {
            return null;
        }
        int btnX = pos.left() + WIKI_BUTTON_X_OFFSET;
        int btnY = pos.top() + WIKI_BUTTON_Y_OFFSET;
        Button wikiButton = Button.builder(Component.literal("W"), b -> {
                    try {
                        Util.getPlatform().openUri(URI.create(url));
                    } catch (Exception e) {
                        // ignore
                    }
                }).pos(btnX, btnY).size(12, 12)
                .tooltip(Tooltip.create(Component.literal("Open Wiki")))
                .build();
        screen.addRecipeWidget(wikiButton);
        return wikiButton;
    }
}
