package com.github.kdgaming0.skyrecipes.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sidecar metadata for the compiled binary .mpk file.
 * Stored as JSON alongside the binary for human-readable version tracking.
 */
public record BinaryMetadata(
    int schemaVersion,
    long buildTimestamp,
    int itemCount,
    String etag,
    String commitHash,
    String sourceUrl
) {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    /**
     * Read metadata from a JSON file.
     */
    public static BinaryMetadata read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, BinaryMetadata.class);
        }
    }

    /**
     * Write metadata to a JSON file.
     */
    public void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    /**
     * Check if this metadata indicates the binary is compatible with the given schema.
     */
    public boolean isCompatibleWith(int expectedSchema) {
        return this.schemaVersion == expectedSchema;
    }
}
