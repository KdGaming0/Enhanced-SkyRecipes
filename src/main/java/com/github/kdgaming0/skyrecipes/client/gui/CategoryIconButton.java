package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * A 16×16 icon button that renders a representative {@link ItemStack} for a
 * {@link SkyblockItemCategory}. A colored border indicates the active state.
 */
public final class CategoryIconButton extends AbstractButton {

    private static final int SIZE = 18;
    private static final int BORDER_NORMAL = 0xFF444444;
    private static final int BORDER_HOVER = 0xFFAAAAAA;
    private static final int BORDER_ACTIVE = 0xFF44AA44;
    private static final int BORDER_ACTIVE_HOVER = 0xFF66CC66;
    private static final int BG_COLOR = 0xFF222222;

    private final SkyblockItemCategory category;
    private final ItemStack iconStack;
    private final Runnable onToggle;

    public CategoryIconButton(int x, int y,
                              SkyblockItemCategory category,
                              ItemStack iconStack,
                              boolean active,
                              Runnable onToggle) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.category = category;
        this.iconStack = iconStack;
        this.onToggle = onToggle;
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal(category.getDisplayName())));
    }

    private boolean toggled = false;

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public boolean isToggled() {
        return toggled;
    }

    public SkyblockItemCategory getCategory() {
        return category;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onToggle.run();
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int borderColor = toggled
            ? (hovered ? BORDER_ACTIVE_HOVER : BORDER_ACTIVE)
            : (hovered ? BORDER_HOVER : BORDER_NORMAL);

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
        // Border
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

        // Item icon (16×16 inside 18×18 button)
        if (!iconStack.isEmpty()) {
            graphics.fakeItem(iconStack, getX() + 1, getY() + 1);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
