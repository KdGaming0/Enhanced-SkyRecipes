package com.github.kdgaming0.skyrecipes.rrv.plugin;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.RecipeGenerator;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        // Register recipe provider early. If data is not ready, returns empty list.
        ItemView.addClientRecipeProvider(recipeList -> {
            if (SkyRecipes.isDataReady()) {
                recipeList.addAll(generateFilteredRecipes());
            }
        });

        // Register reload callback for world changes / resource reloads.
        // This is called AFTER components are bound, so ItemStack creation is safe.
        ItemView.addClientReloadCallback(() -> {
            if (SkyRecipes.isDataReady()) {
                registerStackSensitives();
                registerAliases();
                SkyRecipes.buildSearchAutocomplete();
            }
        });

        // If data is already ready at plugin init time, only register aliases
        // (which don't need bound components) and build autocomplete.
        if (SkyRecipes.isDataReady()) {
            registerAliases();
            SkyRecipes.buildSearchAutocomplete();
        }

        // Register for deferred data arrival (cold start or background update).
        // ItemStacks can only be created after components are bound (i.e. after world join).
        SkyRecipes.addDataReadyListener(result -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    SkyRecipes.buildSearchAutocomplete();
                    registerAliases();
                    // Only register stack-sensitives and trigger rebuild if we're already in a world
                    if (mc.level != null) {
                        LOGGER.info("Data became ready while in-world — triggering RRV recipe rebuild.");
                        registerStackSensitives();
                        triggerRrvRebuild();
                    }
                });
            }
        });

        LOGGER.info("SkyRecipes RRV client plugin initialized");
    }

    private List<ReliableClientRecipe> generateFilteredRecipes() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            return Collections.emptyList();
        }

        try {
            ConstantsRegistry constants = SkyRecipes.getConstantsRegistry();
            RecipeGenerator generator = new RecipeGenerator(registry, constants);
            var result = generator.generate();
            return filterRecipesByConfig(result.recipes());
        } catch (Exception e) {
            LOGGER.error("Failed to generate recipes", e);
            return Collections.emptyList();
        }
    }

    private List<ReliableClientRecipe> filterRecipesByConfig(List<ReliableClientRecipe> recipes) {
        List<ReliableClientRecipe> filtered = new ArrayList<>();
        for (ReliableClientRecipe recipe : recipes) {
            String categoryId = recipe.getType().getId().getPath();
            if (SkyRecipesConfig.isCategoryEnabled(categoryId)) {
                filtered.add(recipe);
            }
        }
        return filtered;
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
                    ItemView.addStackSensitive(stack);
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

    /** Alias map exposed for {@link com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete}. */
    public static final Map<String, String> ALIASES;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("aote", "ASPECT_OF_THE_END");
        map.put("aotv", "ASPECT_OF_THE_VOID");
        map.put("juju", "JUJU_SHORTBOW");
        map.put("livid", "LIVID_DAGGER");
        map.put("fs", "FLOWER_OF_TRUTH");
        map.put("yeti", "YETI_SWORD");
        map.put("term", "TERMINATOR");
        map.put("hype", "HYPERION");
        map.put("aotd", "ASPECT_OF_THE_DRAGON");
        map.put("bonemerang", "BONE_BOOMERANG");
        map.put("daed", "DAEDALUS_AXE");
        map.put("gdrag", "GOLDEN_DRAGON");
        map.put("edrag", "ENDER_DRAGON_PET");
        map.put("wither", "WITHER_SHIELD_SCROLL");
        map.put("sf", "SHADOW_FURY");
        map.put("valk", "VALKYRIE");
        map.put("astrea", "ASTREA");
        map.put("scs", "SCORPION_FOIL");
        map.put("spirit", "SPIRIT_SCEPTRE");
        map.put("giant", "GIANTS_SWORD");
        map.put("midas", "MIDAS_SWORD");
        map.put("pooch", "POOCH_SWORD");
        map.put("reef", "REEF_SCALES");
        map.put("rod", "SPEEDSTER_ROD");
        map.put("inferno", "INFERNO_ROD");
        map.put("hell", "HELLFIRE_ROD");
        map.put("soul", "SOUL_WHIP");
        map.put("wand", "WAND_OF_RESTORATION");
        map.put("ice", "ICE_SPRAY_WAND");
        map.put("plasma", "PLASMAFLUX_POWER_ORB");
        map.put("overflux", "OVERFLUX_POWER_ORB");
        map.put("manaflux", "MANAFLUX_POWER_ORB");
        map.put("rory", "RORY");
        map.put("boo", "BOO_STAFF");
        ALIASES = Collections.unmodifiableMap(map);
    }

    private void registerAliases() {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return;

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            registry.getByInternalName(entry.getValue()).ifPresent(neuItem -> {
                try {
                    // Avoid ItemStack creation — aliases only need the Item, which
                    // can be resolved directly without requiring bound components.
                    Item item = resolveAliasItem(neuItem);
                    if (item != null) {
                        ItemView.addAlias(item, entry.getKey());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to register alias for {}", entry.getValue());
                }
            });
        }
    }

    private static Item resolveAliasItem(com.github.kdgaming0.skyrecipes.core.model.NeuItem neuItem) {
        String itemId = neuItem.itemId();
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId);
        if (id == null) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /**
     * Trigger RRV to rebuild its client recipe cache.
     * <p>This uses an internal RRV API because the stable API does not expose
     * a public mechanism to force cache rebuild after the initial world join on Fabric.
     * If this fails, recipes will still appear on the next world join.
     */
    private void triggerRrvRebuild() {
        try {
            ClientRecipeCache.INSTANCE.buildRecipeCache(true);
            LOGGER.info("Triggered RRV client recipe cache rebuild.");
        } catch (Exception e) {
            LOGGER.warn("Failed to trigger RRV cache rebuild via internal API. " +
                "Recipes may not appear until next world join.", e);
        }
    }
}
