package com.github.kdgaming0.skyrecipes.core.fusion;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Immutable snapshot of the SkyShards fusion dataset.
 *
 * <p>Shards are addressed by a dense index assigned in (rarity, name) order, so
 * packed pair arrays sort naturally into display order. Each fusion pair is one
 * packed int: {@code (firstIdx << 13) | (secondIdx << 4) | outputQty} — pair
 * order matters in the in-game fusion machine, so {@code [A,B]} and {@code [B,A]}
 * are distinct entries. The full dataset (~87k pairs) stays under ~400 KB.</p>
 */
public final class ShardFusionData {

    private final String[] shardIds;
    private final String[] internalIds;
    private final byte[] fuseAmounts;
    private final Map<String, Integer> idToIndex;
    /** Packed pairs producing each output shard, sorted ascending; null = no recipes. */
    private final int[][] pairsByOutput;
    private final int totalPairs;

    ShardFusionData(String[] shardIds, String[] internalIds, byte[] fuseAmounts,
                    Map<String, Integer> idToIndex, int[][] pairsByOutput, int totalPairs) {
        this.shardIds = shardIds;
        this.internalIds = internalIds;
        this.fuseAmounts = fuseAmounts;
        this.idToIndex = idToIndex;
        this.pairsByOutput = pairsByOutput;
        this.totalPairs = totalPairs;
    }

    public int shardCount() {
        return shardIds.length;
    }

    public int totalPairs() {
        return totalPairs;
    }

    /** In-game shard ID, e.g. "E1". */
    public String shardId(int index) {
        return shardIds[index];
    }

    /** SkyShards internal id == Hypixel bazaar product ID, e.g. "SHARD_TERRA". */
    public String internalId(int index) {
        return internalIds[index];
    }

    /** Copies of this shard consumed when it is used as a fusion input. */
    public int fuseAmount(int index) {
        return fuseAmounts[index];
    }

    /** Index for an in-game shard ID, or -1 if unknown. */
    public int indexOf(String shardId) {
        Integer idx = idToIndex.get(shardId);
        return idx != null ? idx : -1;
    }

    /** Sorted packed pairs producing this shard, or {@code null} if none. */
    @Nullable
    public int[] pairsFor(int outputIndex) {
        return pairsByOutput[outputIndex];
    }

    // -- Packed pair helpers --------------------------------------------------

    public static int packPair(int first, int second, int qty) {
        return (first << 13) | (second << 4) | qty;
    }

    public static int pairFirst(int packed) {
        return packed >>> 13;
    }

    public static int pairSecond(int packed) {
        return (packed >>> 4) & 0x1FF;
    }

    public static int pairQty(int packed) {
        return packed & 0xF;
    }
}
