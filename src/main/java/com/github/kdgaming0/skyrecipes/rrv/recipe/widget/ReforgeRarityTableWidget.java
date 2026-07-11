package com.github.kdgaming0.skyrecipes.rrv.recipe.widget;

import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable per-rarity table for the reforge card's all-rarities view.
 *
 * <p>Extends vanilla {@link AbstractTextAreaWidget} (an {@link AbstractScrollArea})
 * so scissoring, scrollbar rendering, wheel/drag handling and scroll clamping are
 * all vanilla behaviour. Background and decorations are disabled — the recipe card
 * texture behind it is the visual frame.</p>
 *
 * <p>Each entry in {@code rarityParagraphs} is one rarity's condensed line; it is
 * word-wrapped to the content width and separated from the next rarity by
 * {@link #RARITY_GAP} extra pixels for readability. Mouse-wheel events only reach
 * this widget through {@code RecipeViewScreenMixin}, because RRV otherwise consumes
 * scrolling over the recipe area to flip recipe pages.</p>
 */
public class ReforgeRarityTableWidget extends AbstractTextAreaWidget {

    private static final int LINE_HEIGHT = 10;
    private static final int RARITY_GAP = 4;
    private static final int SCROLL_RATE = 2 * LINE_HEIGHT;

    private record Line(String text, int y) {
    }

    private final Font font;
    private final List<Line> lines;
    private final int innerHeight;

    public ReforgeRarityTableWidget(int x, int y, int width, int height,
                                    Font font, List<String> rarityParagraphs) {
        super(x, y, width, height, Component.empty(), AbstractScrollArea.defaultSettings(SCROLL_RATE),
                false, false);
        this.font = font;

        int wrapWidth = width - totalInnerPadding();
        List<Line> built = new ArrayList<>();
        int yOffset = 0;
        for (String paragraph : rarityParagraphs) {
            for (String line : RecipeUiHelper.wrapText(font, paragraph, wrapWidth)) {
                built.add(new Line(line, yOffset));
                yOffset += LINE_HEIGHT;
            }
            yOffset += RARITY_GAP;
        }
        this.lines = List.copyOf(built);
        // Drop the trailing gap; the widget's inner padding provides the margin.
        this.innerHeight = Math.max(0, yOffset - RARITY_GAP);
    }

    @Override
    protected int getInnerHeight() {
        return innerHeight;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // Decorative stat table — nothing meaningful to narrate.
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        for (Line line : lines) {
            int top = getInnerTop() + line.y();
            if (withinContentAreaTopBottom(top, top + LINE_HEIGHT)) {
                graphics.text(font, Component.literal(line.text()), getInnerLeft(), top,
                        RecipeUiHelper.TEXT_WHITE, true);
            }
        }
    }
}
