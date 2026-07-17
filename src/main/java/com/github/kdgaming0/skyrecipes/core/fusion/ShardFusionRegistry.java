package com.github.kdgaming0.skyrecipes.core.fusion;

import org.jetbrains.annotations.Nullable;

/**
 * Static holder for the loaded shard fusion snapshot.
 *
 * <p>Populated by the background fetch in {@code SkyRecipesClientPlugin}; read by
 * {@code ShardFusionGenerator} during recipe generation. {@code null} until the
 * first successful load — consumers must tolerate the absent state.</p>
 */
public final class ShardFusionRegistry {

    private static volatile ShardFusionData data;
    private static volatile String etag = "";

    private ShardFusionRegistry() {
    }

    public static void load(ShardFusionData snapshot, String sourceEtag) {
        data = snapshot;
        etag = sourceEtag != null ? sourceEtag : "";
    }

    @Nullable
    public static ShardFusionData get() {
        return data;
    }

    /** ETag the loaded snapshot was fetched under; empty when unknown. */
    public static String getEtag() {
        return etag;
    }
}
