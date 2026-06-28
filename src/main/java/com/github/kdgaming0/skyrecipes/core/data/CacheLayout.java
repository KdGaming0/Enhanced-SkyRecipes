package com.github.kdgaming0.skyrecipes.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Single source of truth for every path SkyRecipes reads or writes on disk.
 *
 * <p>All data lives under one root, {@code gameDir/skyrecipes}: durable outputs in
 * {@code data/} (compiled binary, metadata sidecar, Hypixel snapshot) and
 * regenerable artifacts in {@code cache/} (the NEU ETag plus transient download/compile
 * temporaries).
 */
public final class CacheLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheLayout.class);

    private static final String BINARY_NAME = "skyrecipes_data.mpk";
    private static final String META_NAME = "skyrecipes_data.meta.json";
    private static final String BINARY_TEMP_NAME = "skyrecipes_data_new.mpk";
    private static final String META_TEMP_NAME = "skyrecipes_data_new.meta.json";
    private static final String HYPIXEL_NAME = "hypixel_items.json";
    private static final String NEU_ETAG_NAME = "neu-repo.etag";
    private static final String NEU_ZIP_NAME = "neu-repo.zip";

    private final Path gameDir;
    private final Path root;
    private final Path dataDir;
    private final Path cacheDir;

    public CacheLayout(Path gameDir) {
        this.gameDir = gameDir;
        this.root = gameDir.resolve("skyrecipes");
        this.dataDir = root.resolve("data");
        this.cacheDir = root.resolve("cache");
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Path binaryFile() {
        return dataDir.resolve(BINARY_NAME);
    }

    public Path binaryMetaFile() {
        return dataDir.resolve(META_NAME);
    }

    public Path binaryTempFile() {
        return dataDir.resolve(BINARY_TEMP_NAME);
    }

    public Path binaryTempMetaFile() {
        return dataDir.resolve(META_TEMP_NAME);
    }

    public Path hypixelItemsFile() {
        return dataDir.resolve(HYPIXEL_NAME);
    }

    public Path neuEtagFile() {
        return cacheDir.resolve(NEU_ETAG_NAME);
    }

    /**
     * Transient landing path for the NEU repo ZIP. Compiled from, then deleted.
     */
    public Path neuRepoZip() {
        return cacheDir.resolve(NEU_ZIP_NAME);
    }

    public void createDirectories() throws IOException {
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);
    }

    /**
     * One-time move from the legacy split layout (binary in {@code gameDir/skyblockdata},
     * raw ZIP kept in {@code cache/}) into the current single-root layout. Best-effort:
     * a failure here only forces a fresh download, never blocks startup.
     */
    public void migrateLegacyLayout() {
        Path legacyData = gameDir.resolve("skyblockdata");
        try {
            createDirectories();
            moveIfAbsent(legacyData.resolve(BINARY_NAME), binaryFile());
            moveIfAbsent(legacyData.resolve(META_NAME), binaryMetaFile());
            moveIfAbsent(legacyData.resolve(HYPIXEL_NAME), hypixelItemsFile());
            // Under stream-then-discard the raw repo ZIP is no longer retained.
            Files.deleteIfExists(neuRepoZip());
            deleteIfEmpty(legacyData);
        } catch (IOException e) {
            LOGGER.warn("Cache layout migration failed (data will re-download if needed)", e);
        }
    }

    private static void moveIfAbsent(Path from, Path to) throws IOException {
        if (Files.exists(from) && !Files.exists(to)) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Migrated {} -> {}", from, to);
        }
    }

    private static void deleteIfEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir);
                LOGGER.info("Removed empty legacy directory {}", dir);
            }
        } catch (IOException e) {
            LOGGER.debug("Could not remove legacy directory {}", dir, e);
        }
    }
}
