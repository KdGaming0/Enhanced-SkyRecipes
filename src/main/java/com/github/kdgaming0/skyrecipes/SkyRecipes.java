package com.github.kdgaming0.skyrecipes;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.data.DataLoadResult;
import com.github.kdgaming0.skyrecipes.core.data.RuntimeDataManager;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SkyRecipes implements ClientModInitializer {

    public static final String MOD_ID = "skyrecipes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.1.1";
    private static final List<Consumer<DataLoadResult>> dataReadyListeners = new CopyOnWriteArrayList<>();
    private static RuntimeDataManager dataManager;
    private static SearchAutocomplete searchAutocomplete;

    public static boolean isDataReady() {
        return dataManager != null && dataManager.getState() == RuntimeDataManager.State.READY;
    }

    public static ItemRegistry getItemRegistry() {
        return dataManager != null ? dataManager.getItemRegistry() : null;
    }

    public static ConstantsRegistry getConstantsRegistry() {
        return dataManager != null ? dataManager.getConstantsRegistry() : null;
    }

    public static RuntimeDataManager getDataManager() {
        return dataManager;
    }

    /**
     * Register a listener that will be called when data becomes ready.
     * If data is already ready, the listener is called immediately.
     * This is safe to call before {@link #onInitializeClient()} completes.
     */
    public static void addDataReadyListener(Consumer<DataLoadResult> listener) {
        dataReadyListeners.add(listener);
        if (isDataReady() && dataManager != null) {
            listener.accept(new DataLoadResult(
                    dataManager.getItemRegistry(),
                    dataManager.getConstantsRegistry(),
                    dataManager.getDataPath(),
                    dataManager.getCurrentMetadata()
            ));
        }
    }

    private static void notifyDataReady(DataLoadResult result) {
        for (Consumer<DataLoadResult> listener : dataReadyListeners) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                LOGGER.error("Data ready listener threw exception", e);
            }
        }
    }

    public static SearchAutocomplete getSearchAutocomplete() {
        return searchAutocomplete;
    }

    /**
     * Build the search autocomplete index once data is loaded.
     * Called internally and from the RRV client plugin after aliases are prepared.
     */
    public static void buildSearchAutocomplete() {
        ItemRegistry registry = getItemRegistry();
        if (registry == null) {
            LOGGER.warn("Cannot build search autocomplete: ItemRegistry not loaded");
            return;
        }

        Map<String, String> aliases = com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin.ALIASES;
        List<String> pageNames = List.of(
                "Crafting", "Forge", "Drops", "NPC Shop", "NPC Info",
                "Kat Upgrade", "Trade", "Wiki Info", "Essence Upgrade",
                "Reforge", "Garden Mutation"
        );
        searchAutocomplete = new SearchAutocomplete(registry, aliases, pageNames);
        LOGGER.info("Search autocomplete index built with {} entries",
                registry.getAllItems().size() + aliases.size() + pageNames.size());
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("SkyRecipes v{} for Minecraft {} initializing...", VERSION, MINECRAFT);

        // Initialise MidnightLib configuration
        MidnightConfig.init(MOD_ID, SkyRecipesConfig.class);

        // Set up data directories
        Path dataDir = FabricLoader.getInstance().getGameDir().resolve("skyblockdata");
        Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("skyrecipes/cache");

        // Initialise runtime data manager
        dataManager = new RuntimeDataManager(dataDir, cacheDir);

        // Register callback for when data becomes ready (warm or cold start completion)
        dataManager.whenReady(result -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    buildSearchAutocomplete();
                    notifyDataReady(result);
                    LOGGER.info("SkyRecipes data is now ready.");
                });
            }
        });

        // ETag-first startup: check remote ETag before loading local data
        dataManager.initializeEtagFirst(warmLoaded -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (warmLoaded) {
                        LOGGER.info("SkyRecipes warm start successful.");
                        buildSearchAutocomplete();
                    } else {
                        LOGGER.info("SkyRecipes cold start — data will download and compile in background.");
                    }
                });
            }
        });

        // Register shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dataManager != null) {
                dataManager.shutdown();
            }
        }, "SkyRecipes-Shutdown"));

        LOGGER.info("SkyRecipes initialization complete.");
    }
}
