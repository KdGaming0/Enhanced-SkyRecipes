package com.github.kdgaming0.skyrecipes.core.model;

import java.util.List;
import java.util.Map;

/**
 * Reforge definition with stat modifiers per rarity.
 *
 * @param reforgeName The canonical reforge name (e.g. "Ambered")
 * @param itemTypes Comma-separated or single item type string (e.g. "PICKAXE", "SWORD,BOW")
 * @param requiredRarities List of rarities this reforge supports
 * @param statsPerRarity Map of rarity name -> stat name -> stat value
 * @param reforgeAbility Optional ability text per rarity (may be empty)
 * @param reforgeCosts Optional coin cost per rarity (may be empty)
 */
public record ReforgeData(
    String reforgeName,
    String itemTypes,
    List<String> requiredRarities,
    Map<String, Map<String, Number>> statsPerRarity,
    Map<String, String> reforgeAbility,
    Map<String, Number> reforgeCosts
) {}
