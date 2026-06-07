package com.github.kdgaming0.skyrecipes.rrv.plugin;

import cc.cassian.rrv.api.ReliableRecipeViewerPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RRV server-side plugin entrypoint.
 *
 * <p>In integrated-server mode, RRV broadcasts stack-sensitives from the server to
 * the client. We must populate {@link ItemView#STACK_SENSITIVE} here so that SkyBlock
 * items survive the server sync and appear in the client item list.</p>
 */
public class SkyRecipesPlugin implements ReliableRecipeViewerPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesPlugin.class);

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV server plugin initializing...");

        ItemView.addServerReloadCallback(() -> {
            if (SkyRecipes.isDataReady()) {
                registerServerStackSensitives();
            }
        });

        LOGGER.info("SkyRecipes RRV server plugin initialized");
    }

    private void registerServerStackSensitives() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            LOGGER.warn("ItemRegistry not available, skipping server stack-sensitive registration");
            return;
        }

        int registered = 0;
        int skipped = 0;

        for (NeuItem item : registry.getAllItems()) {
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                    ItemView.addStackSensitive(stack);
                    registered++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to build server stack for {}", item.internalName(), e);
                skipped++;
            }
        }

        LOGGER.info("Registered {} server stack-sensitives, skipped {}", registered, skipped);
    }
}
