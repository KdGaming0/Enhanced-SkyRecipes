package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

/**
 * Base class for all SkyRecipes custom client recipes.
 *
 * <p>Manages the lifecycle of per-recipe widgets (wiki button, craft button, etc.)
 * and provides shared helpers such as requirement-tooltip caching.</p>
 */
public abstract class AbstractSkyblockClientRecipe implements ReliableClientRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSkyblockClientRecipe.class);

    protected final Identifier id;
    protected final List<String> wikiUrls;
    @Nullable
    private Button wikiButton;

    private boolean buttonsDirty = true;
    @Nullable
    private AbstractWidget sentinelWidget;

    // -- Optional requirement-tooltip caching --------------------------------
    @Nullable
    private String craftText;
    private boolean hasCraftText;
    @Nullable
    private Component cachedTooltipLine;

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

    // -- Requirement tooltip helpers -----------------------------------------

    protected final void setCraftText(@Nullable String craftText) {
        this.craftText = craftText != null ? craftText : "";
        this.hasCraftText = !this.craftText.isEmpty();
        this.cachedTooltipLine = null;
    }

    protected final boolean hasCraftText() {
        return hasCraftText;
    }

    protected final void appendRequirementTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.addLast(Component.empty());
        tooltip.addLast(requirementTooltipLine());
    }

    protected final Component requirementTooltipLine() {
        Component cached = cachedTooltipLine;
        if (cached != null) {
            return cached;
        }
        cached = RecipeUiHelper.requirementTooltip(craftText);
        cachedTooltipLine = cached;
        return cached;
    }

    // -- Lifecycle -----------------------------------------------------------

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
     * Place all recipe-specific buttons. The default implementation adds the
     * wiki button when wiki URLs are available. Subclasses may override to add
     * additional buttons (e.g. craft button, navigate button).
     */
    @Nullable
    protected AbstractWidget placeButtons(RecipeViewScreen screen, RecipePosition pos) {
        return addWikiButton(screen, pos);
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
     * Offer a mouse click at recipe-relative coordinates to this recipe's
     * custom-rendered content (RRV's own {@code mouseClicked} only handles bound
     * slots). Return {@code true} to consume the click.
     */
    public boolean handleClick(double relX, double relY, int button) {
        return false;
    }

    /**
     * Offer a mouse-wheel scroll at recipe-relative coordinates to this recipe's
     * custom-rendered content, before RRV consumes it for recipe page flipping.
     * Return {@code true} to consume the scroll.
     */
    public boolean handleScroll(double relX, double relY, double scrollY) {
        return false;
    }

    // -- Wiki button ---------------------------------------------------------

    /**
     * Adds a small wiki button at the bottom-right of the recipe card.
     *
     * @return the created button, or {@code null} if no wiki URLs are available
     */
    @Nullable
    protected final Button addWikiButton(RecipeViewScreen screen, RecipePosition pos) {
        return addWikiButton(screen, pos,
                getType().getDisplayWidth() - RecipeUiHelper.WIKI_BUTTON_OFFSET,
                getType().getDisplayHeight() - RecipeUiHelper.WIKI_BUTTON_OFFSET);
    }

    /**
     * Overload that places the wiki button at a custom position relative to the
     * recipe card origin.
     *
     * @param relX X offset from {@code pos.left()}
     * @param relY Y offset from {@code pos.top()}
     * @return the created button, or {@code null} if no wiki URLs are available
     */
    @Nullable
    protected final Button addWikiButton(RecipeViewScreen screen, RecipePosition pos, int relX, int relY) {
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
        int btnX = pos.left() + relX;
        int btnY = pos.top() + relY;
        wikiButton = Button.builder(Component.literal("W"), b -> {
                    try {
                        Util.getPlatform().openUri(URI.create(url));
                    } catch (Exception e) {
                        LOGGER.debug("Failed to open wiki URL: {}", url, e);
                    }
                }).pos(btnX, btnY).size(RecipeUiHelper.WIKI_BUTTON_SIZE, RecipeUiHelper.WIKI_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.literal("Open Wiki")))
                .build();
        screen.addRecipeWidget(wikiButton);
        return wikiButton;
    }
}
