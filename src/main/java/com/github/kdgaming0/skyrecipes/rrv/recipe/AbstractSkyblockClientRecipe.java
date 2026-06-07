package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * Base class for all SkyRecipes custom client recipes.
 *
 * <p>Manages the lifecycle of per-recipe widgets (wiki button, craft button, etc.)</p>
 */
public abstract class AbstractSkyblockClientRecipe implements ReliableClientRecipe {

    protected final Identifier id;
    protected final List<String> wikiUrls;
    @Nullable
    private Button wikiButton;

    private boolean buttonsDirty = true;
    @Nullable
    private AbstractWidget sentinelWidget;

    protected AbstractSkyblockClientRecipe(Identifier id) {
        this(id, List.of());
    }

    protected AbstractSkyblockClientRecipe(Identifier id, List<String> wikiUrls) {
        this.id = id;
        this.wikiUrls = wikiUrls != null ? List.copyOf(wikiUrls) : List.of();
    }

    @Override
    public final Identifier getId() {
        return id;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }

    public List<String> getWikiUrls() {
        return wikiUrls;
    }

    @Override
    public void initRecipe() {
        buttonsDirty = true;
    }

    @Override
    public void fadeRecipe() {
        buttonsDirty = true;
        sentinelWidget = null;
        wikiButton = null;
    }

    /**
     * Re-adds recipe-specific buttons if they have been cleared by RRV's
     * {@code clearRecipeWidgets()} (e.g. on page change).
     *
     * <p>Call from {@link #renderRecipe} after custom drawing.</p>
     */
    protected final void maintainButtons(RecipeViewScreen screen, RecipePosition pos) {
        boolean dropped = sentinelWidget != null && !screen.children().contains(sentinelWidget);
        if (!buttonsDirty && !dropped) {
            return;
        }
        sentinelWidget = placeButtons(screen, pos);
        buttonsDirty = false;
    }

    /**
     * Place all recipe-specific buttons. Return any one of the placed widgets
     * to serve as a sentinel; {@code null} when no buttons are placed.
     */
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return null;
    }

    /**
     * Called after RRV has rendered slot items, in a stratum that draws on top
     * of them. Use this for count overlays and other text that must appear
     * above item sprites.
     */
    public void renderOverlay(RecipeViewScreen screen, RecipePosition pos,
                              GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // default no-op
    }

    /**
     * Adds a small wiki button at the bottom-right of the recipe card
     * where RRV's share button normally sits.
     *
     * @return the created button, or {@code null} if no wiki URLs are available
     */
    @Nullable
    protected final Button addWikiButton(RecipeViewScreen screen, RecipePosition pos) {
        if (wikiUrls.isEmpty() || wikiButton != null) {
            return wikiButton;
        }
        String url = wikiUrls.stream()
                .filter(u -> u != null && !u.isEmpty())
                .findFirst()
                .orElse(null);
        if (url == null) {
            return null;
        }
        int btnX = pos.left() + getType().getDisplayWidth() - 16;
        int btnY = pos.top() + getType().getDisplayHeight() - 16;
        wikiButton = Button.builder(Component.literal("W"), b -> {
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
