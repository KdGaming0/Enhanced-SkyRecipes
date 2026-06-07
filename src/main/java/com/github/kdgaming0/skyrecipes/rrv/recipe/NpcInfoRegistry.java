package com.github.kdgaming0.skyrecipes.rrv.recipe;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping NPC internal names (e.g. {@code "WALTER_NPC"}) to their
 * corresponding {@link SkyblockInfoClientRecipe} so that NPC shop recipes can
 * offer an "NPC Info" navigation button.
 */
public final class NpcInfoRegistry {
    private static final Map<String, SkyblockInfoClientRecipe> REGISTRY = new ConcurrentHashMap<>();

    private NpcInfoRegistry() {}

    public static void register(String npcInternalName, SkyblockInfoClientRecipe recipe) {
        if (npcInternalName != null && !npcInternalName.isEmpty()) {
            REGISTRY.put(npcInternalName, recipe);
        }
    }

    @Nullable
    public static SkyblockInfoClientRecipe get(String npcInternalName) {
        return REGISTRY.get(npcInternalName);
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
