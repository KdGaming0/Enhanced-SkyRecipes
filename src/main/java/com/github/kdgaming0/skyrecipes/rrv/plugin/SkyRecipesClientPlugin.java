package com.github.kdgaming0.skyrecipes.rrv.plugin;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * RRV client-side plugin entrypoint.
 *
 * <p>Registers client recipes, stack-sensitives, and aliases.</p>
 */
public class SkyRecipesClientPlugin implements ReliableRecipeViewerClientPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyRecipesClientPlugin.class);

    @Override
    public void onIntegrationInitialize() {
        LOGGER.info("SkyRecipes RRV client plugin initializing...");

        registerRecipes();
        registerStackSensitives();
        registerAliases();

        // Re-register on client reload (e.g., world change, resource reload)
        ItemView.addClientReloadCallback(() -> {
            registerStackSensitives();
            registerAliases();
        });

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    private void registerRecipes() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            LOGGER.warn("ItemRegistry not available, skipping recipe registration");
            return;
        }

        try {
            RecipeGenerator generator = new RecipeGenerator(registry);
            var result = generator.generate();

            ItemView.addClientRecipeProvider(recipeList -> {
                recipeList.addAll(result.recipes());
            });

            LOGGER.info("Registered {} SkyBlock recipes with RRV", result.recipes().size());
        } catch (Exception e) {
            LOGGER.error("Failed to generate and register recipes", e);
        }
    }

    private void registerStackSensitives() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            LOGGER.warn("ItemRegistry not available, skipping stack-sensitive registration");
            return;
        }

        int registered = 0;
        int skipped = 0;

        for (NeuItem item : registry.getAllItems()) {
            try {
                ItemStack stack = ItemStackBuilder.build(item);
                if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                    ClientRecipeCache.INSTANCE.addStackSensitive(new ItemView.StackSensitive(stack));
                    registered++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to build stack for {}: {}", item.internalName(), e.getMessage());
                skipped++;
            }
        }

        LOGGER.info("Registered {} stack-sensitives, skipped {}", registered, skipped);
    }

    private void registerAliases() {
        // Common SkyBlock abbreviations
        Map<String, String> aliases = new HashMap<>();
        aliases.put("aote", "ASPECT_OF_THE_END");
        aliases.put("aotv", "ASPECT_OF_THE_VOID");
        aliases.put("juju", "JUJU_SHORTBOW");
        aliases.put("livid", "LIVID_DAGGER");
        aliases.put("fs", "FLOWER_OF_TRUTH");
        aliases.put("yeti", "YETI_SWORD");
        aliases.put("term", "TERMINATOR");
        aliases.put("hype", "HYPERION");

        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return;

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            registry.getByInternalName(entry.getValue()).ifPresent(neuItem -> {
                try {
                    ItemStack stack = ItemStackBuilder.build(neuItem);
                    if (!stack.isEmpty()) {
                        ItemView.addAlias(stack.getItem(), entry.getKey());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to register alias for {}", entry.getValue());
                }
            });
        }
    }
}
