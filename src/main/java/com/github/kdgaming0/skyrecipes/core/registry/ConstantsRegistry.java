package com.github.kdgaming0.skyrecipes.core.registry;

import com.github.kdgaming0.skyrecipes.core.mob.MobRenderDefinition;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.model.EssenceUpgradeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeData;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for NEU constants data loaded from the binary.
 */
public final class ConstantsRegistry {

    private final Map<String, List<String>> parents;
    private final Map<String, EssenceUpgradeData> essenceCosts;
    private final Set<String> bazaarItems;
    private final Map<String, String> museumCategories;
    /** museum.json "children": item → the item it upgrades from (curated upgrade lines). */
    private final Map<String, String> museumChildren;
    private final Map<String, ReforgeData> reforges;
    private final Map<String, ReforgeStoneData> reforgeStones;
    private final Set<String> knownStats;
    private final Map<String, String> reforgeNameToStone;
    private final Map<String, MobRenderDefinition> mobDefinitions;
    private final Map<String, byte[]> mobSkins;
    private final Map<String, AttributeShardData> attributeShards;

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
                Collections.emptyMap(), Collections.emptyMap(),
                Set.of(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories,
            Map<String, ReforgeData> reforges,
            Map<String, ReforgeStoneData> reforgeStones
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
                reforges, reforgeStones,
                Set.of(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories,
            Map<String, ReforgeData> reforges,
            Map<String, ReforgeStoneData> reforgeStones,
            Set<String> knownStats,
            Map<String, String> reforgeNameToStone
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
                reforges, reforgeStones, knownStats, reforgeNameToStone,
                Collections.emptyMap(), Collections.emptyMap());
    }

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories,
            Map<String, ReforgeData> reforges,
            Map<String, ReforgeStoneData> reforgeStones,
            Set<String> knownStats,
            Map<String, String> reforgeNameToStone,
            Map<String, MobRenderDefinition> mobDefinitions,
            Map<String, byte[]> mobSkins
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
                reforges, reforgeStones, knownStats, reforgeNameToStone,
                mobDefinitions, mobSkins, Collections.emptyMap());
    }

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories,
            Map<String, ReforgeData> reforges,
            Map<String, ReforgeStoneData> reforgeStones,
            Set<String> knownStats,
            Map<String, String> reforgeNameToStone,
            Map<String, MobRenderDefinition> mobDefinitions,
            Map<String, byte[]> mobSkins,
            Map<String, String> museumChildren
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
                reforges, reforgeStones, knownStats, reforgeNameToStone,
                mobDefinitions, mobSkins, museumChildren, Collections.emptyMap());
    }

    public ConstantsRegistry(
            Map<String, List<String>> parents,
            Map<String, EssenceUpgradeData> essenceCosts,
            Set<String> bazaarItems,
            Map<String, String> museumCategories,
            Map<String, ReforgeData> reforges,
            Map<String, ReforgeStoneData> reforgeStones,
            Set<String> knownStats,
            Map<String, String> reforgeNameToStone,
            Map<String, MobRenderDefinition> mobDefinitions,
            Map<String, byte[]> mobSkins,
            Map<String, String> museumChildren,
            Map<String, AttributeShardData> attributeShards
    ) {
        this.attributeShards = attributeShards != null ? Collections.unmodifiableMap(attributeShards) : Collections.emptyMap();
        this.museumChildren = museumChildren != null ? Collections.unmodifiableMap(museumChildren) : Collections.emptyMap();
        this.parents = parents != null ? Collections.unmodifiableMap(parents) : Collections.emptyMap();
        this.essenceCosts = essenceCosts != null ? Collections.unmodifiableMap(essenceCosts) : Collections.emptyMap();
        this.bazaarItems = bazaarItems != null ? Collections.unmodifiableSet(bazaarItems) : Collections.emptySet();
        this.museumCategories = museumCategories != null ? Collections.unmodifiableMap(museumCategories) : Collections.emptyMap();
        this.reforges = reforges != null ? Collections.unmodifiableMap(reforges) : Collections.emptyMap();
        this.reforgeStones = reforgeStones != null ? Collections.unmodifiableMap(reforgeStones) : Collections.emptyMap();
        this.knownStats = knownStats != null ? Collections.unmodifiableSet(knownStats) : Set.of();
        this.reforgeNameToStone = reforgeNameToStone != null ? Collections.unmodifiableMap(reforgeNameToStone) : Collections.emptyMap();
        this.mobDefinitions = mobDefinitions != null ? Collections.unmodifiableMap(mobDefinitions) : Collections.emptyMap();
        this.mobSkins = mobSkins != null ? Collections.unmodifiableMap(mobSkins) : Collections.emptyMap();
    }

    public List<String> getChildren(String parentItem) {
        return parents.getOrDefault(parentItem, Collections.emptyList());
    }

    public boolean hasParent(String item) {
        for (Map.Entry<String, List<String>> entry : parents.entrySet()) {
            if (entry.getValue().contains(item)) {
                return true;
            }
        }
        return false;
    }

    public String getParentOf(String childItem) {
        for (Map.Entry<String, List<String>> entry : parents.entrySet()) {
            if (entry.getValue().contains(childItem)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public EssenceUpgradeData getEssenceCost(String item) {
        return essenceCosts.get(item);
    }

    public boolean isBazaarItem(String item) {
        return bazaarItems.contains(item);
    }

    public String getMuseumCategory(String item) {
        return museumCategories.get(item);
    }

    public ReforgeData getReforge(String name) {
        return reforges.get(name);
    }

    public ReforgeStoneData getReforgeStone(String internalName) {
        return reforgeStones.get(internalName);
    }

    public String getStoneForReforge(String reforgeName) {
        return reforgeNameToStone.get(reforgeName);
    }

    public Set<String> getKnownStats() {
        return knownStats;
    }

    public MobRenderDefinition getMobRender(String ref) {
        return mobDefinitions.get(ref);
    }

    public byte[] getMobSkin(String path) {
        return mobSkins.get(path);
    }

    public Map<String, List<String>> getAllParents() {
        return parents;
    }

    public Map<String, EssenceUpgradeData> getAllEssenceCosts() {
        return essenceCosts;
    }

    public Set<String> getBazaarItems() {
        return bazaarItems;
    }

    public Map<String, String> getAllMuseumCategories() {
        return museumCategories;
    }

    /** museum.json "children": item → the item it upgrades from. */
    public Map<String, String> getMuseumChildren() {
        return museumChildren;
    }

    public Map<String, ReforgeData> getAllReforges() {
        return reforges;
    }

    public Map<String, ReforgeStoneData> getAllReforgeStones() {
        return reforgeStones;
    }

    public Map<String, String> getReforgeNameToStone() {
        return reforgeNameToStone;
    }

    public Map<String, MobRenderDefinition> getAllMobDefinitions() {
        return mobDefinitions;
    }

    public Map<String, byte[]> getAllMobSkins() {
        return mobSkins;
    }

    /** Attribute shard metadata keyed by NEU internal name (e.g. "ATTRIBUTE_SHARD_EARTH_ELEMENTAL;1"). */
    public AttributeShardData getAttributeShard(String internalName) {
        return attributeShards.get(internalName);
    }

    public Map<String, AttributeShardData> getAllAttributeShards() {
        return attributeShards;
    }
}
