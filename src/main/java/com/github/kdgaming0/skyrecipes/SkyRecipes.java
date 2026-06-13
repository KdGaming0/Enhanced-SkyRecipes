package com.github.kdgaming0.skyrecipes;

import com.github.kdgaming0.skyrecipes.client.command.SkyRecipesCommand;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.core.data.DataLoadResult;
import com.github.kdgaming0.skyrecipes.core.data.PipelineStatus;
import com.github.kdgaming0.skyrecipes.core.data.RuntimeDataManager;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
    private static final Object listenerLock = new Object();
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
     * Register a listener called when data becomes ready or is reloaded.
     * If data is already ready the listener fires immediately.
     * Shares a lock with {@link #notifyDataReady} so a listener registered
     * during a notify cannot fire twice for the same load.
     */
    public static void addDataReadyListener(Consumer<DataLoadResult> listener) {
        synchronized (listenerLock) {
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
    }

    private static void notifyDataReady(DataLoadResult result) {
        synchronized (listenerLock) {
            for (Consumer<DataLoadResult> listener : dataReadyListeners) {
                try {
                    listener.accept(result);
                } catch (Exception e) {
                    LOGGER.error("Data ready listener threw exception", e);
                }
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

        MidnightConfig.init(MOD_ID, SkyRecipesConfig.class);

        Path dataDir = FabricLoader.getInstance().getGameDir().resolve("skyblockdata");
        Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("skyrecipes/cache");

        // Convert config hours to seconds for the update service scheduler.
        long refreshIntervalSeconds = (long) SkyRecipesConfig.dataRefreshIntervalMinutes * 60L;
        dataManager = new RuntimeDataManager(dataDir, cacheDir, refreshIntervalSeconds);

        // Notify listeners on render thread once data is ready.
        dataManager.whenReady(result -> {
            // Without RRV the pipeline ends here: data loaded is as ready as it gets.
            if (!FabricLoader.getInstance().isModLoaded("rrv")) {
                PipelineStatus.transition(PipelineStatus.State.READY);
            }
            Minecraft mc = Minecraft.getInstance();
            //noinspection ConstantValue
            if (mc != null) {
                mc.execute(() -> {
                    buildSearchAutocomplete();
                    notifyDataReady(result);
                    LOGGER.info("SkyRecipes data is now ready.");
                });
            }
        });

        // ETag-first startup: compare remote ETag before loading local data.
        dataManager.initializeEtagFirst(warmLoaded -> {
            Minecraft mc = Minecraft.getInstance();
            //noinspection ConstantValue
            if (mc != null) {
                mc.execute(() -> {
                    if (warmLoaded) {
                        LOGGER.info("SkyRecipes warm start successful.");
                    } else {
                        LOGGER.info("SkyRecipes cold start — data will download and compile in background.");
                    }
                });
            }
        });

        SkyRecipesCommand.register();

        // One-shot chat notice per error episode: tell the player the pipeline
        // is failed/degraded when they join a world, instead of failing silently.
        ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
            PipelineStatus.Snapshot snap = PipelineStatus.snapshot();
            boolean troubled = snap.state() == PipelineStatus.State.FAILED
                    || snap.state() == PipelineStatus.State.DEGRADED;
            if (!troubled || !PipelineStatus.consumeErrorNotification()) {
                return;
            }
            String headline = snap.state() == PipelineStatus.State.FAILED
                    ? "SkyBlock recipe data failed to load"
                    : "SkyBlock recipe data could not be updated (using older data)";
            String cause = snap.lastErrorMessage() != null ? snap.lastErrorMessage() : "see log";
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "§c[SkyRecipes] " + headline + ": " + cause
                                    + ". §7Retrying automatically — run /skyrecipes status for details."));
                }
            });
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dataManager != null) dataManager.shutdown();
        }, "SkyRecipes-Shutdown"));

        LOGGER.info("SkyRecipes initialization complete.");
    }
}