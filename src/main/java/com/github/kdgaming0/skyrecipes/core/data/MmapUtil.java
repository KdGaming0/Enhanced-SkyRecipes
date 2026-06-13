package com.github.kdgaming0.skyrecipes.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Utility for memory-mapping files with safe unmap support.
 * <p>On Windows, a memory-mapped file remains locked until the buffer is garbage-collected
 * or explicitly unmapped. This utility uses reflection to invoke {@code Unsafe.invokeCleaner}
 * for deterministic unmapping, with a fallback to GC.
 */
public final class MmapUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MmapUtil.class);

    private static final MethodHandle INVOKE_CLEANER;
    private static final Object UNSAFE;

    static {
        MethodHandle cleaner = null;
        Object unsafe = null;
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);

            Method method = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
            cleaner = MethodHandles.lookup()
                    .unreflect(method)
                    .bindTo(unsafe);
        } catch (Exception e) {
            LOGGER.debug("Unable to bind Unsafe.invokeCleaner: {}", e.getMessage());
        }
        INVOKE_CLEANER = cleaner;
        UNSAFE = unsafe;
    }

    private MmapUtil() {
    }

    /**
     * Memory-map a file as read-only and return a ByteBuffer.
     * If mapping fails, falls back to reading the entire file into a heap ByteBuffer.
     *
     * @param path the file to map
     * @return a ByteBuffer containing the file contents
     * @throws IOException if the file cannot be read
     */
    public static ByteBuffer mapFile(Path path) throws IOException {
        long size = Files.size(path);
        if (size > Integer.MAX_VALUE) {
            throw new IOException("File too large to map: " + size + " bytes");
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
        } catch (IOException e) {
            LOGGER.warn("Memory mapping failed for {}, falling back to heap read", path, e);
            // Fallback: read into heap buffer (msgpack-core cannot read from direct buffers on Java 25+)
            byte[] bytes = Files.readAllBytes(path);
            return ByteBuffer.wrap(bytes);
        }
    }

    /**
     * Attempt to unmap a MappedByteBuffer deterministically.
     * Best-effort: if Unsafe is not available, falls back to nulling the reference
     * and suggesting garbage collection.
     *
     * @param buffer the buffer to unmap
     */
    public static void unmap(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (!(buffer instanceof MappedByteBuffer)) {
            return;
        }
        if (INVOKE_CLEANER != null) {
            try {
                INVOKE_CLEANER.invokeExact(buffer);
                return;
            } catch (Throwable t) {
                LOGGER.debug("Unsafe.invokeCleaner failed: {}", t.getMessage());
            }
        }
        // Fallback: clear reference and hope for GC
        LOGGER.debug("Falling back to GC for unmapping");
    }

    /**
     * Check whether deterministic unmap is available on this JVM.
     */
    public static boolean isUnmapAvailable() {
        return INVOKE_CLEANER != null;
    }
}
