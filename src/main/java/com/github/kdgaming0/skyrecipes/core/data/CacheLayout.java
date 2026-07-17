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
 * {@code data/} (compiled binary, metadata sidecar, Hypixel snapshot), transient
 * download artifacts in {@code cache/}, and user-supplied ZIPs in {@code import/}.
 */
public final class CacheLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheLayout.class);

    private static final String BINARY_NAME = "skyrecipes_data.mpk";
    private static final String META_NAME = "skyrecipes_data.meta.json";
    private static final String BINARY_TEMP_NAME = "skyrecipes_data_new.mpk";
    private static final String META_TEMP_NAME = "skyrecipes_data_new.meta.json";
    private static final String HYPIXEL_NAME = "hypixel_items.json";
    private static final String SHARD_FUSIONS_NAME = "shard_fusions.mpk";
    private static final String NEU_ETAG_NAME = "neu-repo.etag";
    private static final String NEU_ZIP_NAME = "neu-repo.zip";

    private final Path gameDir;
    private final Path root;
    private final Path dataDir;
    private final Path cacheDir;
    private final Path importDir;

    public CacheLayout(Path gameDir) {
        this.gameDir = gameDir;
        this.root = gameDir.resolve("skyrecipes");
        this.dataDir = root.resolve("data");
        this.cacheDir = root.resolve("cache");
        this.importDir = root.resolve("import");
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

    public Path shardFusionsFile() {
        return dataDir.resolve(SHARD_FUSIONS_NAME);
    }

    /**
     * Transient landing path for the NEU repo ZIP. Compiled from, then deleted.
     */
    public Path neuRepoZip() {
        return cacheDir.resolve(NEU_ZIP_NAME);
    }

    /**
     * Drop folder for manually downloaded NEU repo ZIPs (offline backup path,
     * consumed by {@code /skyrecipes import}).
     */
    public Path importDir() {
        return importDir;
    }

    /**
     * Newest {@code .zip} in {@link #importDir()}, or null if none. Creates the
     * folder on demand so the user always has a place to drop the file.
     */
    public Path findNewestImportZip() {
        try {
            Files.createDirectories(importDir);
            try (Stream<Path> entries = Files.list(importDir)) {
                return entries
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                        .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not scan import folder {}", importDir, e);
            return null;
        }
    }

    public void createDirectories() throws IOException {
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);
        Files.createDirectories(importDir);
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
            // Under stream-then-discard the raw repo ZIP is no longer retained,
            // and the ETag now lives only in the binary's embedded metadata.
            Files.deleteIfExists(neuRepoZip());
            Files.deleteIfExists(cacheDir.resolve(NEU_ETAG_NAME));
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
