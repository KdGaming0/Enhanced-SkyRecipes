package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RRV client-side plugin entrypoint.
 *
 * <p>Registers client recipe wrappers and reload callbacks.
 * Stub for Milestone 1 — actual wrapper registration comes in Milestone 2.
 */
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV client plugin initialized");

        ItemView.addClientReloadCallback(() -> {
            LOGGER.info("RRV client reload callback triggered");
        });
    }
}
