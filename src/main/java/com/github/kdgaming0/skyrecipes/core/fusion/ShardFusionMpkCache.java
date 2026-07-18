package com.github.kdgaming0.skyrecipes.core.fusion;

import com.github.kdgaming0.skyrecipes.core.util.AtomicFiles;
import org.jetbrains.annotations.Nullable;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes the parsed shard fusion snapshot as a compact MessagePack
 * binary at {@code [gameDir]/skyrecipes/data/shard_fusions.mpk} — the same fast
 * format the NEU binary cache uses, so a warm start never re-parses the ~2 MB
 * SkyShards JSON.
 *
 * <p>The source ETag is embedded in the header (mirroring the NEU binary's
 * embedded metadata) and drives the conditional refresh in
 * {@link ShardFusionFetcher}.</p>
 */
public final class ShardFusionMpkCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardFusionMpkCache.class);

    private static final String MAGIC = "SKYFUSION";
    private static final int SCHEMA_VERSION = 1;

    /** A loaded snapshot plus the ETag it was fetched under. */
    public record Loaded(ShardFusionData data, String etag) {
    }

    private ShardFusionMpkCache() {
    }

    /**
     * Loads the cached snapshot, or {@code null} when the file is absent,
     * corrupt, or from an incompatible schema version.
     */
    @Nullable
    public static Loaded load(Path cacheFile) {
        if (!Files.exists(cacheFile)) return null;
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(Files.readAllBytes(cacheFile))) {
            if (!MAGIC.equals(unpacker.unpackString())) return null;
            if (unpacker.unpackInt() != SCHEMA_VERSION) return null;
            String etag = unpacker.unpackString();
            unpacker.unpackLong(); // savedAt — informational only

            int n = unpacker.unpackInt();
            String[] shardIds = new String[n];
            String[] internalIds = new String[n];
            byte[] fuseAmounts = new byte[n];
            Map<String, Integer> idToIndex = HashMap.newHashMap(n);
            for (int i = 0; i < n; i++) {
                shardIds[i] = unpacker.unpackString();
                internalIds[i] = unpacker.unpackString();
                fuseAmounts[i] = unpacker.unpackByte();
                idToIndex.put(shardIds[i], i);
            }

            int[][] pairsByOutput = new int[n][];
            int totalPairs = 0;
            for (int i = 0; i < n; i++) {
                int len = unpacker.unpackInt();
                if (len < 0) continue;
                int[] pairs = new int[len];
                for (int p = 0; p < len; p++) {
                    pairs[p] = unpacker.unpackInt();
                }
                pairsByOutput[i] = pairs;
                totalPairs += len;
            }
            if (totalPairs == 0) return null;

            return new Loaded(new ShardFusionData(shardIds, internalIds, fuseAmounts,
                    Map.copyOf(idToIndex), pairsByOutput, totalPairs), etag);
        } catch (Exception e) {
            LOGGER.warn("Failed to load shard fusion cache — will re-fetch", e);
            return null;
        }
    }

    /**
     * Writes the snapshot atomically (temp file closed and flushed before the move).
     */
    public static void save(Path cacheFile, ShardFusionData data, String etag) {
        try {
            AtomicFiles.write(cacheFile, out -> {
                try (MessagePacker packer = MessagePack.newDefaultPacker(out)) {
                    pack(packer, data, etag);
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to save shard fusion cache", e);
        }
    }

    private static void pack(MessagePacker packer, ShardFusionData data, String etag) throws IOException {
        packer.packString(MAGIC);
        packer.packInt(SCHEMA_VERSION);
        packer.packString(etag != null ? etag : "");
        packer.packLong(System.currentTimeMillis());

        int n = data.shardCount();
        packer.packInt(n);
        for (int i = 0; i < n; i++) {
            packer.packString(data.shardId(i));
            packer.packString(data.internalId(i));
            packer.packByte((byte) data.fuseAmount(i));
        }
        for (int i = 0; i < n; i++) {
            int[] pairs = data.pairsFor(i);
            if (pairs == null) {
                packer.packInt(-1);
                continue;
            }
            packer.packInt(pairs.length);
            for (int packed : pairs) {
                packer.packInt(packed);
            }
        }
    }
}
