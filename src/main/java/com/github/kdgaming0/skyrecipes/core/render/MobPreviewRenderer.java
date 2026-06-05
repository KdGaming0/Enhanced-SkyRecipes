package com.github.kdgaming0.skyrecipes.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a rotating {@link LivingEntity} preview inside a recipe card.
 *
 * <p>Uses Minecraft 26.1's {@link GuiGraphicsExtractor#entity} API with
 * {@link EntityRenderState} extracted from the preview entity.</p>
 */
public final class MobPreviewRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobPreviewRenderer.class);

    private MobPreviewRenderer() {
    }

    /**
     * Render a preview entity at the given screen coordinates.
     *
     * @param graphics  the gui graphics context
     * @param entity    the preview entity (must be non-null)
     * @param x         screen X centre
     * @param y         screen Y centre
     * @param scale     render scale (e.g. 24.0f)
     * @param rotationY rotation around Y-axis in degrees
     */
    @SuppressWarnings("unchecked")
    public static void render(GuiGraphicsExtractor graphics, LivingEntity entity,
                              int x, int y, float scale, float rotationY) {
        if (entity == null || graphics == null) {
            return;
        }

        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            EntityRenderer<LivingEntity, EntityRenderState> renderer =
                    (EntityRenderer<LivingEntity, EntityRenderState>) dispatcher.getRenderer(entity);

            EntityRenderState state = renderer.createRenderState(entity, 1.0f);
            // Full-bright lighting for GUI preview
            state.lightCoords = 15728880;

            Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(rotationY));
            Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);

            int size = (int) (scale * 1.5f);
            graphics.entity(
                    state,
                    scale,
                    translation,
                    rotation,
                    null,
                    x - size / 2,
                    y - size / 2,
                    x + size / 2,
                    y + size / 2
            );
        } catch (Exception e) {
            LOGGER.debug("Failed to render mob preview: {}", e.getMessage());
        }
    }

    /**
     * Compute a slowly changing Y-rotation angle based on time.
     *
     * @return rotation in degrees
     */
    public static float getRotationAngle() {
        return (System.currentTimeMillis() % 20000L) / 20000.0f * 360.0f;
    }
}
