package com.github.kdgaming0.skyrecipes.core.util;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Shared building blocks for the mod's outbound HTTP GET fetches.
 *
 * <p>Centralises the request construction (User-Agent, timeout, optional
 * conditional {@code If-None-Match}) and the 2xx status check that were
 * duplicated between {@code HypixelItemsFetcher} and {@code ShardFusionFetcher}.
 * The shared {@code HttpClient} itself lives in {@code SkyRecipesExecutors}.</p>
 */
public final class HttpFetch {

    /** User-Agent sent on every request. */
    public static final String USER_AGENT = "SkyRecipes";

    /** Default per-request timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private HttpFetch() {
    }

    /**
     * A GET request builder pre-populated with the SkyRecipes User-Agent and the
     * default timeout. When {@code ifNoneMatch} is non-empty it is sent as an
     * {@code If-None-Match} header for a conditional (304-capable) fetch.
     */
    public static HttpRequest.Builder get(String endpoint, @Nullable String ifNoneMatch) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .timeout(DEFAULT_TIMEOUT)
                .GET();
        if (ifNoneMatch != null && !ifNoneMatch.isEmpty()) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        return builder;
    }

    /** {@code true} for any HTTP 2xx status code. */
    public static boolean isOk(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
