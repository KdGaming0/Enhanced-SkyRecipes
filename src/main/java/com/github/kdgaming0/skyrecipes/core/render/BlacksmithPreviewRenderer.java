package com.github.kdgaming0.skyrecipes.core.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders a blacksmith (Villager) preview for reforge recipes.
 *
 * <p>Delegates to {@link MobPreviewRenderer} with a fixed Villager entity.</p>
 */
public final class BlacksmithPreviewRenderer {

    private BlacksmithPreviewRenderer() {
    }

    /**
     * Render the blacksmith villager preview.
     *
     * @param graphics the gui graphics context
     * @param villager the villager entity
     * @param x        screen X centre
     * @param y        screen Y centre
     * @param scale    render scale
     */
    public static void render(GuiGraphicsExtractor graphics, LivingEntity villager,
                              int x, int y, float scale) {
        MobPreviewRenderer.render(graphics, villager, x, y, scale, MobPreviewRenderer.getRotationAngle());
    }
}
