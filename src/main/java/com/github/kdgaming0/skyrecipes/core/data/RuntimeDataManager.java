package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central orchestrator for the binary data lifecycle.
 *
 * <p>Manages warm starts, cold starts, and background updates.</p>
 */
public class RuntimeDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeDataManager.class);
    private final Path dataDir;
    private final Path cacheDir;
    private final Path dataPath;
    private final Path metaPath;
    private final BinaryDataLoader loader;
    private final RuntimeUpdateService updateService;
    private final List<Consumer<DataLoadResult>> dataReadyCallbacks = new CopyOnWriteArrayList<>();
    private volatile State state = State.UNINITIALIZED;
    private ItemRegistry itemRegistry;
    private ConstantsRegistry constantsRegistry;
    private BinaryMetadata currentMetadata;
    public RuntimeDataManager(Path dataDir, Path cacheDir) {
        this.dataDir = dataDir;
        this.cacheDir = cacheDir;
        this.dataPath = dataDir.resolve("skyrecipes_data.mpk");
        this.metaPath = dataDir.resolve("skyrecipes_data.meta.json");
        this.loader = new BinaryDataLoader();
        this.updateService = new RuntimeUpdateService(dataDir, cacheDir, this::onDataReloaded);
    }

    /**
     * Attempt a warm start by loading an existing binary from disk.
     *
     * @return true if data was loaded successfully
     */
    public synchronized boolean initializeWarm() {
        if (state == State.READY || state == State.LOADING) {
            return true;
        }

        if (!Files.exists(dataPath) || !Files.exists(metaPath)) {
            LOGGER.info("No existing binary found at {} — cold start required", dataPath);
            return false;
        }

        state = State.LOADING;
        try {
            BinaryMetadata metadata = BinaryMetadata.read(metaPath);
            if (!metadata.isCompatibleWith(BinaryDataLoader.EXPECTED_SCHEMA)) {
                LOGGER.warn("Existing binary has incompatible schema version {}. Recompiling.",
                        metadata.schemaVersion());
                state = State.UNINITIALIZED;
                return false;
            }

            boolean loaded = loader.load(dataPath);
            if (!loaded) {
                LOGGER.error("Failed to load existing binary — will recompile");
                state = State.UNINITIALIZED;
                return false;
            }

            this.itemRegistry = loader.getItemRegistry();
            this.constantsRegistry = loader.getConstantsRegistry();
            this.currentMetadata = metadata;
            this.state = State.READY;

            LOGGER.info("Warm start successful: {} items from {}", itemRegistry.size(), dataPath);
            notifyCallbacks();
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to read metadata or binary during warm start", e);
            state = State.UNINITIALIZED;
            return false;
        }
    }

    // ---- Lifecycle ----

    /**
     * Launch with an ETag-first check: compare remote ETag to local metadata
     * before deciding whether to warm-start or cold-start.
     *
     * <p>The {@code onDecision} callback receives {@code true} if a warm start
     * was attempted (and succeeded), or {@code false} if a cold start was
     * triggered.</p>
     */
    public void initializeEtagFirst(Consumer<Boolean> onDecision) {
        updateService.checkEtagAsync(etagMatches -> {
            if (etagMatches) {
                boolean loaded = initializeWarm();
                if (loaded) {
                    updateService.start();
                    onDecision.accept(true);
                } else {
                    LOGGER.warn("ETag matched but warm start failed — forcing cold start");
                    initializeCold();
                    onDecision.accept(false);
                }
            } else {
                initializeCold();
                onDecision.accept(false);
            }
        });
    }

    /**
     * Schedule a cold start: download and compile in the background.
     */
    public synchronized void initializeCold() {
        if (state == State.READY || state == State.LOADING) {
            return;
        }
        state = State.LOADING;
        LOGGER.info("Starting cold start — downloading and compiling NEU data...");

        updateService.compileNow(result -> {
            if (result != null) {
                loadCompiledData(result.outputPath(), result.metaPath());
                updateService.start(); // begin scheduled checks
            } else {
                state = State.ERROR;
                LOGGER.error("Cold start compile failed. Will retry on next scheduled check.");
            }
        });
    }

    /**
     * Register a callback to be invoked when data becomes ready or is reloaded.
     * If data is already ready, the callback is invoked immediately.
     */
    public void whenReady(Consumer<DataLoadResult> callback) {
        dataReadyCallbacks.add(callback);
        if (state == State.READY) {
            callback.accept(createResult());
        }
    }

    /**
     * Reload data from a new path (used by atomic swap during background updates).
     */
    public synchronized boolean reloadData(Path newDataPath, Path newMetaPath) {
        LOGGER.info("Reloading data from {}", newDataPath);
        try {
            BinaryMetadata metadata = BinaryMetadata.read(newMetaPath);
            if (!metadata.isCompatibleWith(BinaryDataLoader.EXPECTED_SCHEMA)) {
                LOGGER.error("New binary has incompatible schema version {}", metadata.schemaVersion());
                return false;
            }

            loader.close();
            boolean loaded = loader.load(newDataPath);
            if (!loaded) {
                LOGGER.error("Failed to load new binary after swap");
                return false;
            }

            this.itemRegistry = loader.getItemRegistry();
            this.constantsRegistry = loader.getConstantsRegistry();
            this.currentMetadata = metadata;
            this.state = State.READY;

            LOGGER.info("Data reloaded successfully: {} items", itemRegistry.size());
            notifyCallbacks();
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to reload data", e);
            return false;
        }
    }

    /**
     * Shutdown: release resources and stop background services.
     */
    public void shutdown() {
        updateService.shutdown();
        loader.close();
        state = State.UNINITIALIZED;
    }

    public State getState() {
        return state;
    }

    // ---- Getters ----

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ConstantsRegistry getConstantsRegistry() {
        return constantsRegistry;
    }

    public BinaryMetadata getCurrentMetadata() {
        return currentMetadata;
    }

    public Path getDataPath() {
        return dataPath;
    }

    public Path getMetaPath() {
        return metaPath;
    }

    public RuntimeUpdateService getUpdateService() {
        return updateService;
    }

    private void onDataReloaded(Path newDataPath, Path newMetaPath) {
        reloadData(newDataPath, newMetaPath);
    }

    // ---- Internal ----

    private void loadCompiledData(Path tempPath, Path tempMeta) {
        try {
            BinaryMetadata metadata = BinaryMetadata.read(tempMeta);
            boolean loaded = loader.load(tempPath);
            if (!loaded) {
                state = State.ERROR;
                LOGGER.error("Failed to load freshly compiled binary");
                return;
            }

            // Capture registries BEFORE close() nulls them
            ItemRegistry loadedItems = loader.getItemRegistry();
            ConstantsRegistry loadedConstants = loader.getConstantsRegistry();

            // Close loader to release file lock before moving
            loader.close();

            // Move to final location for warm starts
            try {
                java.nio.file.Files.move(tempPath, dataPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.move(tempMeta, metaPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved compiled files to final location: {}", dataPath);
            } catch (IOException moveEx) {
                LOGGER.warn("Failed to move compiled files to final location, keeping at temp path", moveEx);
            }

            this.itemRegistry = loadedItems;
            this.constantsRegistry = loadedConstants;
            this.currentMetadata = metadata;
            this.state = State.READY;

            LOGGER.info("Cold start complete: {} items loaded", itemRegistry.size());
            notifyCallbacks();

        } catch (Exception e) {
            state = State.ERROR;
            LOGGER.error("Failed to read metadata or load compiled binary", e);
        }
    }

    private void notifyCallbacks() {
        DataLoadResult result = createResult();
        for (Consumer<DataLoadResult> callback : dataReadyCallbacks) {
            try {
                callback.accept(result);
            } catch (Exception e) {
                LOGGER.error("Data ready callback threw exception", e);
            }
        }
    }

    private DataLoadResult createResult() {
        return new DataLoadResult(itemRegistry, constantsRegistry, dataPath, currentMetadata);
    }

    public enum State {UNINITIALIZED, LOADING, READY, ERROR}
}
