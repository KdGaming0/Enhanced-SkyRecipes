package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RRV server-side plugin entrypoint.
 *
 * <p>SkyRecipes uses client-only recipes via {@link ItemView#addClientRecipeProvider()},
 * so this plugin is currently minimal. It can be expanded later if server-side recipe
 * synchronization is needed.</p>
 */
public class SkyRecipesPlugin implements ReliableRecipeViewerPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesPlugin.class);

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV server plugin initialized (client-only mode)");
    }
}
