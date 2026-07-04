package com.github.kdgaming0.skyrecipes.rrv.recipe;

import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockInfoClientRecipe;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping NPC internal names (e.g. {@code "WALTER_NPC"}) to their
 * corresponding {@link SkyblockInfoClientRecipe} so that NPC shop recipes can
 * offer an "NPC Info" navigation button.
 *
 * <p>Follows the pipeline's prepare-then-commit rule: {@link #beginCycle()} opens a
 * pending map that generation registers into, {@link #publish()} swaps it live at the
 * injection commit point. Live lookups keep serving the previous cycle's data during
 * a background reload, and an aborted cycle never touches them.</p>
 */
public final class NpcInfoRegistry {
    private static volatile Map<String, SkyblockInfoClientRecipe> live = Map.of();
    private static volatile Map<String, SkyblockInfoClientRecipe> pending;

    private NpcInfoRegistry() {
    }

    /** Opens a fresh pending map; called at the start of recipe generation. */
    public static void beginCycle() {
        pending = new ConcurrentHashMap<>();
    }

    public static void register(String npcInternalName, SkyblockInfoClientRecipe recipe) {
        Map<String, SkyblockInfoClientRecipe> target = pending;
        if (target != null && npcInternalName != null && !npcInternalName.isEmpty()) {
            target.put(npcInternalName, recipe);
        }
    }

    @Nullable
    public static SkyblockInfoClientRecipe get(String npcInternalName) {
        return live.get(npcInternalName);
    }

    /** Atomically replaces the live map with the pending cycle's; called at the injection commit. */
    public static void publish() {
        Map<String, SkyblockInfoClientRecipe> ready = pending;
        if (ready != null) {
            live = ready;
            pending = null;
        }
    }
}
