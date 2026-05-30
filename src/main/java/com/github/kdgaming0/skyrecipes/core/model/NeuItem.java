package com.github.kdgaming0.skyrecipes.core.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable representation of a single NEU repository item.
 *
 * @param internalName   Canonical ID (e.g. "ASPECT_OF_THE_END")
 * @param itemId         Minecraft material ID (e.g. "minecraft:diamond_sword")
 * @param displayName    Formatted name with § color codes
 * @param nbtTag         Raw SNBT string
 * @param lore           Lore lines with § color codes
 * @param damage         Damage/metadata value
 * @param clickCommand   "viewrecipe" or empty
 * @param craftText      Human-readable requirement text
 * @param infoType       "WIKI_URL" or empty
 * @param info           Wiki URLs
 * @param recipe         3x3 crafting recipe (nullable)
 * @param recipes        Non-crafting recipes (nullable)
 * @param slayerReq      Slayer requirement e.g. "SPIDER_5" (nullable)
 * @param vanilla        True for vanilla Minecraft items
 */
public record NeuItem(
    String internalName,
    String itemId,
    String displayName,
    String nbtTag,
    List<String> lore,
    int damage,
    String clickCommand,
    String craftText,
    String infoType,
    List<String> info,
    @Nullable NeuRecipe recipe,
    @Nullable List<NeuRecipe> recipes,
    @Nullable String slayerReq,
    boolean vanilla
) {
    /**
     * Returns true if this item has any recipe data (crafting or other).
     */
    public boolean hasRecipes() {
        return recipe != null || (recipes != null && !recipes.isEmpty());
    }

    /**
     * Returns true if this item should show a recipe view when R is pressed.
     */
    public boolean hasViewRecipe() {
        return "viewrecipe".equals(clickCommand);
    }
}
