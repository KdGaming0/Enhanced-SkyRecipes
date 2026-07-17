package com.github.kdgaming0.skyrecipes.core.model;

import java.util.List;

/**
 * Attribute shard metadata from NEU constants/attribute_shards.json.
 *
 * @param internalName NEU internal name of the shard item (e.g. "ATTRIBUTE_SHARD_EARTH_ELEMENTAL;1")
 * @param shardName    Short shard name (e.g. "Terra")
 * @param abilityName  The attribute/ability the shard grants (e.g. "Earth Elemental")
 * @param rarity       Rarity string (e.g. "EPIC")
 * @param alignment    Shard alignment/category (e.g. "Forest", "Combat", "Water")
 * @param family       Shard families (e.g. ["Elemental"]); may be empty
 * @param shardId      In-game shard ID (e.g. "E1")
 * @param bazaarName   Bazaar product ID (e.g. "SHARD_TERRA")
 */
public record AttributeShardData(
        String internalName,
        String shardName,
        String abilityName,
        String rarity,
        String alignment,
        List<String> family,
        String shardId,
        String bazaarName
) {
}
