package com.github.kdgaming0.skyrecipes.rrv.plugin;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
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
            ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
            RecipeGenerator generator = new RecipeGenerator(registry, constants);
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
        aliases.put("aotd", "ASPECT_OF_THE_DRAGON");
        aliases.put("bonemerang", "BONE_BOOMERANG");
        aliases.put("daed", "DAEDALUS_AXE");
        aliases.put("gdrag", "GOLDEN_DRAGON");
        aliases.put("edrag", "ENDER_DRAGON_PET");
        aliases.put("wither", "WITHER_SHIELD_SCROLL");
        aliases.put("sf", "SHADOW_FURY");
        aliases.put("valk", "VALKYRIE");
        aliases.put("astrea", "ASTREA");
        aliases.put("scs", "SCORPION_FOIL");
        aliases.put("spirit", "SPIRIT_SCEPTRE");
        aliases.put("giant", "GIANTS_SWORD");
        aliases.put("midas", "MIDAS_SWORD");
        aliases.put("pooch", "POOCH_SWORD");
        aliases.put("reef", "REEF_SCALES");
        aliases.put("rod", "SPEEDSTER_ROD");
        aliases.put("inferno", "INFERNO_ROD");
        aliases.put("hell", "HELLFIRE_ROD");
        aliases.put("soul", "SOUL_WHIP");
        aliases.put("wand", "WAND_OF_RESTORATION");
        aliases.put("ice", "ICE_SPRAY_WAND");
        aliases.put("plasma", "PLASMAFLUX_POWER_ORB");
        aliases.put("overflux", "OVERFLUX_POWER_ORB");
        aliases.put("manaflux", "MANAFLUX_POWER_ORB");
        aliases.put("rory", "RORY");
        aliases.put("boo", "BOO_STAFF");

        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return;

        int registered = 0;
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
