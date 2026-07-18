package com.github.kdgaming0.skyrecipes.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pipeline-wide, user-facing status of the data pipeline.
 *
 * <p>This is a reporting surface, not a controller: the data layer
 * ({@link RuntimeDataManager}, {@link RuntimeUpdateService}) and the RRV
 * plugin own their own logic state and report transitions, errors, counts,
 * and timings here. {@code /skyrecipes status} and the world-join chat
 * notice render {@link #snapshot()}. The one state read that drives logic is
 * {@link #isFailed()}, which makes the item panel explain the empty list when
 * no data is coming.</p>
 *
 * <p>Headline-state rules: once the pipeline has reached {@code READY} (or
 * {@code DEGRADED}), background refresh work never regresses the headline —
 * it is tracked as {@code refreshInProgress} instead, so partial reload
 * progress is never the reported state. An error while live data exists
 * yields {@code DEGRADED} (old data keeps serving); an error with nothing
 * loaded yields {@code FAILED}.</p>
 */
public final class PipelineStatus {

    private static final Map<String, Long> stageDurationsMs = new LinkedHashMap<>();
    private static State state = State.STARTING;
    private static boolean refreshInProgress = false;
    private static boolean providerOnlyMode = false;
    private static boolean errorNotificationPending = false;

    private static String lastErrorStage = null;
    private static String lastErrorMessage = null;
    private static String lastErrorType = null;
    private static long lastErrorTime = 0L;

    private static long dataBuildTimestamp = 0L;
    private static String etag = null;
    private static int itemCount = 0;
    private static int recipeCount = 0;
    private static int stackCount = 0;
    private static int recipeFailures = 0;
    private static int stackFailures = 0;
    private static int injectedRecipes = 0;
    private static int skippedRecipes = 0;

    private static long lastCheckTime = 0L;
    private static long nextRetryTime = 0L;

    private PipelineStatus() {
    }

    public static synchronized void transition(State target) {
        if (state.hasLiveData() && target.isWorking()) {
            refreshInProgress = true;
            return;
        }
        if (target == State.READY) {
            refreshInProgress = false;
            errorNotificationPending = false;
        }
        state = target;
    }

    /**
     * Record a pipeline error. The headline state becomes {@code DEGRADED}
     * if live data exists, otherwise {@code FAILED}, and a one-shot chat
     * notification is armed (see {@link #consumeErrorNotification()}).
     */
    public static synchronized void recordError(String stage, String userMessage, Throwable t) {
        lastErrorStage = stage;
        lastErrorMessage = userMessage;
        lastErrorType = t != null ? t.getClass().getSimpleName() : null;
        lastErrorTime = System.currentTimeMillis();
        errorNotificationPending = true;
        refreshInProgress = false;
        state = state.hasLiveData() ? State.DEGRADED : State.FAILED;
    }

    public static synchronized void recordStageDuration(String stage, long ms) {
        stageDurationsMs.put(stage, ms);
    }

    public static synchronized void recordDataInfo(long buildTimestamp, String dataEtag, int items) {
        dataBuildTimestamp = buildTimestamp;
        etag = dataEtag;
        itemCount = items;
    }

    public static synchronized void recordGenerationCounts(int recipes, int stacks,
                                                           int recipeFails, int stackFails) {
        recipeCount = recipes;
        stackCount = stacks;
        recipeFailures = recipeFails;
        stackFailures = stackFails;
    }

    public static synchronized void recordInjectionResult(int injected, int skipped, boolean providerOnly) {
        injectedRecipes = injected;
        skippedRecipes = skipped;
        providerOnlyMode = providerOnly;
    }

    public static synchronized void recordCheckTime(long epochMs) {
        lastCheckTime = epochMs;
    }

    /**
     * Epoch millis of the next automatic retry; 0 clears it.
     */
    public static synchronized void recordNextRetry(long epochMs) {
        nextRetryTime = epochMs;
    }

    /**
     * One-shot gate for the world-join chat notice: returns true exactly once
     * per error episode. Re-armed by the next {@link #recordError}; cleared
     * when the pipeline reaches {@code READY}.
     */
    public static synchronized boolean consumeErrorNotification() {
        if (!errorNotificationPending) {
            return false;
        }
        errorNotificationPending = false;
        return true;
    }

    /**
     * True when the pipeline has no live data and is not working toward it —
     * e.g. the first download failed offline. Briefly false again while a
     * scheduled retry attempt runs.
     */
    public static synchronized boolean isFailed() {
        return state == State.FAILED;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(state, refreshInProgress, providerOnlyMode,
                lastErrorStage, lastErrorMessage, lastErrorType, lastErrorTime,
                dataBuildTimestamp, etag, itemCount,
                recipeCount, stackCount, recipeFailures, stackFailures,
                injectedRecipes, skippedRecipes,
                lastCheckTime, nextRetryTime,
                new LinkedHashMap<>(stageDurationsMs));
    }

    public enum State {
        STARTING, DOWNLOADING, COMPILING, LOADING, GENERATING, INJECTING,
        READY, DEGRADED, FAILED;

        boolean isWorking() {
            return this == DOWNLOADING || this == COMPILING || this == LOADING
                    || this == GENERATING || this == INJECTING;
        }

        boolean hasLiveData() {
            return this == READY || this == DEGRADED;
        }
    }

    public record Snapshot(State state, boolean refreshInProgress, boolean providerOnlyMode,
                           String lastErrorStage, String lastErrorMessage, String lastErrorType,
                           long lastErrorTime,
                           long dataBuildTimestamp, String etag, int itemCount,
                           int recipeCount, int stackCount, int recipeFailures, int stackFailures,
                           int injectedRecipes, int skippedRecipes,
                           long lastCheckTime, long nextRetryTime,
                           Map<String, Long> stageDurationsMs) {
    }
}
