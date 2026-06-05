package com.github.kdgaming0.skyrecipes.core.model.garden;

import java.util.List;

/**
 * Immutable garden mutation layout from mutations.json.
 *
 * @param id                  Mutation internal ID
 * @param name                Human-readable name
 * @param rarity              Item rarity string
 * @param gridSize            Layout dimension (1–6)
 * @param surface             Block type required (e.g. "Farmland")
 * @param needsWater          Whether water is required
 * @param stages              Growth stages
 * @param costCoins           Coin cost to spread
 * @param rewardCopper        Copper reward
 * @param layout              gridSize×gridSize array: "EMPTY", "TARGET", "INGREDIENT:ITEM_ID"
 * @param spreadingConditions Conditions for spreading
 * @param effects             Mutation effects
 * @param requiredFor         IDs that require this mutation as ingredient
 * @param specialMechanic     Optional special mechanic description
 */
public record GardenMutation(
        String id,
        String name,
        String rarity,
        int gridSize,
        String surface,
        boolean needsWater,
        int stages,
        long costCoins,
        int rewardCopper,
        List<List<String>> layout,
        List<SpreadingCondition> spreadingConditions,
        List<Effect> effects,
        List<String> requiredFor,
        String specialMechanic
) {
    public GardenMutation {
        layout = layout != null ? List.copyOf(layout.stream().map(List::copyOf).toList()) : List.of();
        spreadingConditions = spreadingConditions != null ? List.copyOf(spreadingConditions) : List.of();
        effects = effects != null ? List.copyOf(effects) : List.of();
        requiredFor = requiredFor != null ? List.copyOf(requiredFor) : List.of();
    }

    /**
     * Get the cell value at the given row/col in the layout.
     * Returns "EMPTY" for out-of-bounds coordinates.
     */
    public String cellAt(int row, int col) {
        if (row < 0 || row >= layout.size() || col < 0 || col >= layout.get(row).size()) {
            return "EMPTY";
        }
        return layout.get(row).get(col);
    }

    /**
     * True if the given cell is the target crop.
     */
    public boolean isTarget(int row, int col) {
        return "TARGET".equals(cellAt(row, col));
    }

    /**
     * True if the given cell is an ingredient.
     */
    public boolean isIngredient(int row, int col) {
        String cell = cellAt(row, col);
        return cell != null && cell.startsWith("INGREDIENT:");
    }

    /**
     * Extract the ingredient item ID from a cell, or empty string.
     */
    public String ingredientIdAt(int row, int col) {
        String cell = cellAt(row, col);
        if (cell != null && cell.startsWith("INGREDIENT:")) {
            return cell.substring("INGREDIENT:".length());
        }
        return "";
    }

    public record SpreadingCondition(String itemId, int count, String text) {
    }

    public record Effect(String name, String description) {
    }
}
