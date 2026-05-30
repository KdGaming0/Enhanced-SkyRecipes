package com.github.kdgaming0.skyrecipes;

import com.github.kdgaming0.skyrecipes.core.data.BinaryDataLoader;
import com.github.kdgaming0.skyrecipes.core.data.DataUpdateChecker;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;

public class SkyRecipes implements ClientModInitializer {

    public static final String MOD_ID = "skyrecipes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.1.1";

    private static ItemRegistry itemRegistry;
    private static ConstantsRegistry constantsRegistry;
    private static BinaryDataLoader dataLoader;

    @Override
    public void onInitializeClient() {
        LOGGER.info("SkyRecipes v{} for Minecraft {} initializing...", VERSION, MINECRAFT);

        // Load binary data
        dataLoader = new BinaryDataLoader();
        boolean loaded = loadBinaryData();

        if (loaded) {
            itemRegistry = dataLoader.getItemRegistry();
            constantsRegistry = dataLoader.getConstantsRegistry();

            // Schedule background update check (30s after first entity load = world join)
            Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("skyrecipes/cache");
            DataUpdateChecker updateChecker = new DataUpdateChecker(0L, cacheDir);
            final boolean[] triggered = { false };
            ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
                if (!triggered[0]) {
                    triggered[0] = true;
                    updateChecker.scheduleCheck(30000);
                }
            });
        } else {
            LOGGER.warn("SkyRecipes data failed to load. Mod will operate in degraded mode.");
            itemRegistry = null;
            constantsRegistry = null;
        }

        LOGGER.info("SkyRecipes initialization complete.");
    }

    private boolean loadBinaryData() {
        String resourcePath = "assets/skyrecipes/data/skyrecipes_data_v1.mpk";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.error("Binary data not found in JAR: {}", resourcePath);
                return false;
            }
            return dataLoader.load(in);
        } catch (Exception e) {
            LOGGER.error("Failed to load binary data", e);
            return false;
        }
    }

    public static ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public static ConstantsRegistry getConstantsRegistry() {
        return constantsRegistry;
    }
}
