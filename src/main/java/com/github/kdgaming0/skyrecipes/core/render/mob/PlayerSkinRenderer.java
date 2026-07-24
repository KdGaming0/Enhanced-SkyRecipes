package com.github.kdgaming0.skyrecipes.core.render.mob;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
//? if >=26.2 {
/*import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else {
import net.minecraft.client.model.player.PlayerModel;
 
//?}
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a player skin texture into a screen rectangle.
 *
 * <p>Owns the cached player model and the standard skin-rendering constants
 * so callers only need to supply a texture, bounds, and rotation angle.</p>
 */
public final class PlayerSkinRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSkinRenderer.class);
    private static final float ROT_X = -5.0f;
    private static final float PIVOT_Y = -1.0625f;

    @Nullable
            //? if >=26.2 {
    /*private static Model.Simple cachedPlayerModel;
    *///?} else {
    private static PlayerModel cachedPlayerModel;
     
    //?}
    @Nullable
    private static EntityModelSet cachedModelSource;
    private static boolean playerModelLookupFailed;
    private static boolean modelFailureLogged;

    private PlayerSkinRenderer() {
    }

    /**
     * Renders a player skin at the given screen coordinates.
     *
     * @param graphics  the gui graphics context
     * @param texture   the skin texture identifier
     * @param x         screen left
     * @param y         screen top
     * @param width     render width
     * @param height    render height
     * @param rotationY rotation around the Y axis in degrees
     */
    public static void render(GuiGraphicsExtractor graphics, Identifier texture,
                              int x, int y, int width, int height, float rotationY) {
        if (texture == null) return;

        //? if >=26.2 {
        /*Model.Simple model = getPlayerModel();
        *///?} else {
        PlayerModel model = getPlayerModel();
         
        //?}
        if (model == null) return;

        float scale = 0.97f * height / 2.125f;
        graphics.skin(model, texture, scale, ROT_X, rotationY, PIVOT_Y,
                x, y, x + width, y + height);
    }

    @Nullable
            //? if >=26.2 {
    /*public static Model.Simple getPlayerModel() {
        *///?} else {
        public static PlayerModel getPlayerModel() {
         
        //?}
        EntityModelSet models = Minecraft.getInstance().getEntityModels();
        // A resource reload replaces the EntityModelSet instance — rebake against the new one.
        if (models != cachedModelSource) {
            cachedModelSource = models;
            cachedPlayerModel = null;
            playerModelLookupFailed = false;
        }
        if (cachedPlayerModel != null) return cachedPlayerModel;
        if (playerModelLookupFailed) return null;

        try {
            //? if >=26.2 {
            /*cachedPlayerModel = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
            *///?} else {
            cachedPlayerModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
             
            //?}
            return cachedPlayerModel;
        } catch (Exception e) {
            playerModelLookupFailed = true;
            if (!modelFailureLogged) {
                modelFailureLogged = true;
                LOGGER.error("Failed to create PlayerModel for skin preview.", e);
            }
            return null;
        }
    }
}