package com.github.kdgaming0.skyrecipes.core.fusion;

import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the SkyShards {@code fusion-data.json} into a {@link ShardFusionData} snapshot.
 *
 * <p>Two streaming passes over the raw bytes: pass 1 reads the {@code shards} table
 * (the file lists {@code recipes} first, so indices are not known until the shard
 * table has been read), pass 2 packs the pair lists against the final indices.
 * Streaming keeps the ~2 MB document off the heap as a DOM tree.</p>
 */
final class ShardFusionParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardFusionParser.class);

    private ShardFusionParser() {
    }

    /**
     * @return parsed snapshot, or {@code null} when the document is unusable
     */
    @Nullable
    static ShardFusionData parse(byte[] jsonBytes) {
        try {
            List<ShardDef> defs = readShards(jsonBytes);
            if (defs.isEmpty()) {
                LOGGER.warn("Shard fusion data contains no shards");
                return null;
            }
            // Dense indices in (rarity, name) order so packed pairs sort into display order.
            defs.sort(null);

            int n = defs.size();
            String[] shardIds = new String[n];
            String[] internalIds = new String[n];
            byte[] fuseAmounts = new byte[n];
            Map<String, Integer> idToIndex = HashMap.newHashMap(n);
            for (int i = 0; i < n; i++) {
                ShardDef def = defs.get(i);
                shardIds[i] = def.shardId;
                internalIds[i] = def.internalId;
                fuseAmounts[i] = (byte) Math.max(1, Math.min(127, def.fuseAmount));
                idToIndex.put(def.shardId, i);
            }

            return readRecipes(jsonBytes, shardIds, internalIds, fuseAmounts, idToIndex);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse shard fusion data", e);
            return null;
        }
    }

    // -- Pass 1: shard table --------------------------------------------------

    private static List<ShardDef> readShards(byte[] jsonBytes) throws IOException {
        List<ShardDef> defs = new ArrayList<>(200);
        try (JsonReader reader = newReader(jsonBytes)) {
            reader.beginObject();
            while (reader.hasNext()) {
                if ("shards".equals(reader.nextName())) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String shardId = reader.nextName();
                        ShardDef def = readShard(reader, shardId);
                        if (def != null) defs.add(def);
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return defs;
    }

    @Nullable
    private static ShardDef readShard(JsonReader reader, String shardId) throws IOException {
        String name = "";
        String rarity = "";
        String internalId = "";
        int fuseAmount = 2;

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "name" -> name = reader.nextString();
                case "rarity" -> rarity = reader.nextString();
                case "fuse_amount" -> fuseAmount = reader.nextInt();
                case "internal_id" -> internalId = reader.nextString();
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (shardId.isEmpty() || internalId.isEmpty()) return null;
        return new ShardDef(shardId, internalId, name, rarityRank(rarity), fuseAmount);
    }

    private static int rarityRank(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> 0;
            case "uncommon" -> 1;
            case "rare" -> 2;
            case "epic" -> 3;
            case "legendary" -> 4;
            default -> 5;
        };
    }

    // -- Pass 2: pair lists ---------------------------------------------------

    @Nullable
    private static ShardFusionData readRecipes(byte[] jsonBytes, String[] shardIds,
                                               String[] internalIds, byte[] fuseAmounts,
                                               Map<String, Integer> idToIndex) throws IOException {
        int n = shardIds.length;
        IntList[] collected = new IntList[n];
        int totalPairs = 0;
        int dropped = 0;

        try (JsonReader reader = newReader(jsonBytes)) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (!"recipes".equals(reader.nextName())) {
                    reader.skipValue();
                    continue;
                }
                reader.beginObject();
                while (reader.hasNext()) {
                    String outputId = reader.nextName();
                    Integer outIdx = idToIndex.get(outputId);
                    if (outIdx == null) {
                        dropped += skipQuantityGroups(reader);
                        continue;
                    }
                    reader.beginObject();
                    while (reader.hasNext()) {
                        int qty;
                        try {
                            qty = Integer.parseInt(reader.nextName());
                        } catch (NumberFormatException e) {
                            reader.skipValue();
                            continue;
                        }
                        qty = Math.max(1, Math.min(15, qty));
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginArray();
                            String first = reader.nextString();
                            String second = reader.nextString();
                            while (reader.hasNext()) reader.skipValue();
                            reader.endArray();

                            Integer f = idToIndex.get(first);
                            Integer s = idToIndex.get(second);
                            if (f == null || s == null) {
                                dropped++;
                                continue;
                            }
                            IntList list = collected[outIdx];
                            if (list == null) {
                                list = new IntList();
                                collected[outIdx] = list;
                            }
                            list.add(ShardFusionData.packPair(f, s, qty));
                            totalPairs++;
                        }
                        reader.endArray();
                    }
                    reader.endObject();
                }
                reader.endObject();
            }
            reader.endObject();
        }

        if (totalPairs == 0) {
            LOGGER.warn("Shard fusion data contains no usable fusion pairs");
            return null;
        }
        if (dropped > 0) {
            LOGGER.debug("Dropped {} fusion pairs referencing unknown shard IDs", dropped);
        }

        int[][] pairsByOutput = new int[n][];
        for (int i = 0; i < n; i++) {
            if (collected[i] != null) {
                int[] pairs = collected[i].toArray();
                Arrays.sort(pairs);
                pairsByOutput[i] = pairs;
            }
        }

        return new ShardFusionData(shardIds, internalIds, fuseAmounts,
                Map.copyOf(idToIndex), pairsByOutput, totalPairs);
    }

    /** Skips one output's quantity-group object, returning the number of pairs skipped. */
    private static int skipQuantityGroups(JsonReader reader) throws IOException {
        int skipped = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            reader.nextName();
            reader.beginArray();
            while (reader.hasNext()) {
                reader.skipValue();
                skipped++;
            }
            reader.endArray();
        }
        reader.endObject();
        return skipped;
    }

    private static JsonReader newReader(byte[] jsonBytes) {
        return new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(jsonBytes), StandardCharsets.UTF_8));
    }

    private record ShardDef(String shardId, String internalId, String name,
                            int rarityRank, int fuseAmount) implements Comparable<ShardDef> {
        @Override
        public int compareTo(ShardDef other) {
            int c = Integer.compare(rarityRank, other.rarityRank);
            if (c != 0) return c;
            c = name.compareTo(other.name);
            if (c != 0) return c;
            return shardId.compareTo(other.shardId);
        }
    }

    /** Minimal growable int array — avoids boxing 87k pair ints during parse. */
    private static final class IntList {
        private int[] data = new int[16];
        private int size;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
