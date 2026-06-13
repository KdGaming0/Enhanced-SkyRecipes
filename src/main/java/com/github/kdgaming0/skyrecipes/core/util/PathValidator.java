package com.github.kdgaming0.skyrecipes.core.util;

import java.nio.file.Path;

/**
 * Validates filesystem paths before they are used as file I/O sinks.
 *
 * <p>Guards against path traversal by rejecting parent-directory references
 * ("{@code ..}") and ensuring paths are absolute and normalized before use.</p>
 */
public final class PathValidator {

    private PathValidator() {
    }

    /**
     * Validates a path supplied as a string.
     *
     * @param path the raw path string
     * @param name descriptive name used in error messages
     * @return an absolute, normalized path with no parent references
     * @throws IllegalArgumentException if the path is null, blank, or contains traversal sequences
     */
    public static Path requireSafePath(String path, String name) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        return requireSafePath(Path.of(path), name);
    }

    /**
     * Validates a {@link Path} before it is used for file I/O.
     *
     * @param path the path to validate
     * @param name descriptive name used in error messages
     * @return an absolute, normalized path with no parent references
     * @throws IllegalArgumentException if the path is null or resolves outside the expected root
     */
    public static Path requireSafePath(Path path, String name) {
        if (path == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }

        // Reject explicit parent references in the supplied path before any
        // normalization can mask them.
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                throw new IllegalArgumentException(name + " must not contain parent references: " + path);
            }
        }

        Path normalized = path.toAbsolutePath().normalize();

        // Defense in depth: if normalization still leaves a ".." component,
        // the path attempts to escape above the filesystem root.
        for (Path part : normalized) {
            if ("..".equals(part.toString())) {
                throw new IllegalArgumentException(name + " resolves outside the filesystem root: " + path);
            }
        }

        return normalized;
    }
}
