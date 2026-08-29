package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.data.codec.ConstantsCodec;
import com.github.kdgaming0.skyrecipes.core.data.codec.ItemCodec;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

/**
 * Runtime loader that reads the compiled binary .mpk file from disk
 * and deserializes it into registries.
 *
 * <p>Supports memory-mapped file access for fast loading.</p>
 */
public class BinaryDataLoader {

    public static final int EXPECTED_SCHEMA = 12;
    private static final int HEADER_SIZE = 96;
    private static final long MAX_METADATA_LENGTH = 1 << 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryDataLoader.class);
    private static final byte[] EXPECTED_MAGIC = new byte[]{'S', 'K', 'Y', '2'};
    private ByteBuffer fileBuffer;
    private ItemRegistry itemRegistry;
    private ConstantsRegistry constantsRegistry;
    private BinaryMetadata metadata;
    private LoadFailure lastFailure = LoadFailure.NONE;

    /**
     * Read only the embedded metadata section from a v8 binary, without
     * deserializing items or constants. Used for cheap ETag comparisons.
     */
    public static BinaryMetadata readEmbeddedMetadata(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < HEADER_SIZE) {
                throw new IOException("Binary file too small");
            }
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (!Arrays.equals(magic, EXPECTED_MAGIC)) {
                throw new IOException("Invalid binary magic bytes");
            }
            int schemaVersion = raf.readInt();
            if (schemaVersion != EXPECTED_SCHEMA) {
                throw new IOException("Schema version mismatch: " + schemaVersion);
            }
            raf.seek(64);
            long metadataOffset = raf.readLong();
            long metadataLength = raf.readLong();
            if (metadataLength <= 0 || metadataLength > MAX_METADATA_LENGTH
                    || metadataOffset + metadataLength > raf.length()) {
                throw new IOException("Invalid metadata section bounds");
            }
            raf.seek(metadataOffset);
            byte[] bytes = new byte[(int) metadataLength];
            raf.readFully(bytes);
            return BinaryMetadata.fromBytes(bytes);
        }
    }

    /**
     * Load binary data from a file path.
     *
     * <p>Validation order: size, magic, schema, section bounds, payload
     * CRC32C, metadata section, items, constants. Each rejection logs a
     * distinct reason and sets {@link #getLastFailure()}.</p>
     *
     * @param path path to the .mpk file
     * @return true if loaded successfully
     */
    public boolean load(Path path) {
        long startTime = System.currentTimeMillis();
        lastFailure = LoadFailure.CORRUPT;
        try {
            close(); // release any previous mapping

            this.fileBuffer = MmapUtil.mapFile(path);

            if (fileBuffer.remaining() < HEADER_SIZE) {
                LOGGER.error("Binary file too small ({} bytes). Expected at least {} bytes.",
                        fileBuffer.remaining(), HEADER_SIZE);
                return false;
            }

            // Read header
            byte[] magic = new byte[4];
            fileBuffer.get(magic);
            if (!Arrays.equals(magic, EXPECTED_MAGIC)) {
                LOGGER.error("Invalid binary magic bytes. Expected SKY2.");
                return false;
            }

            int schemaVersion = fileBuffer.getInt();
            long buildTimestamp = fileBuffer.getLong();
            int itemCount = fileBuffer.getInt();
            int sectionCount = fileBuffer.getInt();
            fileBuffer.getLong();
            long itemsOffset = fileBuffer.getLong();
            long itemsLength = fileBuffer.getLong();
            long constantsOffset = fileBuffer.getLong();
            long constantsLength = fileBuffer.getLong();
            long metadataOffset = fileBuffer.getLong();
            long metadataLength = fileBuffer.getLong();
            int expectedCrc = fileBuffer.getInt();

            if (schemaVersion != EXPECTED_SCHEMA) {
                LOGGER.error("Binary schema version mismatch: expected {}, got {}. Data may be stale or incompatible.",
                        EXPECTED_SCHEMA, schemaVersion);
                lastFailure = LoadFailure.SCHEMA_MISMATCH;
                return false;
            }

            long fileSize = fileBuffer.capacity();
            if (itemsOffset + itemsLength > fileSize
                    || constantsOffset + constantsLength > fileSize
                    || metadataOffset + metadataLength > fileSize) {
                LOGGER.error("Binary section offsets exceed file size. File may be truncated.");
                return false;
            }
            if (metadataLength <= 0 || metadataLength > MAX_METADATA_LENGTH) {
                LOGGER.error("Binary metadata section has implausible length {}.", metadataLength);
                return false;
            }

            java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
            crc.update(fileBuffer.duplicate().position(HEADER_SIZE));
            if ((int) crc.getValue() != expectedCrc) {
                LOGGER.error("Binary checksum mismatch (expected {}, got {}). File is corrupt.",
                        Integer.toHexString(expectedCrc), Long.toHexString(crc.getValue() & 0xFFFFFFFFL));
                return false;
            }

            byte[] metadataBytes = new byte[(int) metadataLength];
            fileBuffer.duplicate().position((int) metadataOffset).get(metadataBytes);
            try {
                this.metadata = BinaryMetadata.fromBytes(metadataBytes);
            } catch (Exception e) {
                LOGGER.error("Binary metadata section is unreadable.", e);
                return false;
            }

            LOGGER.info("Loading binary: schema={}, items={}, sections={}, built={}",
                    schemaVersion, itemCount, sectionCount, new Date(buildTimestamp));

            // Read items section
            // Copy to byte[] because msgpack-core 0.9.8 cannot read from
            // MappedByteBuffer (direct buffer) on Java 25+ due to module restrictions.
            byte[] itemsBytes = new byte[(int) itemsLength];
            fileBuffer.duplicate().position((int) itemsOffset).limit((int) (itemsOffset + itemsLength)).get(itemsBytes);
            List<NeuItem> items = unpackItems(itemsBytes, itemCount);
            this.itemRegistry = new ItemRegistry(items);

            // Read constants section
            byte[] constantsBytes = new byte[(int) constantsLength];
            fileBuffer.duplicate().position((int) constantsOffset).limit((int) (constantsOffset + constantsLength)).get(constantsBytes);
            this.constantsRegistry = unpackConstants(constantsBytes);

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("Binary loaded in {} ms. Items: {}, Parents: {}, Essence: {}, Bazaar: {}, Museum: {}, Reforges: {}, ReforgeStones: {}, MobDefs: {}, MobSkins: {}",
                    elapsed,
                    itemRegistry.size(),
                    constantsRegistry.getAllParents().size(),
                    constantsRegistry.getAllEssenceCosts().size(),
                    constantsRegistry.getBazaarItems().size(),
                    constantsRegistry.getAllMuseumCategories().size(),
                    constantsRegistry.getAllReforges().size(),
                    constantsRegistry.getAllReforgeStones().size(),
                    constantsRegistry.getAllMobDefinitions().size(),
                    constantsRegistry.getAllMobSkins().size()
            );
            lastFailure = LoadFailure.NONE;
            return true;

        } catch (Exception e) {
            // msgpack throws RuntimeExceptions (MessagePackException) on data that
            // passes the CRC but is semantically corrupt — same contract: return false.
            LOGGER.error("Failed to load binary data", e);
            return false;
        } finally {
            // Everything is copied to heap during load, so the mapping is never
            // read again — release it immediately. A held mapping also locks the
            // file on Windows, blocking moves and later compiles.
            if (fileBuffer != null) {
                MmapUtil.unmap(fileBuffer);
                fileBuffer = null;
            }
            if (lastFailure != LoadFailure.NONE) {
                close();
            }
        }
    }

    /**
     * Drop the loaded registries. The file mapping is already released at the
     * end of {@link #load(Path)}; this only frees the heap data.
     */
    public void close() {
        if (fileBuffer != null) {
            MmapUtil.unmap(fileBuffer);
            fileBuffer = null;
        }
        itemRegistry = null;
        constantsRegistry = null;
        metadata = null;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ConstantsRegistry getConstantsRegistry() {
        return constantsRegistry;
    }

    /**
     * Metadata embedded in the loaded binary; non-null after a successful {@link #load(Path)}.
     */
    public BinaryMetadata getMetadata() {
        return metadata;
    }

    public LoadFailure getLastFailure() {
        return lastFailure;
    }

    // ---- MessagePack deserialization (see core.data.codec for the section codecs) ----

    private List<NeuItem> unpackItems(byte[] data, int expectedCount) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            return ItemCodec.unpackItems(unpacker, expectedCount);
        }
    }

    private ConstantsRegistry unpackConstants(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            return ConstantsCodec.unpack(unpacker);
        }
    }

    /**
     * Why the most recent {@link #load(Path)} returned false. Lets callers
     * distinguish a stale-but-intact file (recompile) from real corruption
     * (quarantine before recompiling).
     */
    public enum LoadFailure {NONE, SCHEMA_MISMATCH, CORRUPT}
}
