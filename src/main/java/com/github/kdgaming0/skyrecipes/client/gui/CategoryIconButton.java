package com.github.kdgaming0.skyrecipes.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;

/**
 * A square icon button that renders one of four category-filter sprites depending on its
 * toggled and hovered state. No text is drawn; the icon communicates the category.
 *
 * <p>Sprite names follow the pattern (all under
 * {@code skyrecipes:gui/sprites/item_list/}):
 * <ul>
 *   <li>{@code <base>.png} — normal</li>
 *   <li>{@code <base>_highlighted.png} — hovered</li>
 *   <li>{@code <base>_toggled.png} — active filter</li>
 *   <li>{@code <base>_toggled_highlighted.png} — active filter + hovered</li>
 * </ul>
 */
public final class CategoryIconButton extends AbstractButton {

    private static final String NAMESPACE = "skyrecipes";
    private static final String SPRITE_BASE = "item_list/";
    private final Identifier spriteNormal;
    private final Identifier spriteHighlighted;
    private final Identifier spriteToggled;
    private final Identifier spriteToggledHighlighted;
    private final PressHandler pressHandler;
    private boolean toggled;

    /**
     * @param x            left position in screen pixels
     * @param y            top position in screen pixels
     * @param size         width and height in pixels (buttons are square)
     * @param spriteName   base sprite name without suffix, e.g. {@code "armour"}
     * @param toggled      initial toggled (active filter) state
     * @param pressHandler called when the button is pressed
     */
    public CategoryIconButton(
            int x,
            int y,
            int size,
            String spriteName,
            boolean toggled,
            PressHandler pressHandler) {
        super(x, y, size, size, Component.empty());
        this.toggled = toggled;
        this.pressHandler = pressHandler;

        spriteNormal = sprite(spriteName);
        spriteHighlighted = sprite(spriteName + "_highlighted");
        spriteToggled = sprite(spriteName + "_toggled");
        spriteToggledHighlighted = sprite(spriteName + "_toggled_highlighted");
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, SPRITE_BASE + name);
    }

    // ── State ─────────────────────────────────────────────────────────────────────

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public void setTooltipText(String text) {
        setTooltip(Tooltip.create(Component.literal(text)));
    }

    // ── AbstractButton contract ───────────────────────────────────────────────────

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        pressHandler.onPress(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Identifier sprite = resolveSprite(isHoveredOrFocused());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), width, height,
                ARGB.white(alpha));
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    private Identifier resolveSprite(boolean hovered) {
        if (toggled) return hovered ? spriteToggledHighlighted : spriteToggled;
        return hovered ? spriteHighlighted : spriteNormal;
    }

    /**
     * Called when the button is pressed; receives the owning button instance.
     */
    public interface PressHandler {
        void onPress(CategoryIconButton button);
    }
}
