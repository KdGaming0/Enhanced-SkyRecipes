package com.github.kdgaming0.skyrecipes.core.family;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves item families for recipe lookup expansion.
 *
 * <p>Builds explicit families from {@code constants/parents.json} and implicit families by
 * scanning all items in the {@link ItemRegistry}. Families are classified into
 * {@link FamilyType}s that determine whether pressing <b>R</b> on a member should show
 * recipes for all family members.</p>
 *
 * <p>Immutable after construction — safe to share across threads.</p>
 */
public final class FamilyResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FamilyResolver.class);

    /** Prefixes of families that have numeric-looking members but are actually collections. */
    private static final Set<String> DENYLIST_PREFIXES = Set.of(
        "MASTER_SKULL_TIER", "DUNGEON_DISC_",
        "BANNER", "CARPET", "INK_SACK", "STAINED_CLAY", "STAINED_GLASS",
        "STAINED_GLASS_PANE", "WOOL", "WOOD", "WOOD_STEP", "STONE", "STEP",
        "LOG", "SANDSTONE", "RED_SANDSTONE", "PRISMARINE", "QUARTZ_BLOCK",
        "SMOOTH_BRICK"
    );

    private static final Set<String> ACCESSORY_SUFFIXES = Set.of("TALISMAN", "RING", "ARTIFACT", "RELIC");

    private final Map<String, FamilyInfo> memberToFamily;

    public FamilyResolver(ConstantsRegistry constants, ItemRegistry items) {
        Map<String, FamilyInfo> map = new HashMap<>();
        if (constants != null) {
            buildExplicitFamilies(constants, map);
        }
        if (items != null) {
            buildImplicitFamilies(constants, items, map);
        }
        this.memberToFamily = Collections.unmodifiableMap(map);
        LOGGER.info("FamilyResolver built: {} items in {} families",
            map.size(), (int) map.values().stream().map(FamilyInfo::familyId).distinct().count());
    }

    /**
     * Returns the set of internal names to search for recipes when the player presses
     * <b>R</b> on the given item.
     *
     * <p>If the item's family type does not expand for results, returns a singleton set
     * containing only the given name.</p>
     *
     * @param internalName the SkyBlock internal name (e.g. {@code "WHEAT_GENERATOR_3"})
     * @return set of names to query; never null
     */
    public Set<String> getFamilyMembers(String internalName) {
        FamilyInfo info = memberToFamily.get(internalName);
        if (info == null) {
            return Set.of(internalName);
        }
        if (!info.type().expandsForResults()) {
            return Set.of(internalName);
        }
        return info.members();
    }

    // -----------------------------------------------------------------
    // Explicit families from parents.json
    // -----------------------------------------------------------------

    private void buildExplicitFamilies(ConstantsRegistry constants, Map<String, FamilyInfo> out) {
        Map<String, List<String>> parentToChildren = constants.getAllParents();
        if (parentToChildren == null || parentToChildren.isEmpty()) return;

        // Build child->parent map for root detection
        Map<String, String> childToParent = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : parentToChildren.entrySet()) {
            for (String child : entry.getValue()) {
                // In case of duplicate children, first parent wins
                childToParent.putIfAbsent(child, entry.getKey());
            }
        }

        for (String parent : parentToChildren.keySet()) {
            // Skip non-roots (this parent is itself a child of someone else)
            if (childToParent.containsKey(parent)) continue;

            Set<String> members = new LinkedHashSet<>();
            collectDescendants(parent, parentToChildren, members);

            if (members.size() < 2) continue;

            FamilyType type = classifyExplicitFamily(parent, members);
            FamilyInfo info = new FamilyInfo(parent, type, Collections.unmodifiableSet(members));
            for (String member : members) {
                out.put(member, info);
            }
        }
    }

    private void collectDescendants(String node, Map<String, List<String>> parentToChildren, Set<String> members) {
        members.add(node);
        List<String> children = parentToChildren.get(node);
        if (children == null) return;
        for (String child : children) {
            collectDescendants(child, parentToChildren, members);
        }
    }

    private FamilyType classifyExplicitFamily(String root, Set<String> members) {
        // Denylist
        for (String m : members) {
            for (String prefix : DENYLIST_PREFIXES) {
                if (m.startsWith(prefix)) {
                    return FamilyType.COLLECTION;
                }
            }
        }

        // Starred dungeon items
        for (String m : members) {
            if (m.startsWith("STARRED_")) {
                return FamilyType.STARRED;
            }
        }

        // Hardcoded branching families
        if ("NECRON_BLADE".equals(root)) {
            return FamilyType.BRANCHING;
        }

        // Tiered: at least two members have numeric tier suffixes
        long numericCount = members.stream().filter(this::hasNumericTier).count();
        if (numericCount >= 2) {
            return FamilyType.TIERED;
        }

        return FamilyType.COLLECTION;
    }

    // -----------------------------------------------------------------
    // Implicit families from item name patterns
    // -----------------------------------------------------------------

    private void buildImplicitFamilies(ConstantsRegistry constants, ItemRegistry items, Map<String, FamilyInfo> out) {
        Set<String> alreadyRegistered = new HashSet<>(out.keySet());

        Map<String, Set<String>> armorSets = new HashMap<>();
        Map<String, Set<String>> petFamilies = new HashMap<>();
        Map<String, Set<String>> accessoryChains = new HashMap<>();
        Map<String, Set<String>> drillFamilies = new HashMap<>();

        for (NeuItem item : items.getAllItems()) {
            String id = item.internalName();
            if (alreadyRegistered.contains(id)) continue;

            // Armor set piece
            String armorBase = getArmorBase(id);
            if (armorBase != null) {
                armorSets.computeIfAbsent(armorBase, k -> new HashSet<>()).add(id);
                continue;
            }

            // Pet tier
            String petBase = getPetBase(id);
            if (petBase != null) {
                petFamilies.computeIfAbsent(petBase, k -> new HashSet<>()).add(id);
                continue;
            }

            // Accessory chain piece
            String accessoryBase = getAccessoryBase(id);
            if (accessoryBase != null) {
                accessoryChains.computeIfAbsent(accessoryBase, k -> new HashSet<>()).add(id);
                continue;
            }

            // Drill tier
            String drillBase = getDrillBase(id);
            if (drillBase != null) {
                drillFamilies.computeIfAbsent(drillBase, k -> new HashSet<>()).add(id);
            }
        }

        registerImplicitFamily(armorSets, FamilyType.ARMOR_SET, "_SET", 2, out);
        registerImplicitFamily(petFamilies, FamilyType.TIERED, "_PET", 2, out);
        registerImplicitFamily(accessoryChains, FamilyType.ACCESSORY_CHAIN, "_ACCESSORY", 2, out);
        registerImplicitFamily(drillFamilies, FamilyType.TIERED, "_DRILL", 2, out);
    }

    private void registerImplicitFamily(Map<String, Set<String>> groups, FamilyType type,
                                        String idSuffix, int minSize, Map<String, FamilyInfo> out) {
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() >= minSize) {
                Set<String> members = Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue()));
                FamilyInfo info = new FamilyInfo(entry.getKey() + idSuffix, type, members);
                for (String member : members) {
                    out.put(member, info);
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Pattern detection helpers
    // -----------------------------------------------------------------

    private boolean hasNumericTier(String id) {
        // Match _N at end (e.g. GENERATOR_1, PERFECT_BOOTS_12)
        int lastUnderscore = id.lastIndexOf('_');
        if (lastUnderscore != -1 && lastUnderscore < id.length() - 1) {
            String suffix = id.substring(lastUnderscore + 1);
            if (isAllDigits(suffix)) return true;
        }
        // Match ;N at end (e.g. SHARPNESS;3, ARMADILLO;1)
        int semi = id.lastIndexOf(';');
        if (semi != -1 && semi < id.length() - 1) {
            String suffix = id.substring(semi + 1);
            if (isAllDigits(suffix)) return true;
        }
        return false;
    }

    private boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return !s.isEmpty();
    }

    private String getArmorBase(String id) {
        if (id.endsWith("_HELMET")) return id.substring(0, id.length() - 7);
        if (id.endsWith("_CHESTPLATE")) return id.substring(0, id.length() - 11);
        if (id.endsWith("_LEGGINGS")) return id.substring(0, id.length() - 9);
        if (id.endsWith("_BOOTS")) return id.substring(0, id.length() - 6);
        return null;
    }

    private String getPetBase(String id) {
        int semi = id.lastIndexOf(';');
        if (semi == -1) return null;
        return id.substring(0, semi);
    }

    private String getAccessoryBase(String id) {
        for (String suffix : ACCESSORY_SUFFIXES) {
            String withUnderscore = "_" + suffix;
            if (id.endsWith(withUnderscore)) {
                return id.substring(0, id.length() - withUnderscore.length());
            }
        }
        return null;
    }

    private String getDrillBase(String id) {
        int lastUnderscore = id.lastIndexOf('_');
        if (lastUnderscore == -1 || lastUnderscore == id.length() - 1) return null;
        String suffix = id.substring(lastUnderscore + 1);
        if (!isAllDigits(suffix)) return null;
        String prefix = id.substring(0, lastUnderscore);
        if (prefix.endsWith("_DRILL")) {
            return prefix;
        }
        return null;
    }
}
