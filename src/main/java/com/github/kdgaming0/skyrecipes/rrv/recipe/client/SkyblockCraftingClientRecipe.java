package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;

import com.github.kdgaming0.skyrecipes.rrv.recipe.type.SkyblockCraftingRecipeType;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * SkyBlock crafting recipe that reuses RRV's crafting grid layout,
 * with requirement indicators, wiki button, and craft button.
 */
public class SkyblockCraftingClientRecipe extends AbstractSkyblockClientRecipe {

    private static final int ARROW_X = 62;
    private static final int ARROW_Y = 22;
    /**
     * Hit-box for the requirement tooltip. Made wide so it catches the moved "!" easily.
     */
    private static final int ARROW_HIT_W = 32;
    private static final int ARROW_HIT_H = 20;

    private static final int OUTPUT_SLOT_X = 98;
    private static final int OUTPUT_SLOT_Y = 22;
    private static final int CRAFT_BUTTON_SIZE = 12;
    private static final int CRAFT_BUTTON_X_OFFSET = 26;
    private static final int CRAFT_BUTTON_Y_OFFSET = 12;

    private final Map<Integer, SlotContent> ingredients;
    private final SlotContent result;

    public SkyblockCraftingClientRecipe(Identifier id, Map<Integer, SlotContent> ingredients,
                                        SlotContent result, String craftText, List<String> wikiUrls) {
        super(id, wikiUrls);
        this.ingredients = Map.copyOf(ingredients);
        this.result = result;
        setCraftText(craftText);
    }

    private static void sendViewRecipeCommand(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("viewrecipe " + itemId);
        }
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
        if (hasCraftText()) {
            ctx.addAdditionalStackModifier(9, this::appendRequirementTooltip);
        }
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
        if (hasCraftText()) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, "§c!", ARROW_X + 25, ARROW_Y - 3, RecipeUiHelper.TEXT_WHITE, false);
            if (mouseX >= ARROW_X && mouseX < ARROW_X + ARROW_HIT_W
                    && mouseY >= ARROW_Y && mouseY < ARROW_Y + ARROW_HIT_H) {
                graphics.setComponentTooltipForNextFrame(font,
                        List.of(requirementTooltipLine()),
                        pos.left() + mouseX,
                        pos.top() + mouseY);
            }
        }
        maintainButtons(screen, pos);
    }

    @Override
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        Button wiki = addWikiButton(screen, pos);
        String itemId = resolveOutputId();
        if (itemId == null) {
            return wiki;
        }
        int x = pos.left() + OUTPUT_SLOT_X + CRAFT_BUTTON_X_OFFSET;
        int y = pos.top() + OUTPUT_SLOT_Y + CRAFT_BUTTON_Y_OFFSET;
        Button craft = Button.builder(Component.literal("+"), b -> sendViewRecipeCommand(itemId))
                .pos(x, y)
                .size(CRAFT_BUTTON_SIZE, CRAFT_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.literal("Craft")))
                .build();
        screen.addRecipeWidget(craft);
        return craft;
    }

    @Nullable
    private String resolveOutputId() {
        if (result == null || result.isEmpty()) {
            return null;
        }
        List<ItemStack> contents = result.getValidContents();
        if (contents.isEmpty()) {
            return null;
        }
        return SkyblockIdExtractor.extract(contents.getFirst());
    }
}
