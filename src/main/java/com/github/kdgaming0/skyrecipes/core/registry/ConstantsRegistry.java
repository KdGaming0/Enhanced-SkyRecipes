package com.github.kdgaming0.skyrecipes.core.registry;

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
    private final Map<String, ReforgeData> reforges;
    private final Map<String, ReforgeStoneData> reforgeStones;
    /** Compile-time-generated whitelist of stat names observed in NEU gear item lore. */
    private final Set<String> knownStats;
    /** Reverse map: reforge name → stone internal name. */
    private final Map<String, String> reforgeNameToStone;

    public ConstantsRegistry(
        Map<String, List<String>> parents,
        Map<String, EssenceUpgradeData> essenceCosts,
        Set<String> bazaarItems,
        Map<String, String> museumCategories
    ) {
        this(parents, essenceCosts, bazaarItems, museumCategories,
             Collections.emptyMap(), Collections.emptyMap(),
             Set.of(), Collections.emptyMap());
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
             Set.of(), Collections.emptyMap());
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
        this.parents = parents != null ? Collections.unmodifiableMap(parents) : Collections.emptyMap();
        this.essenceCosts = essenceCosts != null ? Collections.unmodifiableMap(essenceCosts) : Collections.emptyMap();
        this.bazaarItems = bazaarItems != null ? Collections.unmodifiableSet(bazaarItems) : Collections.emptySet();
        this.museumCategories = museumCategories != null ? Collections.unmodifiableMap(museumCategories) : Collections.emptyMap();
        this.reforges = reforges != null ? Collections.unmodifiableMap(reforges) : Collections.emptyMap();
        this.reforgeStones = reforgeStones != null ? Collections.unmodifiableMap(reforgeStones) : Collections.emptyMap();
        this.knownStats = knownStats != null ? Collections.unmodifiableSet(knownStats) : Set.of();
        this.reforgeNameToStone = reforgeNameToStone != null ? Collections.unmodifiableMap(reforgeNameToStone) : Collections.emptyMap();
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

    /**
     * Returns the internal name of the reforge stone that applies the given reforge name.
     */
    public String getStoneForReforge(String reforgeName) {
        return reforgeNameToStone.get(reforgeName);
    }

    /**
     * Returns the compile-time-generated set of known stat names observed in NEU gear lore.
     */
    public Set<String> getKnownStats() {
        return knownStats;
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

    public Map<String, ReforgeData> getAllReforges() {
        return reforges;
    }

    public Map<String, ReforgeStoneData> getAllReforgeStones() {
        return reforgeStones;
    }

    public Map<String, String> getReforgeNameToStone() {
        return reforgeNameToStone;
    }
}
