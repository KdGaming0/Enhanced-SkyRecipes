package com.github.kdgaming0.skyrecipes.core.family;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.core.search.ItemCategoryResolver;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    /**
     * Prefixes of families that have numeric-looking members but are actually collections.
     */
    private static final Set<String> DENYLIST_PREFIXES = Set.of(
            "MASTER_SKULL_TIER", "DUNGEON_DISC_",
            "BANNER", "CARPET", "INK_SACK", "STAINED_CLAY", "STAINED_GLASS",
            "STAINED_GLASS_PANE", "WOOL", "WOOD", "WOOD_STEP", "STONE", "STEP",
            "LOG", "SANDSTONE", "RED_SANDSTONE", "PRISMARINE", "QUARTZ_BLOCK",
            "SMOOTH_BRICK"
    );

    private static final Set<String> ACCESSORY_SUFFIXES = Set.of("TALISMAN", "RING", "ARTIFACT", "RELIC", "HEIRLOOM", "CHRONOMICON");

    private static final Map<String, Integer> ACCESSORY_TIER_MAP;
    private static final Map<String, Integer> GEMSTONE_TIER_MAP = Map.of(
            "ROUGH", 1,
            "FLAWED", 2,
            "FINE", 3,
            "FLAWLESS", 4,
            "PERFECT", 5
    );

    /** Kuudra armor upgrade ladder: BASE(0) → HOT → BURNING → FIERY → INFERNAL. */
    private static final Map<String, Integer> KUUDRA_TIER_MAP = Map.of(
            "HOT", 1,
            "BURNING", 2,
            "FIERY", 3,
            "INFERNAL", 4
    );

    private static final String[] ARMOR_SLOT_SUFFIXES = {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};

    static {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("TALISMAN", 1);
        map.put("RING", 2);
        map.put("ARTIFACT", 3);
        map.put("RELIC", 4);
        map.put("HEIRLOOM", 5);
        map.put("CHRONOMICON", 6);
        ACCESSORY_TIER_MAP = Collections.unmodifiableMap(map);
    }

    private final Map<String, FamilyInfo> memberToFamily;

    /**
     * @param groupCraftedChains also build general crafted-into upgrade chains (compaction
     *                           lines, weapon upgrades) — see {@link #buildGeneralCraftedChains}
     */
    public FamilyResolver(ConstantsRegistry constants, ItemRegistry items, boolean groupCraftedChains) {
        Map<String, FamilyInfo> map = new HashMap<>();
        if (constants != null) {
            buildExplicitFamilies(constants, map);
        }
        if (items != null) {
            buildImplicitFamilies(constants, items, map);
            if (groupCraftedChains) {
                buildGeneralCraftedChains(items, map);
            }
        }
        this.memberToFamily = Collections.unmodifiableMap(map);
        LOGGER.info("FamilyResolver built: {} items in {} families",
                map.size(), (int) map.values().stream().map(FamilyInfo::familyId).distinct().count());
    }

    /**
     * Extracts the numeric tier from an internal name.
     *
     * <p>Priority order:</p>
     * <ol>
     *   <li>Numeric suffix {@code _N} or {@code ;N} (minions, pets, enchantments, drills)</li>
     *   <li>Accessory suffix ({@code TALISMAN=1}, {@code RING=2}, {@code ARTIFACT=3},
     *       {@code RELIC=4}, {@code HEIRLOOM=5}, {@code CHRONOMICON=6})</li>
     *   <li>Gemstone quality prefix ({@code ROUGH=1}, {@code FLAWED=2}, {@code FINE=3},
     *       {@code FLAWLESS=4}, {@code PERFECT=5}) for {@code *_GEM} items</li>
     * </ol>
     *
     * <p>Returns 0 when no tier is found.</p>
     */
    public static int extractTier(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return 0;
        }

        // Tiered: ;N suffix (pets, enchantments, etc.)
        int semi = internalName.lastIndexOf(';');
        if (semi != -1 && semi < internalName.length() - 1) {
            String suffix = internalName.substring(semi + 1);
            if (isAllDigits(suffix)) {
                try {
                    return Integer.parseInt(suffix);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Tiered: _N suffix (minions, drills, etc.)
        int lastUnderscore = internalName.lastIndexOf('_');
        if (lastUnderscore != -1 && lastUnderscore < internalName.length() - 1) {
            String suffix = internalName.substring(lastUnderscore + 1);
            if (isAllDigits(suffix)) {
                try {
                    return Integer.parseInt(suffix);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Accessory chain suffixes
        for (Map.Entry<String, Integer> entry : ACCESSORY_TIER_MAP.entrySet()) {
            String suffix = "_" + entry.getKey();
            if (internalName.endsWith(suffix)) {
                return entry.getValue();
            }
        }

        // Gemstone quality prefixes
        if (internalName.endsWith("_GEM")) {
            int firstUnderscore = internalName.indexOf('_');
            if (firstUnderscore > 0) {
                String prefix = internalName.substring(0, firstUnderscore);
                Integer tier = GEMSTONE_TIER_MAP.get(prefix);
                if (tier != null) {
                    return tier;
                }
            }
        }

        // Kuudra upgrade prefixes, only on armor pieces (HOT_CRIMSON_HELMET)
        if (getArmorSlotSuffix(internalName) != null) {
            int firstUnderscore = internalName.indexOf('_');
            if (firstUnderscore > 0) {
                Integer tier = KUUDRA_TIER_MAP.get(internalName.substring(0, firstUnderscore));
                if (tier != null) {
                    return tier;
                }
            }
        }

        return 0;
    }

    // -----------------------------------------------------------------
    // Tier extraction utilities (shared across sorting contexts)
    // -----------------------------------------------------------------

    /**
     * Extracts the family base name from an internal name.
     *
     * <p>For tiered items this is the prefix before the numeric tier suffix
     * (e.g. {@code WHEAT_GENERATOR_3} → {@code WHEAT_GENERATOR},
     * {@code ARMADILLO;2} → {@code ARMADILLO}).</p>
     *
     * <p>For accessory chains the accessory suffix is stripped
     * (e.g. {@code ZOMBIE_TALISMAN} → {@code ZOMBIE}).</p>
     *
     * <p>For armor sets the armor-piece suffix is stripped
     * (e.g. {@code DIAMOND_CHESTPLATE} → {@code DIAMOND}).</p>
     *
     * <p>For gemstones the quality prefix is stripped
     * (e.g. {@code FINE_RUBY_GEM} → {@code RUBY_GEM}).</p>
     *
     * <p>For all other items the full internal name is returned.</p>
     */
    public static String extractBaseName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return "";
        }

        // Tiered: ;N suffix (pets, enchantments, etc.)
        int semi = internalName.lastIndexOf(';');
        if (semi != -1 && semi < internalName.length() - 1) {
            String suffix = internalName.substring(semi + 1);
            if (isAllDigits(suffix)) {
                return internalName.substring(0, semi);
            }
        }

        // Tiered: _N suffix (minions, drills, etc.)
        int lastUnderscore = internalName.lastIndexOf('_');
        if (lastUnderscore != -1 && lastUnderscore < internalName.length() - 1) {
            String suffix = internalName.substring(lastUnderscore + 1);
            if (isAllDigits(suffix)) {
                return internalName.substring(0, lastUnderscore);
            }
        }

        // Accessory chain suffixes
        for (String accSuffix : ACCESSORY_SUFFIXES) {
            String withUnderscore = "_" + accSuffix;
            if (internalName.endsWith(withUnderscore)) {
                return internalName.substring(0, internalName.length() - withUnderscore.length());
            }
        }

        // Armor set suffixes
        if (internalName.endsWith("_HELMET")) {
            return internalName.substring(0, internalName.length() - 7);
        }
        if (internalName.endsWith("_CHESTPLATE")) {
            return internalName.substring(0, internalName.length() - 11);
        }
        if (internalName.endsWith("_LEGGINGS")) {
            return internalName.substring(0, internalName.length() - 9);
        }
        if (internalName.endsWith("_BOOTS")) {
            return internalName.substring(0, internalName.length() - 6);
        }

        // Gemstone quality prefixes
        if (internalName.endsWith("_GEM")) {
            int firstUnderscore = internalName.indexOf('_');
            if (firstUnderscore > 0) {
                String prefix = internalName.substring(0, firstUnderscore);
                if (GEMSTONE_TIER_MAP.containsKey(prefix)) {
                    return internalName.substring(firstUnderscore + 1);
                }
            }
        }

        return internalName;
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return !s.isEmpty();
    }

    // -----------------------------------------------------------------
    // Explicit families from parents.json
    // -----------------------------------------------------------------

    /**
     * Returns the set of internal names to search for recipes when the player presses
     * <b>R</b> on the given item.
     *
     * <p>If the item's family type does not expand for results, returns a singleton set
     * containing only the given name.</p>
     *
     * <p>For expanding families, members are returned in tier-aware order (lower tiers
     * first) so that recipe views display progression chains logically.</p>
     *
     * @param internalName the SkyBlock internal name (e.g. {@code "WHEAT_GENERATOR_3"})
     * @return set of names to query; never null
     */
    /**
     * Returns every distinct family (explicit and implicit), each appearing once.
     */
    public Collection<FamilyInfo> getAllFamilies() {
        return memberToFamily.values().stream().distinct().toList();
    }

    public Set<String> getFamilyMembers(String internalName) {
        FamilyInfo info = memberToFamily.get(internalName);
        if (info == null) {
            return Collections.singleton(internalName);
        }
        if (!info.type().expandsForResults()) {
            return Collections.singleton(internalName);
        }
        return info.members();
    }

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
            List<String> sortedMembers = members.stream()
                    .sorted(FamilyMemberComparator.INSTANCE)
                    .toList();
            FamilyInfo info = new FamilyInfo(parent, type, Collections.unmodifiableSet(new LinkedHashSet<>(sortedMembers)));
            for (String member : members) {
                out.put(member, info);
            }
        }
    }

    private void collectDescendants(String node, Map<String, List<String>> parentToChildren, Set<String> members) {
        // members doubles as the visited set — a cyclic parents.json must not recurse forever
        if (!members.add(node)) return;
        List<String> children = parentToChildren.get(node);
        if (children == null) return;
        for (String child : children) {
            collectDescendants(child, parentToChildren, members);
        }
    }

    // -----------------------------------------------------------------
    // Implicit families from item name patterns
    // -----------------------------------------------------------------

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

        // Kuudra armor ladders: CRIMSON_HELMET → HOT_/BURNING_/FIERY_/INFERNAL_CRIMSON_HELMET.
        // Wither armor has the same parents.json shape but POWER/SPEED/TANK/WISE are
        // side-grades, not ladder steps — its prefixes fail the map lookup.
        if (isKuudraChain(root, members)) {
            return FamilyType.UPGRADE_CHAIN;
        }

        // Gemstone quality ladders: ROUGH → FLAWED → FINE → FLAWLESS → PERFECT_<gem>_GEM.
        // Their tiers are prefixes, so the numeric-suffix check below never fires.
        if (isGemstoneQualityFamily(members)) {
            return FamilyType.TIERED;
        }

        // Tiered: at least two members have numeric tier suffixes
        long numericCount = members.stream().filter(this::hasNumericTier).count();
        if (numericCount >= 2) {
            return FamilyType.TIERED;
        }

        // Accessory chain: nearly all accessory upgrade lines are recorded in
        // parents.json (e.g. SPEED_RELIC -> ARTIFACT -> RING -> TALISMAN), so they
        // arrive here rather than through the implicit suffix scan. Two suffixed
        // members suffice: chains may include odd-named top tiers
        // (SEAL_OF_THE_FAMILY, PESTHUNTER_BADGE).
        long accessoryCount = members.stream().filter(m -> getAccessoryBase(m) != null).count();
        if (accessoryCount >= 2) {
            return FamilyType.ACCESSORY_CHAIN;
        }

        return FamilyType.COLLECTION;
    }

    /** True when every member is a quality-prefixed {@code *_GEM} over one common base. */
    private static boolean isGemstoneQualityFamily(Set<String> members) {
        String base = null;
        for (String m : members) {
            if (!m.endsWith("_GEM") || extractTier(m) == 0) {
                return false;
            }
            String b = extractBaseName(m);
            if (base == null) {
                base = b;
            } else if (!base.equals(b)) {
                return false;
            }
        }
        return true;
    }

    /** True when every non-root member is {@code <KUUDRA_PREFIX>_<root>} for an armor-piece root. */
    private static boolean isKuudraChain(String root, Set<String> members) {
        if (getArmorSlotSuffix(root) == null) {
            return false;
        }
        for (String m : members) {
            if (m.equals(root)) continue;
            int prefixLen = m.length() - root.length() - 1;
            if (prefixLen <= 0 || !m.endsWith(root) || m.charAt(prefixLen) != '_'
                    || !KUUDRA_TIER_MAP.containsKey(m.substring(0, prefixLen))) {
                return false;
            }
        }
        return true;
    }

    private void buildImplicitFamilies(ConstantsRegistry constants, ItemRegistry items, Map<String, FamilyInfo> out) {
        Set<String> alreadyRegistered = new HashSet<>(out.keySet());
        buildCraftedUpgradeChains(items, alreadyRegistered, out);

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

    /**
     * Discovers named armor upgrade lines (MELON_HELMET → CROPIE_HELMET → … →
     * HELIANTHUS_HELMET) by following crafting recipes: a piece whose recipe contains
     * exactly one other piece of the same slot is an upgrade of that piece. Only
     * maximal linear chains qualify — a base with several crafted variants
     * (WITHER_HELMET → POWER/SPEED/TANK/WISE) is a set of side-grades, not a chain.
     *
     * <p>Members are registered in chain order (base first) and added to
     * {@code alreadyRegistered} so the armor-set pass below skips them.</p>
     */
    private void buildCraftedUpgradeChains(ItemRegistry items, Set<String> alreadyRegistered,
                                           Map<String, FamilyInfo> out) {
        Map<String, String> parentOf = new HashMap<>();
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (NeuItem item : items.getAllItems()) {
            String id = item.internalName();
            if (alreadyRegistered.contains(id)) continue;
            String slot = getArmorSlotSuffix(id);
            if (slot == null) continue;
            String from = craftedFromSameSlot(item, id, slot, items, alreadyRegistered);
            if (from != null) {
                parentOf.put(id, from);
                childrenOf.computeIfAbsent(from, k -> new ArrayList<>(2)).add(id);
            }
        }

        registerLinearChains(parentOf, childrenOf, out, alreadyRegistered);
    }

    /**
     * General crafted-into upgrade chains, discovered from crafting recipes across all
     * remaining items: compaction lines (DIAMOND → ENCHANTED_DIAMOND →
     * ENCHANTED_DIAMOND_BLOCK), weapon lines (ASPECT_OF_THE_END → ASPECT_OF_THE_VOID,
     * SPIDER_SWORD → … → STING), rods, wands, etc. Config-gated (see constructor).
     *
     * <p>An edge means "crafted from": a recipe with a single distinct ingredient, or —
     * for weapon/tool-like items — a recipe whose ingredients include exactly one item
     * of the same category. When alternate recipes yield two candidate parents that are
     * themselves linked (ENCHANTED_DIAMOND from DIAMOND or DIAMOND_BLOCK), the more
     * derived one wins so the chain stays linear. As with armor, only maximal linear
     * chains register — branching bases are side-grades.</p>
     */
    private void buildGeneralCraftedChains(ItemRegistry items, Map<String, FamilyInfo> out) {
        Set<String> registered = out.keySet();

        Map<String, Set<String>> candidates = new HashMap<>();
        Map<String, SkyblockItemCategory> categoryCache = new HashMap<>();
        for (NeuItem item : items.getAllItems()) {
            String id = item.internalName();
            if (registered.contains(id)) continue;
            Set<String> parents = candidateParents(item, id, items, registered, categoryCache);
            if (!parents.isEmpty()) {
                candidates.put(id, parents);
            }
        }

        Map<String, String> parentOf = new HashMap<>();
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet()) {
            String parent = resolveParent(entry.getValue(), candidates);
            if (parent != null) {
                parentOf.put(entry.getKey(), parent);
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>(2)).add(entry.getKey());
            }
        }

        registerLinearChains(parentOf, childrenOf, out, null);
    }

    /** Categories whose items may chain through a same-category ingredient. */
    private static final Set<SkyblockItemCategory> CHAINABLE_CATEGORIES = Set.of(
            SkyblockItemCategory.WEAPON, SkyblockItemCategory.TOOL,
            SkyblockItemCategory.EQUIPMENT, SkyblockItemCategory.FISHING
    );

    /**
     * Modifier words NEU adds or drops between compaction tiers ({@code CARROT_ITEM} →
     * {@code ENCHANTED_CARROT} → {@code ENCHANTED_GOLDEN_CARROT}, {@code ENCHANTED_HAY_BLOCK});
     * ignored when checking name continuity along a chain edge.
     */
    private static final Set<String> CHAIN_MODIFIER_TOKENS = Set.of("ENCHANTED", "ITEM", "BLOCK");

    /**
     * True when the child's internal name keeps every core token of the parent's — the
     * signature of a compaction/upgrade tier of the same material, as opposed to a product
     * merely crafted out of it ({@code ENCHANTED_ACACIA_LOG} → {@code ACACIA_BIRDHOUSE}
     * drops {@code LOG} and is rejected, while {@code ENCHANTED_PUMPKIN} →
     * {@code POLISHED_PUMPKIN} keeps its core token {@code PUMPKIN} and passes).
     */
    private static boolean hasTokenContinuity(String parent, String child) {
        Set<String> childTokens = new HashSet<>(Arrays.asList(child.split("[_;]+")));
        for (String token : parent.split("[_;]+")) {
            if (token.isEmpty() || CHAIN_MODIFIER_TOKENS.contains(token)) continue;
            if (!childTokens.contains(token)) return false;
        }
        return true;
    }

    /** Possible crafted-from parents of the item, one candidate per qualifying recipe. */
    private Set<String> candidateParents(NeuItem item, String id, ItemRegistry items,
                                         Set<String> registered,
                                         Map<String, SkyblockItemCategory> categoryCache) {
        Set<String> result = Collections.emptySet();
        SkyblockItemCategory category = null;
        boolean categoryResolved = false;

        NeuRecipe direct = item.recipe();
        List<NeuRecipe> extra = item.recipes();
        int recipeCount = (direct != null ? 1 : 0) + (extra != null ? extra.size() : 0);
        for (int i = 0; i < recipeCount; i++) {
            NeuRecipe recipe = direct != null ? (i == 0 ? direct : extra.get(i - 1))
                    : extra.get(i);
            if (!(recipe instanceof NeuRecipe.CraftingRecipe crafting)) continue;

            // Distinct known, unregistered ingredients of this one recipe
            Set<String> distinct = new HashSet<>(4);
            for (String cell : crafting.grid().values()) {
                if (cell == null || cell.isEmpty()) continue;
                int colon = cell.indexOf(':');
                String ingredient = colon >= 0 ? cell.substring(0, colon) : cell;
                if (ingredient.equals(id) || registered.contains(ingredient)
                        || items.getByInternalName(ingredient).isEmpty()) continue;
                distinct.add(ingredient);
            }

            String candidate = null;
            if (distinct.size() == 1) {
                String only = distinct.iterator().next();
                if (!categoryResolved) {
                    category = ItemCategoryResolver.resolve(item);
                    categoryResolved = true;
                }
                // Weapon/tool-like upgrades legitimately rename (ASPECT_OF_THE_END →
                // ASPECT_OF_THE_VOID); everything else must keep the material's name.
                if (CHAINABLE_CATEGORIES.contains(category) || hasTokenContinuity(only, id)) {
                    candidate = only;
                }
            } else if (distinct.size() > 1) {
                if (!categoryResolved) {
                    category = ItemCategoryResolver.resolve(item);
                    categoryResolved = true;
                }
                if (CHAINABLE_CATEGORIES.contains(category)) {
                    for (String ingredient : distinct) {
                        SkyblockItemCategory ingredientCategory = categoryCache.computeIfAbsent(
                                ingredient, ing -> items.getByInternalName(ing)
                                        .map(ItemCategoryResolver::resolve)
                                        .orElse(SkyblockItemCategory.UNKNOWN));
                        if (ingredientCategory == category) {
                            if (candidate != null) {
                                candidate = null; // two same-category ingredients: ambiguous
                                break;
                            }
                            candidate = ingredient;
                        }
                    }
                }
            }
            if (candidate != null) {
                if (result.isEmpty()) result = new HashSet<>(2);
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Reduces a candidate-parent set to one parent. Two candidates resolve when one is
     * itself crafted from the other (the more derived wins); anything else is ambiguous.
     */
    private static String resolveParent(Set<String> parents, Map<String, Set<String>> candidates) {
        if (parents.size() == 1) {
            return parents.iterator().next();
        }
        if (parents.size() == 2) {
            Iterator<String> it = parents.iterator();
            String a = it.next();
            String b = it.next();
            boolean bFromA = candidates.getOrDefault(b, Collections.emptySet()).contains(a);
            boolean aFromB = candidates.getOrDefault(a, Collections.emptySet()).contains(b);
            if (bFromA != aFromB) {
                return bFromA ? b : a;
            }
        }
        return null;
    }

    /**
     * Registers every maximal linear chain in the crafted-from forest as an
     * UPGRADE_CHAIN family, members in chain order (base first). Chains touching a
     * branching node are skipped entirely — branches are side-grades.
     */
    private static void registerLinearChains(Map<String, String> parentOf,
                                             Map<String, List<String>> childrenOf,
                                             Map<String, FamilyInfo> out,
                                             Set<String> alsoMark) {
        for (Map.Entry<String, List<String>> entry : childrenOf.entrySet()) {
            String root = entry.getKey();
            if (parentOf.containsKey(root)) continue; // mid-chain node, not a root

            List<String> chain = new ArrayList<>(4);
            chain.add(root);
            List<String> children = entry.getValue();
            while (children != null && children.size() == 1) {
                String next = children.get(0);
                chain.add(next);
                children = childrenOf.get(next);
            }
            if (children != null) continue; // hit a branching node: side-grades, skip

            Set<String> members = Collections.unmodifiableSet(new LinkedHashSet<>(chain));
            FamilyInfo info = new FamilyInfo(root, FamilyType.UPGRADE_CHAIN, members);
            for (String member : chain) {
                out.put(member, info);
                if (alsoMark != null) {
                    alsoMark.add(member);
                }
            }
        }
    }

    /**
     * The single same-slot armor piece appearing in the item's crafting recipes, or null
     * when there is none, several distinct ones, or the candidate is unknown/already in
     * an explicit family.
     */
    private String craftedFromSameSlot(NeuItem item, String id, String slot,
                                       ItemRegistry items, Set<String> alreadyRegistered) {
        String found = null;
        if (item.recipe() instanceof NeuRecipe.CraftingRecipe crafting) {
            found = sameSlotIngredient(crafting, id, slot, items, alreadyRegistered, found);
            if (found == CONFLICT) return null;
        }
        if (item.recipes() != null) {
            for (NeuRecipe recipe : item.recipes()) {
                if (recipe instanceof NeuRecipe.CraftingRecipe crafting) {
                    found = sameSlotIngredient(crafting, id, slot, items, alreadyRegistered, found);
                    if (found == CONFLICT) return null;
                }
            }
        }
        return found;
    }

    /** Sentinel: several distinct same-slot ingredients — no unambiguous upgrade parent. */
    private static final String CONFLICT = "\0CONFLICT";

    private String sameSlotIngredient(NeuRecipe.CraftingRecipe recipe, String id, String slot,
                                      ItemRegistry items, Set<String> alreadyRegistered, String found) {
        for (String cell : recipe.grid().values()) {
            if (cell == null || cell.isEmpty()) continue;
            int colon = cell.indexOf(':');
            String ingredient = colon >= 0 ? cell.substring(0, colon) : cell;
            if (!ingredient.endsWith(slot) || ingredient.equals(id)) continue;
            if (alreadyRegistered.contains(ingredient)
                    || items.getByInternalName(ingredient).isEmpty()) continue;
            if (found != null && !found.equals(ingredient)) return CONFLICT;
            found = ingredient;
        }
        return found;
    }

    // -----------------------------------------------------------------
    // Pattern detection helpers
    // -----------------------------------------------------------------

    private void registerImplicitFamily(Map<String, Set<String>> groups, FamilyType type,
                                        String idSuffix, int minSize, Map<String, FamilyInfo> out) {
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() >= minSize) {
                List<String> sorted = entry.getValue().stream()
                        .sorted(FamilyMemberComparator.INSTANCE)
                        .toList();
                Set<String> members = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
                FamilyInfo info = new FamilyInfo(entry.getKey() + idSuffix, type, members);
                for (String member : members) {
                    out.put(member, info);
                }
            }
        }
    }

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
            return isAllDigits(suffix);
        }
        return false;
    }

    private String getArmorBase(String id) {
        String suffix = getArmorSlotSuffix(id);
        return suffix != null ? id.substring(0, id.length() - suffix.length()) : null;
    }

    private static String getArmorSlotSuffix(String id) {
        for (String suffix : ARMOR_SLOT_SUFFIXES) {
            if (id.endsWith(suffix)) return suffix;
        }
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

    /**
     * Comparator for SkyBlock internal names that orders tiered family members
     * logically (lower tiers first).
     */
    private static final class FamilyMemberComparator implements Comparator<String> {

        public static final FamilyMemberComparator INSTANCE = new FamilyMemberComparator();

        private FamilyMemberComparator() {
        }

        @Override
        public int compare(String a, String b) {
            int tierA = FamilyResolver.extractTier(a);
            int tierB = FamilyResolver.extractTier(b);
            if (tierA != tierB) {
                return Integer.compare(tierA, tierB);
            }
            return a.compareTo(b);
        }
    }
}
