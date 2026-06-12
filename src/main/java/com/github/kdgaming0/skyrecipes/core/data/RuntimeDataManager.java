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
 * <p>Manages warm starts, cold starts, and background updates.
 * The data-refresh interval is provided at construction time from
 * {@code SkyRecipesConfig.dataRefreshIntervalHours}.</p>
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
    private volatile Path currentLoadedPath;

    /**
     * @param dataDir               directory containing the compiled .mpk and .meta.json
     * @param cacheDir              directory for the downloaded NEU repo ZIP and ETag cache
     * @param refreshIntervalSeconds how often background update checks run, in seconds
     */
    public RuntimeDataManager(Path dataDir, Path cacheDir, long refreshIntervalSeconds) {
        this.dataDir = dataDir;
        this.cacheDir = cacheDir;
        this.dataPath = dataDir.resolve("skyrecipes_data.mpk");
        this.metaPath = dataDir.resolve("skyrecipes_data.meta.json");
        this.loader = new BinaryDataLoader();
        this.updateService = new RuntimeUpdateService(
                dataDir, cacheDir, this::onDataReloaded, refreshIntervalSeconds);
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

        if (!Files.exists(dataPath)) {
            LOGGER.info("No existing binary found at {} — cold start required", dataPath);
            return false;
        }

        state = State.LOADING;
        PipelineStatus.transition(PipelineStatus.State.LOADING);
        try {
            long loadStart = System.currentTimeMillis();
            boolean loaded = loader.load(dataPath);
            PipelineStatus.recordStageDuration("load", System.currentTimeMillis() - loadStart);
            if (!loaded) {
                if (loader.getLastFailure() == BinaryDataLoader.LoadFailure.CORRUPT) {
                    quarantineCorruptFile(dataPath);
                } else {
                    LOGGER.warn("Existing binary has an incompatible schema. Recompiling.");
                }
                state = State.UNINITIALIZED;
                return false;
            }

            this.itemRegistry = loader.getItemRegistry();
            this.constantsRegistry = loader.getConstantsRegistry();
            this.currentMetadata = loader.getMetadata();
            this.currentLoadedPath = dataPath;
            this.state = State.READY;
            recordDataInfo();

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

    /** Schedule a cold start: download and compile in the background. */
    public synchronized void initializeCold() {
        if (state == State.READY || state == State.LOADING) {
            return;
        }
        state = State.LOADING;
        LOGGER.info("Starting cold start — downloading and compiling NEU data...");

        updateService.compileNow(result -> {
            if (result != null) {
                loadCompiledData(result.outputPath(), result.metaPath());
            } else {
                state = State.ERROR;
            }

            if (state == State.READY) {
                updateService.onPipelineSuccess();
            } else {
                LOGGER.error("Cold start failed — automatic retry scheduled.");
                updateService.scheduleRetry();
            }
            // Regular cadence must exist regardless of outcome, so recovery
            // does not depend on a successful first attempt.
            updateService.start();
        });
    }

    /**
     * Register a callback invoked when data becomes ready or is reloaded.
     * If data is already ready the callback fires immediately.
     * Synchronized with {@link #notifyCallbacks()} so a callback registered
     * during a notify cannot fire twice for the same load.
     */
    public synchronized void whenReady(Consumer<DataLoadResult> callback) {
        dataReadyCallbacks.add(callback);
        if (state == State.READY) {
            callback.accept(createResult());
        }
    }

    /** Reload data from a new path (used by atomic swap during background updates). */
    public synchronized boolean reloadData(Path newDataPath, Path newMetaPath) {
        LOGGER.info("Reloading data from {}", newDataPath);
        PipelineStatus.transition(PipelineStatus.State.LOADING);
        try {
            loader.close();
            long loadStart = System.currentTimeMillis();
            boolean loaded = loader.load(newDataPath);
            PipelineStatus.recordStageDuration("load", System.currentTimeMillis() - loadStart);
            if (!loaded) {
                LOGGER.error("Failed to load new binary after swap");
                return false;
            }

            this.itemRegistry = loader.getItemRegistry();
            this.constantsRegistry = loader.getConstantsRegistry();
            this.currentMetadata = loader.getMetadata();
            this.currentLoadedPath = newDataPath;
            this.state = State.READY;
            recordDataInfo();

            LOGGER.info("Data reloaded successfully: {} items", itemRegistry.size());
            notifyCallbacks();
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to reload data", e);
            return false;
        }
    }

    /** Shutdown: release resources and stop background services. */
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

    /**
     * Force a complete pipeline rebuild regardless of ETag, with progress and completion callbacks.
     * A self-removing one-shot callback fires {@code onSuccess} on the update-service thread
     * once data reloads; callers must dispatch to the render thread themselves.
     * {@code onFailure} fires on the scheduler thread if any pipeline stage fails.
     */
    public void forceRefreshNow(Consumer<String> onProgress, Runnable onSuccess, Runnable onFailure) {
        Consumer<DataLoadResult> callback = new Consumer<DataLoadResult>() {
            @Override
            public void accept(DataLoadResult result) {
                dataReadyCallbacks.remove(this);
                if (onSuccess != null) onSuccess.run();
            }
        };
        dataReadyCallbacks.add(callback);
        updateService.forceRefreshNow(onProgress, onFailure);
    }

    // ---- Internal ----

    private boolean onDataReloaded(Path newDataPath, Path newMetaPath) {
        return reloadData(newDataPath, newMetaPath);
    }

    private synchronized void loadCompiledData(Path tempPath, Path tempMeta) {
        PipelineStatus.transition(PipelineStatus.State.LOADING);
        try {
            // Move first, while no loader holds the temp file, then load from
            // wherever the data actually ended up.
            Path loadPath = tempPath;
            Path loadMeta = tempMeta;
            try {
                java.nio.file.Files.move(tempPath, dataPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.move(tempMeta, metaPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                loadPath = dataPath;
                loadMeta = metaPath;
                LOGGER.info("Moved compiled files to final location: {}", dataPath);
            } catch (IOException moveEx) {
                LOGGER.warn("Failed to move compiled files to final location, loading from temp path", moveEx);
            }

            long loadStart = System.currentTimeMillis();
            boolean loaded = loader.load(loadPath);
            PipelineStatus.recordStageDuration("load", System.currentTimeMillis() - loadStart);
            if (!loaded) {
                state = State.ERROR;
                LOGGER.error("Failed to load freshly compiled binary");
                PipelineStatus.recordError("load", "Freshly compiled data could not be loaded", null);
                return;
            }

            this.itemRegistry = loader.getItemRegistry();
            this.constantsRegistry = loader.getConstantsRegistry();
            this.currentMetadata = loader.getMetadata();
            this.currentLoadedPath = loadPath;
            this.state = State.READY;
            recordDataInfo();

            LOGGER.info("Cold start complete: {} items loaded", itemRegistry.size());
            notifyCallbacks();

        } catch (Exception e) {
            state = State.ERROR;
            LOGGER.error("Failed to read metadata or load compiled binary", e);
            PipelineStatus.recordError("load", "Compiled data could not be loaded: " + e.getMessage(), e);
        }
    }

    private void recordDataInfo() {
        if (currentMetadata != null && itemRegistry != null) {
            PipelineStatus.recordDataInfo(currentMetadata.buildTimestamp(),
                    currentMetadata.etag(), itemRegistry.size());
        }
    }

    /**
     * Move a corrupt binary aside so the cold start cannot be poisoned by it
     * and the file remains available for bug reports. The loader may still
     * hold a mapping from the failed load, so it is closed first.
     */
    private void quarantineCorruptFile(Path path) {
        loader.close();
        Path corrupt = path.resolveSibling(path.getFileName() + ".corrupt");
        try {
            Files.move(path, corrupt, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Quarantined corrupt binary to {} — recompiling", corrupt);
        } catch (IOException e) {
            LOGGER.warn("Failed to quarantine corrupt binary at {}", path, e);
        }
    }

    private synchronized void notifyCallbacks() {
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
        Path loadedPath = currentLoadedPath != null ? currentLoadedPath : dataPath;
        return new DataLoadResult(itemRegistry, constantsRegistry, loadedPath, currentMetadata);
    }

    public enum State { UNINITIALIZED, LOADING, READY, ERROR }
}