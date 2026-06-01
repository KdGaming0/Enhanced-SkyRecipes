package com.github.kdgaming0.skyrecipes.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Scheduled background service that checks for NEU repository updates,
 * downloads, compiles, and atomically swaps the binary data file.
 */
public class RuntimeUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeUpdateService.class);

    private static final String NEU_REPO_URL =
        "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final long CHECK_INTERVAL_MINUTES = 15;
    private static final long INITIAL_DELAY_SECONDS = 5;

    private final Path dataDir;
    private final Path cacheDir;
    private final BiConsumer<Path, Path> onDataUpdated;
    private final ScheduledExecutorService scheduler;
    private final BinaryDataCompiler compiler;

    private volatile boolean running = false;
    private volatile boolean compileInProgress = false;

    public RuntimeUpdateService(Path dataDir, Path cacheDir, BiConsumer<Path, Path> onDataUpdated) {
        this.dataDir = dataDir;
        this.cacheDir = cacheDir;
        this.onDataUpdated = onDataUpdated;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SkyRecipes-UpdateService");
            t.setDaemon(true);
            return t;
        });
        this.compiler = new BinaryDataCompiler();
    }

    // ---- Public API ----

    /**
     * Start scheduled update checks.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler.scheduleWithFixedDelay(
            this::performUpdateCheck,
            INITIAL_DELAY_SECONDS,
            CHECK_INTERVAL_MINUTES * 60,
            TimeUnit.SECONDS
        );
        LOGGER.info("Update service started. Checking every {} minutes.", CHECK_INTERVAL_MINUTES);
    }

    /**
     * Stop scheduled checks.
     */
    public synchronized void shutdown() {
        running = false;
        scheduler.shutdownNow();
        LOGGER.info("Update service shut down.");
    }

    /**
     * Force an immediate update check.
     */
    public void checkNow() {
        scheduler.execute(this::performUpdateCheck);
    }

    /**
     * Check remote ETag against local metadata asynchronously.
     * Calls {@code onComplete} with {@code true} if local data is up-to-date
     * and a warm start should be attempted; {@code false} if a cold start
     * (download + compile) is required.
     *
     * <p>If the network is unreachable, returns {@code true} when local data
     * exists so the user isn't left with no recipes.</p>
     */
    public void checkEtagAsync(Consumer<Boolean> onComplete) {
        scheduler.execute(() -> {
            try {
                String currentEtag = readCurrentEtag();
                String remoteEtag = fetchRemoteEtag();

                if (remoteEtag == null) {
                    // Network unavailable — use warm start if we have any local data
                    LOGGER.warn("Could not fetch remote ETag (network unavailable). Using local data if present.");
                    onComplete.accept(currentEtag != null);
                    return;
                }

                if (currentEtag == null) {
                    LOGGER.info("No local metadata found — cold start required.");
                    onComplete.accept(false);
                    return;
                }

                boolean matches = remoteEtag.equals(currentEtag);
                if (matches) {
                    LOGGER.info("Remote ETag matches local ({}). Warm start permitted.", remoteEtag);
                } else {
                    LOGGER.info("Remote ETag changed (remote: {}, local: {}). Cold start required.", remoteEtag, currentEtag);
                }
                onComplete.accept(matches);

            } catch (Exception e) {
                LOGGER.error("ETag check failed unexpectedly", e);
                onComplete.accept(false);
            }
        });
    }

    /**
     * Compile data immediately (for cold start). Runs on the scheduler thread.
     */
    public void compileNow(Consumer<BinaryDataCompiler.CompileResult> onComplete) {
        scheduler.execute(() -> {
            try {
                BinaryDataCompiler.CompileResult result = downloadAndCompile(null);
                onComplete.accept(result);
            } catch (Exception e) {
                LOGGER.error("Immediate compile failed", e);
                onComplete.accept(null);
            }
        });
    }

    // ---- Internal ----

    private void performUpdateCheck() {
        if (compileInProgress) {
            LOGGER.debug("Compile already in progress, skipping scheduled check.");
            return;
        }

        try {
            String currentEtag = readCurrentEtag();
            String remoteEtag = fetchRemoteEtag();

            if (remoteEtag == null) {
                LOGGER.warn("Could not fetch remote ETag. Skipping update check.");
                return;
            }

            if (remoteEtag.equals(currentEtag)) {
                LOGGER.debug("Data is up to date (ETag match).");
                return;
            }

            LOGGER.info("New NEU data detected (ETag changed). Starting update...");
            compileInProgress = true;

            try {
                BinaryDataCompiler.CompileResult result = downloadAndCompile(remoteEtag);
                if (result == null) {
                    LOGGER.error("Update compile failed. Keeping old data.");
                    return;
                }

                // Validate by loading the new file
                if (!validateCompiledFile(result.outputPath(), result.metaPath())) {
                    LOGGER.error("Validation failed for new binary. Keeping old data.");
                    cleanupTempFiles();
                    return;
                }

                // Atomic swap
                if (!atomicSwap(result.outputPath(), result.metaPath())) {
                    LOGGER.error("Atomic swap failed. Keeping old data.");
                    cleanupTempFiles();
                    return;
                }

                LOGGER.info("Update complete. Swapped to new binary with {} items.", result.itemCount());

            } finally {
                compileInProgress = false;
            }

        } catch (Exception e) {
            LOGGER.error("Update check failed", e);
            compileInProgress = false;
        }
    }

    private BinaryDataCompiler.CompileResult downloadAndCompile(String expectedEtag) throws Exception {
        Files.createDirectories(cacheDir);
        Files.createDirectories(dataDir);

        Path zipFile = cacheDir.resolve("neu-repo.zip");
        Path etagFile = cacheDir.resolve("neu-repo.etag");

        // Download if needed
        String existingEtag = null;
        if (Files.exists(etagFile)) {
            existingEtag = Files.readString(etagFile).trim();
        }

        boolean downloaded = compiler.downloadNeuRepo(zipFile, etagFile, existingEtag);
        if (downloaded) {
            LOGGER.info("Downloaded fresh NEU repo.");
        } else {
            LOGGER.info("Using cached NEU repo.");
        }

        String actualEtag = downloaded ? Files.readString(etagFile).trim() : existingEtag;
        if (actualEtag == null) actualEtag = "";

        // Use expectedEtag if provided (from update check), otherwise use downloaded etag
        String etagForMeta = expectedEtag != null ? expectedEtag : actualEtag;

        Path tempMpk = dataDir.resolve("skyrecipes_data_new.mpk");
        Path tempMeta = dataDir.resolve("skyrecipes_data_new.meta.json");

        return compiler.compileToPath(zipFile, tempMpk, tempMeta, etagForMeta, null);
    }

    private boolean validateCompiledFile(Path mpkPath, Path metaPath) {
        try {
            BinaryMetadata metadata = BinaryMetadata.read(metaPath);
            if (!metadata.isCompatibleWith(BinaryDataLoader.EXPECTED_SCHEMA)) {
                LOGGER.error("Validation failed: incompatible schema version {}", metadata.schemaVersion());
                return false;
            }

            BinaryDataLoader validator = new BinaryDataLoader();
            boolean loaded = validator.load(mpkPath);
            validator.close();

            if (!loaded) {
                LOGGER.error("Validation failed: could not load binary.");
                return false;
            }

            LOGGER.debug("Validation passed for new binary.");
            return true;

        } catch (IOException e) {
            LOGGER.error("Validation failed with exception", e);
            return false;
        }
    }

    private boolean atomicSwap(Path newMpk, Path newMeta) {
        Path finalMpk = dataDir.resolve("skyrecipes_data.mpk");
        Path finalMeta = dataDir.resolve("skyrecipes_data.meta.json");

        try {
            // On Windows, the old file may be memory-mapped and locked.
            // The caller (RuntimeDataManager) should unmap before calling this.
            // We try atomic move first, then fallback to regular replace.

            try {
                Files.move(newMpk, finalMpk, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                Files.move(newMeta, finalMeta, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Atomic move failed ({}), falling back to regular replace", e.getMessage());
                Files.move(newMpk, finalMpk, StandardCopyOption.REPLACE_EXISTING);
                Files.move(newMeta, finalMeta, StandardCopyOption.REPLACE_EXISTING);
            }

            // Notify the manager to reload
            onDataUpdated.accept(finalMpk, finalMeta);
            return true;

        } catch (IOException e) {
            LOGGER.error("Atomic swap failed", e);
            return false;
        }
    }

    private void cleanupTempFiles() {
        try {
            Files.deleteIfExists(dataDir.resolve("skyrecipes_data_new.mpk"));
            Files.deleteIfExists(dataDir.resolve("skyrecipes_data_new.meta.json"));
        } catch (IOException e) {
            LOGGER.debug("Failed to clean up temp files", e);
        }
    }

    public String readCurrentEtag() {
        Path metaPath = dataDir.resolve("skyrecipes_data.meta.json");
        try {
            if (Files.exists(metaPath)) {
                BinaryMetadata metadata = BinaryMetadata.read(metaPath);
                return metadata.etag();
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to read current ETag", e);
        }
        return null;
    }

    private String fetchRemoteEtag() {
        try {
            URL url = new URL(NEU_REPO_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warn("HEAD request returned {}", responseCode);
                return null;
            }

            String etag = conn.getHeaderField("ETag");
            conn.disconnect();
            return etag;

        } catch (IOException e) {
            LOGGER.warn("Failed to fetch remote ETag", e);
            return null;
        }
    }
}
