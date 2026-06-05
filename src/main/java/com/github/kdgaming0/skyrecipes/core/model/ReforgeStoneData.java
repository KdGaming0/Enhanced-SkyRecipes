package com.github.kdgaming0.skyrecipes.core.model;

import java.util.List;
import java.util.Map;

/**
 * Reforge stone definition mapping a stone item to the reforge it applies.
 *
 * @param internalName     The NEU internal name of the stone item (e.g. "AMBER_MATERIAL")
 * @param reforgeName      The reforge this stone applies (e.g. "Ambered")
 * @param reforgeType      The reforge type/category (e.g. "blacksmith/reforge_stone")
 * @param itemTypes        Comma-separated item types this stone can apply to
 * @param requiredRarities List of rarities supported
 * @param reforgeAbility   Ability description per rarity
 * @param reforgeCosts     Coin cost per rarity
 * @param reforgeStats     Stat modifiers per rarity
 */
public record ReforgeStoneData(
        String internalName,
        String reforgeName,
        String reforgeType,
        String itemTypes,
        List<String> requiredRarities,
        Map<String, String> reforgeAbility,
        Map<String, Number> reforgeCosts,
        Map<String, Map<String, Number>> reforgeStats
) {
}
