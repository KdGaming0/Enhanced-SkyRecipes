package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.security.cert.CertificateException;
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

    public static final String NEU_REPO_URL =
            "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";

    private static final long INITIAL_DELAY_SECONDS = 30;

    /**
     * Retry delays after pipeline failures: 1 min, 5 min, 15 min, then hourly forever.
     */
    private static final long[] BACKOFF_SECONDS = {60, 300, 900, 3600};

    private final CacheLayout layout;
    private final BiPredicate<BinaryDataLoader, Path> onDataPublished;
    private final ScheduledExecutorService scheduler;
    private final BinaryDataCompiler compiler;
    private final long checkIntervalSeconds;

    private volatile boolean running = false;
    private volatile boolean compileInProgress = false;
    private volatile boolean forceNextCheck = false;
    private volatile Runnable onNextFailure;
    private volatile Consumer<String> progressCallback;
    private volatile Throwable lastEtagFetchError;
    private int retryAttempt = 0;
    private ScheduledFuture<?> pendingRetry;

    public RuntimeUpdateService(CacheLayout layout,
                                BiPredicate<BinaryDataLoader, Path> onDataPublished,
                                long checkIntervalSeconds) {
        this.layout = layout;
        this.onDataPublished = onDataPublished;
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
     * from {@code SkyRecipesConfig.dataRefreshIntervalMinutes}.
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
    @SuppressWarnings("unused")
    public void checkNow() {
        cancelPendingRetry();
        scheduler.execute(this::performUpdateCheck);
    }

    /**
     * Force a complete rebuild as if starting with an empty cache: skips the ETag
     * check entirely and re-downloads, recompiles, and reloads everything. Live
     * data is never touched until the new build has fully loaded, so a failed
     * refresh keeps the current items serving.
     * Progress messages are delivered via {@code onProgress} (scheduler thread; callers
     * must dispatch to the render thread themselves).
     * {@code onFailure} fires once on the scheduler thread if any pipeline stage fails.
     */
    public void forceRefreshNow(Consumer<String> onProgress, Runnable onFailure) {
        cancelPendingRetry();
        this.progressCallback = onProgress;
        this.onNextFailure = onFailure;
        forceNextCheck = true;
        scheduler.execute(this::performUpdateCheck);
    }

    /**
     * Compile and load a manually supplied NEU repo ZIP — the last-resort path when
     * the mod cannot reach GitHub (user downloads the ZIP in a browser or gets it
     * from a friend, drops it in {@code skyrecipes/import/}). Runs the same
     * compile → validate → swap → load sequence as a download, minus the network.
     *
     * <p>The recorded ETag ({@code manual-import-<time>}) never matches the remote,
     * so the next successful online check replaces imported data automatically.
     * The ZIP itself is kept — it is the user's file and their only recovery source.</p>
     */
    public void importFromZip(Path zipFile, Consumer<String> onProgress, Runnable onFailure) {
        cancelPendingRetry();
        this.progressCallback = onProgress;
        this.onNextFailure = onFailure;
        scheduler.execute(() -> {
            if (compileInProgress) {
                LOGGER.warn("Import skipped: another compile is already in progress.");
                fireNextFailure();
                return;
            }
            compileInProgress = true;
            try {
                String importEtag = "manual-import-" + System.currentTimeMillis();
                PipelineStatus.transition(PipelineStatus.State.COMPILING);
                notifyProgress("§7SkyRecipes: Compiling imported data...");
                long compileStart = System.currentTimeMillis();
                BinaryDataCompiler.CompileResult result = compiler.compileToPath(
                        zipFile, layout.binaryTempFile(), layout.binaryTempMetaFile(), importEtag, null);
                PipelineStatus.recordStageDuration("compile", System.currentTimeMillis() - compileStart);

                if (result == null) {
                    LOGGER.error("Imported ZIP could not be compiled: {}", zipFile);
                    PipelineStatus.recordError("import", "The imported ZIP did not contain usable SkyBlock data", null);
                    cleanupTempFiles();
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }
                if (!loadSwapPublish(result)) {
                    fireNextFailure();
                    scheduleRetry();
                    return;
                }
                LOGGER.info("Manual import complete: {} items from {}", result.itemCount(), zipFile.getFileName());
                progressCallback = null;
                onNextFailure = null;
                onPipelineSuccess();

            } catch (Exception e) {
                LOGGER.error("Manual import failed for {}", zipFile, e);
                PipelineStatus.recordError("import", "The imported ZIP could not be compiled: " + e.getMessage(), e);
                cleanupTempFiles();
                fireNextFailure();
                scheduleRetry();
            } finally {
                compileInProgress = false;
            }
        });
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
    @SuppressWarnings("unused")
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
            // The decision is computed inside the try but delivered outside it,
            // so a throw from the consumer itself can never re-trigger the catch
            // and fire onComplete a second time.
            boolean decision;
            try {
                long checkStart = System.currentTimeMillis();
                String currentEtag = readCurrentEtag();
                String remoteEtag = fetchRemoteEtag();
                PipelineStatus.recordCheckTime(System.currentTimeMillis());
                PipelineStatus.recordStageDuration("check", System.currentTimeMillis() - checkStart);

                if (remoteEtag == null) {
                    LOGGER.warn("Could not fetch remote ETag (network unavailable). Using local data if present.");
                    decision = currentEtag != null;
                } else if (currentEtag == null) {
                    LOGGER.info("No local metadata found — cold start required.");
                    decision = false;
                } else {
                    boolean matches = etagsMatch(remoteEtag, currentEtag);
                    if (matches) {
                        LOGGER.info("Remote ETag matches local ({}). Warm start permitted.", remoteEtag);
                    } else {
                        LOGGER.info("Remote ETag changed (remote: {}, local: {}). Cold start required.",
                                remoteEtag, currentEtag);
                    }
                    decision = matches;
                }
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError vme) throw vme;
                LOGGER.error("ETag check failed unexpectedly", e);
                decision = false;
            }
            onComplete.accept(decision);
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
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError vme) throw vme;
                // Record before the callback so the pipeline is already FAILED when
                // downstream fallbacks (e.g. the overlay mixins) check the state.
                if (isNetworkError(e)) {
                    String reason = describeNetworkError(e);
                    LOGGER.warn("Cold-start compile failed: {}", reason);
                    LOGGER.debug("Cold-start compile failure detail", e);
                    PipelineStatus.recordError("download", reason, e);
                } else {
                    LOGGER.error("Cold-start compile failed", e);
                    PipelineStatus.recordError("compile", "Data compile failed: " + e.getMessage(), e);
                }
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
            String remoteEtag = null;
            if (forced) {
                // A forced refresh behaves like a clean start: no ETag shortcut, no
                // HEAD probe — straight to a fresh download (its GET carries the ETag).
                LOGGER.info("Forced full refresh — re-downloading and recompiling NEU data.");
                PipelineStatus.recordCheckTime(System.currentTimeMillis());
            } else {
                long checkStart = System.currentTimeMillis();
                String currentEtag = readCurrentEtag();
                remoteEtag = fetchRemoteEtag();
                PipelineStatus.recordCheckTime(System.currentTimeMillis());
                PipelineStatus.recordStageDuration("check", System.currentTimeMillis() - checkStart);

                if (remoteEtag == null) {
                    if (currentEtag != null) {
                        // Offline with usable local data: benign, wait for the next scheduled check.
                        LOGGER.warn("Could not fetch remote ETag. Skipping update check.");
                    } else {
                        String reason = describeNetworkError(lastEtagFetchError);
                        LOGGER.warn("Could not fetch remote ETag and no local data exists. "
                                + "Retrying with backoff. ({})", reason);
                        PipelineStatus.recordError("check", reason, lastEtagFetchError);
                        fireNextFailure();
                        scheduleRetry();
                    }
                    return;
                }

                if (etagsMatch(remoteEtag, currentEtag)) {
                    LOGGER.debug("Data is up to date (ETag match).");
                    onPipelineSuccess();
                    return;
                }
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

                if (!loadSwapPublish(result)) {
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

        } catch (Throwable e) {
            // A Throwable escaping a scheduleWithFixedDelay task silently cancels
            // every future execution — including the backoff retries scheduled on
            // this same executor. Only genuinely fatal VM errors may escape.
            if (e instanceof VirtualMachineError vme) {
                LOGGER.error("Fatal error in update check — update service halted", vme);
                throw vme;
            }
            if (isNetworkError(e)) {
                String reason = describeNetworkError(e);
                LOGGER.warn("Update check failed: {}", reason);
                LOGGER.debug("Update check failure detail", e);
                PipelineStatus.recordError("update", reason, e);
            } else {
                LOGGER.error("Update check failed", e);
                PipelineStatus.recordError("update", "Data update failed: " + e.getMessage(), e);
            }
            compileInProgress = false;
            fireNextFailure();
            scheduleRetry();
        }
    }

    private BinaryDataCompiler.CompileResult downloadAndCompile(String expectedEtag) throws Exception {
        layout.createDirectories();

        Path zipFile = layout.neuRepoZip();

        // The enclosing check already compared ETags and decided a download is needed,
        // so this streams a single GET (no redundant HEAD) into a transient ZIP.
        PipelineStatus.transition(PipelineStatus.State.DOWNLOADING);
        notifyProgress("§7SkyRecipes: Downloading latest SkyBlock data from GitHub...");
        long downloadStart = System.currentTimeMillis();
        String downloadedEtag = compiler.downloadNeuRepoStreaming(zipFile);
        PipelineStatus.recordStageDuration("download", System.currentTimeMillis() - downloadStart);
        LOGGER.info("Downloaded fresh NEU repo.");

        // The ETag is embedded in the compiled binary's metadata — the single
        // source of truth read back by readCurrentEtag(); no sidecar file.
        String etagForMeta = expectedEtag != null ? expectedEtag : downloadedEtag;
        if (etagForMeta == null) etagForMeta = "";

        Path tempMpk = layout.binaryTempFile();
        Path tempMeta = layout.binaryTempMetaFile();

        PipelineStatus.transition(PipelineStatus.State.COMPILING);
        notifyProgress("§7SkyRecipes: Compiling item and recipe data...");
        long compileStart = System.currentTimeMillis();
        try {
            BinaryDataCompiler.CompileResult result =
                    compiler.compileToPath(zipFile, tempMpk, tempMeta, etagForMeta, null);
            PipelineStatus.recordStageDuration("compile", System.currentTimeMillis() - compileStart);
            return result;
        } finally {
            // Stream-then-discard: the multi-megabyte ZIP is never kept on disk.
            try {
                Files.deleteIfExists(zipFile);
            } catch (IOException e) {
                LOGGER.debug("Could not delete transient NEU repo ZIP", e);
            }
        }
    }

    /**
     * Load the freshly compiled temp binary (the load doubles as validation),
     * swap it into the final location, then publish the loaded registries to
     * the data manager in one step. The previously live data is not touched
     * until publication, so a failure at any stage keeps the old data serving —
     * and the binary is parsed exactly once instead of validate-then-reload.
     */
    private boolean loadSwapPublish(BinaryDataCompiler.CompileResult result) {
        PipelineStatus.transition(PipelineStatus.State.LOADING);
        notifyProgress("§7SkyRecipes: Loading compiled data...");

        BinaryDataLoader newLoader = new BinaryDataLoader();
        long loadStart = System.currentTimeMillis();
        boolean loaded = newLoader.load(result.outputPath());
        PipelineStatus.recordStageDuration("load", System.currentTimeMillis() - loadStart);
        if (!loaded) {
            LOGGER.error("Freshly compiled binary failed to load. Keeping old data.");
            PipelineStatus.recordError("validate", "Compiled data failed validation", null);
            cleanupTempFiles();
            return false;
        }

        if (!atomicSwap(result.outputPath(), result.metaPath())) {
            LOGGER.error("Atomic swap failed. Keeping old data.");
            PipelineStatus.recordError("swap", "Could not replace the data file on disk", null);
            newLoader.close();
            cleanupTempFiles();
            return false;
        }

        if (!onDataPublished.test(newLoader, layout.binaryFile())) {
            LOGGER.error("New binary loaded but could not be published. Retrying with backoff.");
            PipelineStatus.recordError("load", "New data could not be published", null);
            newLoader.close();
            return false;
        }
        return true;
    }

    private boolean atomicSwap(Path newMpk, Path newMeta) {
        Path finalMpk = layout.binaryFile();
        Path finalMeta = layout.binaryMetaFile();

        try {
            AtomicFiles.move(newMpk, finalMpk);
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
            Files.deleteIfExists(layout.binaryTempFile());
            Files.deleteIfExists(layout.binaryTempMetaFile());
        } catch (IOException e) {
            LOGGER.debug("Failed to clean up temp files", e);
        }
    }

    public String readCurrentEtag() {
        Path mpkPath = layout.binaryFile();
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
                URL url = URI.create(NEU_REPO_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);

                try {
                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        LOGGER.warn("HEAD request returned {}", responseCode);
                        // GitHub answered, so "check your internet" would mislead —
                        // record the status so describeNetworkError reports it.
                        lastEtagFetchError = new IOException("GitHub responded with HTTP " + responseCode
                                + (responseCode == 429 || responseCode == 403
                                ? " (rate limited — usually resolves on its own; not a connection problem)"
                                : ""));
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
        lastEtagFetchError = lastFailure;
        LOGGER.warn("Failed to fetch remote ETag: {}", describeNetworkError(lastFailure));
        LOGGER.debug("Remote ETag fetch failure detail", lastFailure);
        return null;
    }

    /**
     * Classify a network failure into one concise, plain-English line for logs and
     * {@code /skyrecipes status}. Falls back to a generic message for unrecognized causes.
     */
    private static String describeNetworkError(Throwable t) {
        String specific = classifyNetworkError(t);
        String base;
        if (specific != null) {
            base = specific;
        } else {
            String msg = t != null ? t.getMessage() : null;
            base = msg != null ? "Couldn't reach GitHub: " + msg : "Couldn't reach GitHub.";
        }
        return base + " You can open this download link in a browser to check whether your connection can reach GitHub: " + NEU_REPO_URL;
    }

    /**
     * Walk the cause chain for a recognized network failure type. TLS/cert failures
     * arrive wrapped (an {@link SSLException} caused by a {@link CertificateException}),
     * so the whole chain is inspected. Returns {@code null} if nothing matches.
     */
    private static String classifyNetworkError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SSLException || c instanceof CertificateException) {
                return "GitHub's secure connection was blocked or altered (TLS) — usually a "
                        + "firewall, VPN, antivirus HTTPS-scanning, or ISP/DNS interference, "
                        + "not a SkyRecipes problem.";
            }
            if (c instanceof UnknownHostException) {
                return "Couldn't resolve GitHub (DNS) — check your internet connection or DNS.";
            }
            if (c instanceof SocketTimeoutException) {
                return "Connection to GitHub timed out — check your internet connection.";
            }
            if (c instanceof ConnectException) {
                return "Couldn't connect to GitHub — check your internet connection.";
            }
        }
        return null;
    }

    private static boolean isNetworkError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}