package com.github.kdgaming0.skyrecipes.core.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic "write to a sibling temp file, then move it into place" helpers.
 *
 * <p>Centralises the temp-file + {@code ATOMIC_MOVE}-with-fallback pattern that was
 * hand-rolled (with inconsistent robustness) across the cache/save code in
 * {@code core/data} and {@code core/fusion}/{@code core/hypixel}.</p>
 */
public final class AtomicFiles {

    /** Streams bytes into the temp file created by {@link #write(Path, OutputWriter)}. */
    @FunctionalInterface
    public interface OutputWriter {
        void write(OutputStream out) throws IOException;
    }

    private AtomicFiles() {
    }

    /**
     * Writes {@code target} atomically: the parent directories are created, the
     * content is streamed via {@code writer} into a sibling {@code .tmp} file, and
     * that file is then moved into place with {@link #move(Path, Path)}.
     */
    public static void write(Path target, OutputWriter writer) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temp)) {
            writer.write(out);
        }
        move(temp, target);
    }

    /**
     * Moves {@code source} onto {@code target}, preferring an atomic move and
     * falling back to a plain replacing move on filesystems that don't support
     * atomic moves.
     */
    public static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
