package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RRV server-side plugin entrypoint.
 *
 * <p>Registers server recipe providers and reload callbacks.
 * Stub for Milestone 1 — actual recipe registration comes in Milestone 2.
 */
public class SkyRecipesPlugin implements ReliableRecipeViewerPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesPlugin.class);

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV server plugin initialized");

        ItemView.addServerRecipeProvider(recipeList -> {
            // Recipe providers will be registered in Milestone 2
        });

        ItemView.addServerReloadCallback(() -> {
            LOGGER.info("RRV server reload callback triggered");
        });
    }
}
