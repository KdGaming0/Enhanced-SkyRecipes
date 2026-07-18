package com.github.kdgaming0.skyrecipes.core.fusion;

import com.github.kdgaming0.skyrecipes.core.util.HttpFetch;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

/**
 * Fetches the shard fusion dataset from the SkyShards repository.
 *
 * <p>Data source: <a href="https://github.com/Campionnn/SkyShards">SkyShards</a> by
 * Campionn — {@code public/fusion-data.json} via GitHub raw. Fetched at runtime and
 * cached on disk ({@link ShardFusionMpkCache}); not redistributed inside the mod jar.</p>
 *
 * <p>Uses a conditional GET: the ETag of the last successful fetch is sent as
 * {@code If-None-Match}, so an unchanged dataset costs one tiny 304 response
 * instead of a ~2 MB download — the same update-detection approach the NEU repo
 * pipeline uses.</p>
 */
public final class ShardFusionFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardFusionFetcher.class);
    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/Campionnn/SkyShards/master/public/fusion-data.json";

    /**
     * Outcome of a conditional fetch.
     *
     * @param body        raw response bytes; {@code null} when {@code notModified}
     * @param etag        the ETag header verbatim (kept verbatim so it round-trips
     *                    exactly in the next {@code If-None-Match}); empty if absent
     * @param notModified {@code true} on HTTP 304 — the cached data is current
     */
    public record FetchResult(byte @Nullable [] body, String etag, boolean notModified) {
    }

    private ShardFusionFetcher() {
    }

    /**
     * Downloads the raw fusion JSON, conditionally when a previous ETag is known.
     *
     * @param previousEtag ETag of the currently cached data, or {@code null}/empty
     *                     to force an unconditional fetch
     * @return fetch result, or {@code null} on any network/HTTP failure
     */
    @Nullable
    public static FetchResult fetch(HttpClient http, @Nullable String previousEtag) {
        try {
            HttpResponse<byte[]> response = http.send(HttpFetch.get(ENDPOINT, previousEtag).build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 304) {
                return new FetchResult(null, previousEtag, true);
            }
            if (!HttpFetch.isOk(response.statusCode())) {
                LOGGER.warn("Shard fusion data fetch returned HTTP {}", response.statusCode());
                return null;
            }
            String etag = response.headers().firstValue("ETag").orElse("");
            return new FetchResult(response.body(), etag, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch shard fusion data", e);
            return null;
        }
    }

    /**
     * Parses raw fusion JSON bytes into a snapshot.
     *
     * @return parsed snapshot, or {@code null} on parse failure
     */
    @Nullable
    public static ShardFusionData parse(byte[] jsonBytes) {
        return ShardFusionParser.parse(jsonBytes);
    }
}
