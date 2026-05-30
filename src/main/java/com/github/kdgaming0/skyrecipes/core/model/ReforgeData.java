package com.github.kdgaming0.skyrecipes.core.model;

import java.util.Map;

/**
 * Reforge definition with stat modifiers per rarity.
 *
 * @param applicableItemTypes List of item types this reforge applies to
 * @param statsPerRarity Map of rarity -> stat name -> stat value
 */
public record ReforgeData(
    java.util.List<String> applicableItemTypes,
    Map<SkyblockRarity, Map<String, Number>> statsPerRarity
) {}
