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
 * Metadata for the compiled binary .mpk file.
 *
 * <p>Authoritative copy is embedded in the binary's metadata section
 * (schema v8+). A JSON sidecar is still written for human inspection
 * but is never read at runtime.</p>
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
     * Deserialize from the binary's metadata section.
     */
    public static BinaryMetadata fromBytes(byte[] bytes) {
        return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), BinaryMetadata.class);
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

    /**
     * Serialize for embedding in the binary's metadata section.
     */
    public byte[] toBytes() {
        return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
    }
}
