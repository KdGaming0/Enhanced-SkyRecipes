package com.github.kdgaming0.skyrecipes.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Scheduled background service that checks for NEU repository updates,
 * downloads, compiles, and atomically swaps the binary data file.
 */
public class RuntimeUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeUpdateService.class);

    private static final String NEU_REPO_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final long INITIAL_DELAY_SECONDS = 30;

    /**
     * Retry delays after pipeline failures: 1 min, 5 min, 15 min, then hourly forever.
     */
    private static final long[] BACKOFF_SECONDS = {60, 300, 900, 3600};

    private final Path dataDir;
    private final Path cacheDir;
    private final BiPredicate<Path, Path> onDataUpdated;
    private final ScheduledExecutorService scheduler;
    private final BinaryDataCompiler compiler;
    private final long checkIntervalSeconds;

    private volatile boolean running = false;
    private volatile boolean compileInProgress = false;
    private volatile boolean forceNextCheck = false;
    private volatile Runnable onNextFailure;
    private volatile Consumer<String> progressCallback;
    private int retryAttempt = 0;
    private ScheduledFuture<?> pendingRetry;

    public RuntimeUpdateService(Path dataDir, Path cacheDir,
                                BiPredicate<Path, Path> onDataUpdated,
                                long checkIntervalSeconds) {
        this.dataDir = dataDir;
        this.cacheDir = cacheDir;
        this.onDataUpdated = onDataUpdated;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SkyRecipes-UpdateService");
            t.setDaemon(true);
            return t;
        });
        this.compiler = new BinaryDataCompiler();
    }

    // ---- Public API ----

    /**
     * Normalize an HTTP ETag for comparison: strips the weak-validator prefix
     * ({@code W/}) and surrounding quotes, so weak and strong forms of the
     * same tag compare equal regardless of which request produced them.
     */
    public static String normalizeEtag(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("W/")) {
            s = s.substring(2);
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static boolean etagsMatch(String a, String b) {
        String na = normalizeEtag(a);
        String nb = normalizeEtag(b);
        return na != null && na.equals(nb);
    }

    /**
     * Start scheduled update checks. The interval was fixed at construction time
     * from {@code SkyRecipesConfig.dataRefreshIntervalHours}.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler.scheduleWithFixedDelay(
                this::performUpdateCheck,
                INITIAL_DELAY_SECONDS,
                checkIntervalSeconds,
                TimeUnit.SECONDS
        );
        LOGGER.info("Update service started. Checking every {} h.", checkIntervalSeconds / 3600L);
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
     * Force an immediate update check on the scheduler thread. Cancels any pending backoff retry.
     */
    public void checkNow() {
        cancelPendingRetry();
        scheduler.execute(this::performUpdateCheck);
    }

    /**
     * Force a complete rebuild regardless of ETag: deletes the cached ETag file so
     * the next check always re-downloads fresh data, then runs the pipeline immediately.
     * Progress messages are delivered via {@code onProgress} (scheduler thread; callers
     * must dispatch to the render thread themselves).
     * {@code onFailure} fires once on the scheduler thread if any pipeline stage fails.
     */
    public void forceRefreshNow(Consumer<String> onProgress, Runnable onFailure) {
        cancelPendingRetry();
        this.progressCallback = onProgress;
        this.onNextFailure = onFailure;
        try {
            Files.deleteIfExists(cacheDir.resolve("neu-repo.etag"));
        } catch (IOException e) {
            LOGGER.warn("Could not clear ETag cache for forced refresh", e);
        }
        forceNextCheck = true;
        scheduler.execute(this::performUpdateCheck);
    }

    private void notifyProgress(String message) {
        Consumer<String> cb = progressCallback;
        if (cb != null) cb.accept(message);
    }

    private void fireNextFailure() {
        progressCallback = null;
        Runnable r = onNextFailure;
        onNextFailure = null;
        if (r != null) r.run();
    }

    /**
     * Schedule a one-shot retry of the update pipeline with escalating backoff.
     * Each failed attempt advances to the next delay tier; the last tier repeats forever.
     * A successful pipeline run ({@link #onPipelineSuccess()}) resets the tier.
     */
    public synchronized void scheduleRetry() {
        if (pendingRetry != null && !pendingRetry.isDone()) {
            return;
        }
        long delay = BACKOFF_SECONDS[Math.min(retryAttempt, BACKOFF_SECONDS.length - 1)];
        retryAttempt++;
        LOGGER.warn("Data pipeline failed — retry #{} scheduled in {} s", retryAttempt, delay);
        pendingRetry = scheduler.schedule(this::performUpdateCheck, delay, TimeUnit.SECONDS);
        PipelineStatus.recordNextRetry(System.currentTimeMillis() + delay * 1000L);
    }

    /**
     * Cancel a pending backoff retry (used by manual refresh, which runs immediately instead).
     */
    public synchronized void cancelPendingRetry() {
        if (pendingRetry != null) {
            pendingRetry.cancel(false);
            pendingRetry = null;
        }
        PipelineStatus.recordNextRetry(0L);
    }

    /**
     * Reset the backoff tier after a successful pipeline run.
     */
    public synchronized void onPipelineSuccess() {
        retryAttempt = 0;
        cancelPendingRetry();
    }

    /**
     * Epoch millis of the next scheduled retry, or 0 if none is pending.
     */
    public synchronized long getNextRetryTime() {
        if (pendingRetry == null || pendingRetry.isDone()) {
            return 0L;
        }
        return System.currentTimeMillis() + pendingRetry.getDelay(TimeUnit.MILLISECONDS);
    }

    /**
     * Check remote ETag against local metadata asynchronously.
     * Calls {@code onComplete} with {@code true} if local data is up-to-date (warm start);
     * {@code false} if a cold start (download + compile) is required.
     *
     * <p>If the network is unreachable, returns {@code true} when local data
     * exists so the user is never left with no recipes.</p>
     */
    public void checkEtagAsync(Consumer<Boolean> onComplete) {
        scheduler.execute(() -> {
            try {
                long checkStart = System.currentTimeMillis();
                String currentEtag = readCurrentEtag();
                String remoteEtag = fetchRemoteEtag();
                PipelineStatus.recordCheckTime(System.currentTimeMillis());
                PipelineStatus.recordStageDuration("check", System.currentTimeMillis() - checkStart);

                if (remoteEtag == null) {
                    LOGGER.warn("Could not fetch remote ETag (network unavailable). Using local data if present.");
                    onComplete.accept(currentEtag != null);
                    return;
                }

                if (currentEtag == null) {
                    LOGGER.info("No local metadata found — cold start required.");
                    onComplete.accept(false);
                    return;
                }

                boolean matches = etagsMatch(remoteEtag, currentEtag);
                if (matches) {
                    LOGGER.info("Remote ETag matches local ({}). Warm start permitted.", remoteEtag);
                } else {
                    LOGGER.info("Remote ETag changed (remote: {}, local: {}). Cold start required.",
                            remoteEtag, currentEtag);
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

        boolean forced = forceNextCheck;
        forceNextCheck = false;

        try {
            long checkStart = System.currentTimeMillis();
            String currentEtag = readCurrentEtag();
            String remoteEtag = fetchRemoteEtag();
            PipelineStatus.recordCheckTime(System.currentTimeMillis());
            PipelineStatus.recordStageDuration("check", System.currentTimeMillis() - checkStart);

            if (remoteEtag == null) {
                if (currentEtag != null) {
                    // Offline with usable local data: benign, wait for the next scheduled check.
                    LOGGER.warn("Could not fetch remote ETag. Skipping update check.");
                    if (forced) fireNextFailure();
                } else {
                    LOGGER.warn("Could not fetch remote ETag and no local data exists. Retrying with backoff.");
                    PipelineStatus.recordError("check",
                            "Could not reach GitHub to download SkyBlock data", null);
                    fireNextFailure();
                    scheduleRetry();
                }
                return;
            }

            if (!forced && etagsMatch(remoteEtag, currentEtag)) {
                LOGGER.debug("Data is up to date (ETag match).");
                onPipelineSuccess();
                return;
            }

            if (forced) {
                LOGGER.info("Forced full refresh — re-downloading and recompiling NEU data.");
            } else {
                LOGGER.info("New NEU data detected (ETag changed). Starting update...");
            }
            compileInProgress = true;

            try {
                BinaryDataCompiler.CompileResult result = downloadAndCompile(remoteEtag);
                if (result == null) {
                    LOGGER.error("Update compile failed. Keeping old data.");
                    PipelineStatus.recordError("compile", "NEU data compile failed", null);
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }

                if (!validateCompiledFile(result.outputPath())) {
                    LOGGER.error("Validation failed for new binary. Keeping old data.");
                    PipelineStatus.recordError("validate", "Downloaded data failed validation", null);
                    cleanupTempFiles();
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }

                if (!atomicSwap(result.outputPath(), result.metaPath())) {
                    LOGGER.error("Atomic swap failed. Keeping old data.");
                    PipelineStatus.recordError("swap", "Could not replace the data file on disk", null);
                    cleanupTempFiles();
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }

                notifyProgress("§7SkyRecipes: Loading compiled data...");
                if (!onDataUpdated.test(dataDir.resolve("skyrecipes_data.mpk"),
                        dataDir.resolve("skyrecipes_data.meta.json"))) {
                    LOGGER.error("New binary swapped in but reload failed. Retrying with backoff.");
                    PipelineStatus.recordError("load", "New data file could not be loaded", null);
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }

                LOGGER.info("Update complete. Swapped to new binary with {} items.", result.itemCount());
                progressCallback = null;
                onPipelineSuccess();

            } finally {
                compileInProgress = false;
            }

        } catch (Exception e) {
            LOGGER.error("Update check failed", e);
            PipelineStatus.recordError("update",
                    "Data update failed: " + e.getMessage(), e);
            compileInProgress = false;
            fireNextFailure();
            scheduleRetry();
        }
    }

    private BinaryDataCompiler.CompileResult downloadAndCompile(String expectedEtag) throws Exception {
        Files.createDirectories(cacheDir);
        Files.createDirectories(dataDir);

        Path zipFile = cacheDir.resolve("neu-repo.zip");
        Path etagFile = cacheDir.resolve("neu-repo.etag");

        String existingEtag = null;
        if (Files.exists(etagFile)) {
            existingEtag = Files.readString(etagFile).trim();
        }

        PipelineStatus.transition(PipelineStatus.State.DOWNLOADING);
        notifyProgress("§7SkyRecipes: Downloading latest SkyBlock data from GitHub...");
        long downloadStart = System.currentTimeMillis();
        BinaryDataCompiler.DownloadResult download = compiler.downloadNeuRepo(zipFile, etagFile, existingEtag);
        PipelineStatus.recordStageDuration("download", System.currentTimeMillis() - downloadStart);
        boolean downloaded = switch (download) {
            case DOWNLOADED -> {
                LOGGER.info("Downloaded fresh NEU repo.");
                yield true;
            }
            case CACHE_HIT -> {
                LOGGER.info("Using cached NEU repo.");
                yield false;
            }
            case FAILED_NO_CACHE -> throw new IOException(
                    "NEU repo download failed and no cached copy exists");
        };

        String actualEtag = downloaded ? Files.readString(etagFile).trim() : existingEtag;
        if (actualEtag == null) actualEtag = "";

        String etagForMeta = expectedEtag != null ? expectedEtag : actualEtag;

        Path tempMpk = dataDir.resolve("skyrecipes_data_new.mpk");
        Path tempMeta = dataDir.resolve("skyrecipes_data_new.meta.json");

        PipelineStatus.transition(PipelineStatus.State.COMPILING);
        notifyProgress("§7SkyRecipes: Compiling item and recipe data...");
        long compileStart = System.currentTimeMillis();
        BinaryDataCompiler.CompileResult result =
                compiler.compileToPath(zipFile, tempMpk, tempMeta, etagForMeta, null);
        PipelineStatus.recordStageDuration("compile", System.currentTimeMillis() - compileStart);
        return result;
    }

    private boolean validateCompiledFile(Path mpkPath) {
        // A full load exercises every check the runtime performs: header,
        // schema, CRC32C, embedded metadata, and complete deserialization.
        BinaryDataLoader validator = new BinaryDataLoader();
        boolean loaded = validator.load(mpkPath);
        validator.close();

        if (!loaded) {
            LOGGER.error("Validation failed: could not load binary.");
            return false;
        }

        LOGGER.debug("Validation passed for new binary.");
        return true;
    }

    private boolean atomicSwap(Path newMpk, Path newMeta) {
        Path finalMpk = dataDir.resolve("skyrecipes_data.mpk");
        Path finalMeta = dataDir.resolve("skyrecipes_data.meta.json");

        try {
            try {
                Files.move(newMpk, finalMpk, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Atomic move failed, falling back to regular replace", e);
                Files.move(newMpk, finalMpk, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Swap of new binary failed", e);
            return false;
        }

        // Sidecar is write-only diagnostics; its move is best-effort.
        try {
            Files.move(newMeta, finalMeta, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.debug("Failed to move metadata sidecar", e);
        }
        return true;
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
        Path mpkPath = dataDir.resolve("skyrecipes_data.mpk");
        try {
            if (Files.exists(mpkPath)) {
                return BinaryDataLoader.readEmbeddedMetadata(mpkPath).etag();
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to read current ETag from binary", e);
        }
        return null;
    }

    private String fetchRemoteEtag() {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                URL url = new URL(NEU_REPO_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);

                try {
                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        LOGGER.warn("HEAD request returned {}", responseCode);
                        return null;
                    }
                    return conn.getHeaderField("ETag");
                } finally {
                    conn.disconnect();
                }

            } catch (IOException e) {
                lastFailure = e;
                if (attempt == 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        LOGGER.warn("Failed to fetch remote ETag", lastFailure);
        return null;
    }
}