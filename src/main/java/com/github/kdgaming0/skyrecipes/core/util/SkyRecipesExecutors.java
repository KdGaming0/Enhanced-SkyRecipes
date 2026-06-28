package com.github.kdgaming0.skyrecipes.core.util;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Shared, lifecycle-managed async resources for SkyRecipes.
 */
public final class SkyRecipesExecutors {

    private static final ForkJoinPool WORKER = createWorkerPool();
    private static volatile HttpClient httpClient;

    private SkyRecipesExecutors() {
    }

    public static ForkJoinPool worker() {
        return WORKER;
    }

    public static synchronized HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return httpClient;
    }

    public static synchronized void shutdown() {
        WORKER.shutdownNow();
        if (httpClient != null) {
            httpClient.shutdownNow();
            httpClient = null;
        }
    }

    private static ForkJoinPool createWorkerPool() {
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        return new ForkJoinPool(parallelism, pool -> {
            ForkJoinWorkerThread thread =
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("SkyRecipes-Worker-" + thread.getPoolIndex());
            thread.setDaemon(true);
            return thread;
        }, null, false);
    }
}
