package com.github.kdgaming0.skyrecipes.core.model;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing all SkyBlock recipe types found in NEU repo data.
 */
public sealed interface NeuRecipe {

    /**
     * Classic 3x3 crafting grid recipe.
     *
     * @param grid Map of slot (A1-C3) to ingredient string ("ITEM:COUNT" or "")
     * @param count Output quantity (default 1)
     * @param overrideOutputId Optional output override
     */
    record CraftingRecipe(
        Map<String, String> grid,
        int count,
        String overrideOutputId
    ) implements NeuRecipe {}

    /**
     * Dwarven Forge timed crafting recipe.
     *
     * @param inputs Unordered ingredient list ("ITEM:COUNT")
     * @param count Output quantity
     * @param overrideOutputId Optional output override
     * @param duration Forge time in seconds
     */
    record ForgeRecipe(
        List<String> inputs,
        int count,
        String overrideOutputId,
        int duration
    ) implements NeuRecipe {}

    /**
     * Pet rarity upgrade at Kat NPC.
     *
     * @param coins Coin cost
     * @param time Processing time in seconds
     * @param input Input pet internal name with tier
     * @param output Output pet internal name with tier
     * @param items Additional item requirements
     */
    record KatGradeRecipe(
        int coins,
        int time,
        String input,
        String output,
        List<String> items
    ) implements NeuRecipe {}

    /**
     * NPC shop purchase recipe.
     *
     * @param npc Name of the selling NPC
     * @param costs Array of {item, cost} pairs
     * @param result Internal name of the item obtained
     */
    record NpcShopRecipe(
        String npc,
        List<Cost> costs,
        String result
    ) implements NeuRecipe {
        public record Cost(String item, int cost) {}
    }

    /**
     * Mob/boss drop source recipe.
     *
     * @param drops List of possible drops with chance strings
     */
    record DropsRecipe(
        List<Drop> drops
    ) implements NeuRecipe {
        public record Drop(String id, String chance) {}
    }

    /**
     * Barter/trade recipe.
     */
    record TradeRecipe(
        List<String> inputs,
        String output,
        int count
    ) implements NeuRecipe {}
}
