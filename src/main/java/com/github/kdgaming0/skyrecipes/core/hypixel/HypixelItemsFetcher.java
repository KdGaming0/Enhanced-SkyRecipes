package com.github.kdgaming0.skyrecipes.core.hypixel;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches {@code /v2/resources/skyblock/items} from the Hypixel public API.
 *
 * <p>Uses streaming {@link JsonReader} to avoid materialising the entire multi-megabyte
 * response on the heap. The endpoint is public and requires no API key.</p>
 */
public final class HypixelItemsFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HypixelItemsFetcher.class);
    private static final String ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private HypixelItemsFetcher() {}

    /**
     * Fetches and parses the Hypixel items endpoint.
     *
     * @return parsed snapshot, or {@code null} on any network or parse failure
     */
    @Nullable
    public static HypixelItemsSnapshot fetch(HttpClient http) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("User-Agent", "SkyRecipes")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = http.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Hypixel items API returned HTTP {}", response.statusCode());
                return null;
            }

            try (InputStream body = response.body();
                 JsonReader reader = new JsonReader(
                         new InputStreamReader(body, StandardCharsets.UTF_8))) {
                return parseResponse(reader);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch Hypixel items API: {}", e.getMessage());
            return null;
        }
    }

    // ── Streaming parser ──────────────────────────────────────────────────────

    @Nullable
    private static HypixelItemsSnapshot parseResponse(JsonReader reader) throws IOException {
        Map<String, Map<String, Integer>> baseStats = new HashMap<>();
        Map<String, Map<String, int[]>> tieredStats = new HashMap<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("items".equals(name)) {
                parseItemsArray(reader, baseStats, tieredStats);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new HypixelItemsSnapshot(
                Collections.unmodifiableMap(baseStats),
                Collections.unmodifiableMap(tieredStats));
    }

    private static void parseItemsArray(JsonReader reader,
                                        Map<String, Map<String, Integer>> baseStats,
                                        Map<String, Map<String, int[]>> tieredStats)
            throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            parseItem(reader, baseStats, tieredStats);
        }
        reader.endArray();
    }

    private static void parseItem(JsonReader reader,
                                  Map<String, Map<String, Integer>> baseStats,
                                  Map<String, Map<String, int[]>> tieredStats)
            throws IOException {
        String id = null;
        Map<String, Integer> localBase = null;
        Map<String, int[]> localTiered = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "id" -> id = reader.nextString();
                case "stats" -> localBase = readBaseStats(reader);
                case "tiered_stats" -> localTiered = readTieredStats(reader);
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (id == null || id.isEmpty()) return;

        if (localBase != null && !localBase.isEmpty()) {
            baseStats.put(id, Collections.unmodifiableMap(localBase));
        }
        if (localTiered != null && !localTiered.isEmpty()) {
            tieredStats.put(id, Collections.unmodifiableMap(localTiered));
        }
    }

    private static Map<String, Integer> readBaseStats(JsonReader reader) throws IOException {
        Map<String, Integer> stats = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (reader.peek() == JsonToken.NUMBER) {
                try {
                    stats.put(key.toUpperCase(Locale.ROOT), reader.nextInt());
                } catch (NumberFormatException e) {
                    reader.skipValue();
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return stats.isEmpty() ? null : stats;
    }

    private static Map<String, int[]> readTieredStats(JsonReader reader) throws IOException {
        Map<String, int[]> stats = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            List<Integer> values = readIntArray(reader);
            if (values.size() >= 2) {
                int[] arr = new int[values.size()];
                for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
                stats.put(key, arr);
            }
        }
        reader.endObject();
        return stats.isEmpty() ? null : stats;
    }

    private static List<Integer> readIntArray(JsonReader reader) throws IOException {
        List<Integer> list = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.NUMBER) {
                try {
                    list.add(reader.nextInt());
                } catch (NumberFormatException e) {
                    reader.skipValue();
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endArray();
        return list;
    }
}
