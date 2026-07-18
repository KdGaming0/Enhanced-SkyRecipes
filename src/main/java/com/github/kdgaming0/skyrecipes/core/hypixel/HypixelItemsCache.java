package com.github.kdgaming0.skyrecipes.core.hypixel;

import com.github.kdgaming0.skyrecipes.core.util.AtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes the Hypixel items snapshot to disk.
 *
 * <p>Cache TTL is 24 hours. The cache is stored as JSON at
 * {@code [gameDir]/skyrecipes/data/hypixel_items.json}.</p>
 */
public final class HypixelItemsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(HypixelItemsCache.class);
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HypixelItemsCache() {
    }

    /**
     * Returns {@code true} when the cache file exists and is newer than the TTL.
     */
    public static boolean isFresh(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            return age < CACHE_TTL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the cached snapshot, or {@code null} on failure.
     */
    public static HypixelItemsSnapshot tryLoad(Path cacheFile) {
        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, HypixelItemsSnapshot.class);
        } catch (Exception e) {
            LOGGER.debug("Failed to load Hypixel items cache", e);
            return null;
        }
    }

    /**
     * Writes the snapshot atomically.
     */
    public static void save(Path cacheFile, HypixelItemsSnapshot data) {
        try {
            AtomicFiles.write(cacheFile, out -> {
                try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                    GSON.toJson(data, writer);
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to save Hypixel items cache", e);
        }
    }
}
