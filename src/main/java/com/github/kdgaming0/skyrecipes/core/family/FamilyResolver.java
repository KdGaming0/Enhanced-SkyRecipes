package com.github.kdgaming0.skyrecipes.core.family;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.github.kdgaming0.skyrecipes.core.search.ItemCategoryResolver;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves item families: which items belong together, and how ({@link FamilyType}).
 *
 * <p>Families drive two behaviors — merging recipes when the player presses <b>R</b> on a
 * member, and collapsing members into one stack in the RRV item list (see
 * {@code SkyblockStackGroups}). They are discovered in four passes; earlier passes win:</p>
 *
 * <ol>
 *   <li>Explicit families curated in NEU's {@code constants/parents.json}</li>
 *   <li>Museum upgrade chains from {@code constants/museum.json}</li>
 *   <li>Implicit families from item name patterns (armor sets, pets, accessories, drills)</li>
 *   <li>General crafted-into upgrade chains (config-gated)</li>
 * </ol>
 *
 * <p>Immutable after construction — safe to share across threads.</p>
 */
public final class FamilyResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FamilyResolver.class);

    /**
     * Members matching these prefixes never group: their families look tiered but the
     * "tiers" are unrelated items (dungeon music discs) or vanilla material variants
     * (the wood→diamond hoe line). The family is left unregistered rather than claimed.
     */
    private static final Set<String> NEVER_GROUP_PREFIXES = Set.of("DUNGEON_DISC_", "WOOD_HOE");

    /**
     * Roots of curated families whose children are mutually exclusive side-grades of the
     * base item, not an upgrade ladder: Necron's Blade → Astraea/Hyperion/Scylla/Valkyrie,
     * Wither armor → Necron's/Storm's/Goldor's/Maxor's pieces.
     */
    private static final Set<String> BRANCHING_ROOTS = Set.of(
            "NECRON_BLADE",
            "WITHER_HELMET", "WITHER_CHESTPLATE", "WITHER_LEGGINGS", "WITHER_BOOTS"
    );

    /** Accessory chain suffix → tier: ZOMBIE_TALISMAN(1) → RING(2) → ARTIFACT(3) → … */
    private static final Map<String, Integer> ACCESSORY_SUFFIX_TIERS = Map.of(
            "TALISMAN", 1,
            "RING", 2,
            "ARTIFACT", 3,
            "RELIC", 4,
            "HEIRLOOM", 5,
            "CHRONOMICON", 6
    );

    /**
     * Accessory words that also appear as name <em>prefixes</em> on higher tiers
     * (RING_OF_COINS, ARTIFACT_POTION_AFFINITY). TALISMAN is deliberately absent:
     * tier-1 accessories are always suffix-named, while many non-accessories
     * (TALISMAN_ENRICHMENT_*) start with the word.
     */
    private static final Map<String, Integer> ACCESSORY_PREFIX_TIERS = Map.of(
            "RING", 2,
            "ARTIFACT", 3,
            "RELIC", 4,
            "HEIRLOOM", 5,
            "CHRONOMICON", 6
    );

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

    /** Trophy fish medal ladder: BLOBFISH_BRONZE → SILVER → GOLD → DIAMOND. */
    private static final Map<String, Integer> TROPHY_TIER_MAP = Map.of(
            "BRONZE", 1,
            "SILVER", 2,
            "GOLD", 3,
            "DIAMOND", 4
    );

    /** In display order — also the sort order of armor-set members. */
    private static final String[] ARMOR_SLOT_SUFFIXES = {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};

    private final Map<String, FamilyInfo> memberToFamily;

    /**
     * @param groupCraftedChains also build general crafted-into upgrade chains (compaction
     *                           lines, weapon upgrades) — see {@link #buildGeneralCraftedChains}
     */
    public FamilyResolver(ConstantsRegistry constants, ItemRegistry items, boolean groupCraftedChains) {
        Map<String, FamilyInfo> map = new HashMap<>();
        if (constants != null) {
            buildExplicitFamilies(constants, items, map);
        }
        if (items != null) {
            if (constants != null) {
                buildMuseumChains(constants, items, map);
            }
            buildImplicitFamilies(items, map);
            if (groupCraftedChains) {
                buildGeneralCraftedChains(items, map);
            }
        }
        this.memberToFamily = Collections.unmodifiableMap(map);
        LOGGER.info("FamilyResolver built: {} items in {} families",
                map.size(), (int) map.values().stream().map(FamilyInfo::familyId).distinct().count());
    }

    /**
     * Returns the set of internal names to search for recipes when the player presses
     * <b>R</b> on the given item: the whole family in tier order for expanding family
     * types, otherwise a singleton of the given name. Never null.
     */
    public Set<String> getFamilyMembers(String internalName) {
        FamilyInfo info = memberToFamily.get(internalName);
        if (info == null || !info.type().expandsForResults()) {
            return Collections.singleton(internalName);
        }
        return info.members();
    }

    /** Returns every distinct family (explicit and implicit), each appearing once. */
    public Collection<FamilyInfo> getAllFamilies() {
        return memberToFamily.values().stream().distinct().toList();
    }

    // -----------------------------------------------------------------
    // Tier / base-name extraction (shared with sorting and group naming)
    // -----------------------------------------------------------------

    /**
     * Extracts the numeric tier from an internal name: a numeric {@code _N}/{@code ;N}
     * suffix (minions, pets, enchantments, drills), else the accessory suffix/prefix rank
     * (TALISMAN=1 … CHRONOMICON=6), else the trophy medal (BRONZE=1 … DIAMOND=4), else the
     * gemstone quality prefix on {@code *_GEM} items (ROUGH=1 … PERFECT=5), else the
     * Kuudra prefix on armor pieces (HOT=1 … INFERNAL=4). Returns 0 when no tier is found.
     */
    public static int extractTier(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return 0;
        }

        int sep = numericSuffixSeparator(internalName);
        if (sep >= 0) {
            try {
                return Integer.parseInt(internalName.substring(sep + 1));
            } catch (NumberFormatException ignored) {
                // absurdly long digit run — fall through to the pattern checks
            }
        }

        for (Map.Entry<String, Integer> entry : ACCESSORY_SUFFIX_TIERS.entrySet()) {
            if (internalName.endsWith("_" + entry.getKey())) {
                return entry.getValue();
            }
        }
        Integer accessoryPrefixTier = accessoryPrefixTier(internalName);
        if (accessoryPrefixTier != null) {
            return accessoryPrefixTier;
        }

        int lastUnderscore = internalName.lastIndexOf('_');
        if (lastUnderscore > 0) {
            Integer medal = TROPHY_TIER_MAP.get(internalName.substring(lastUnderscore + 1));
            if (medal != null) {
                return medal;
            }
        }

        Integer gemstone = leadingWordTier(internalName, GEMSTONE_TIER_MAP, internalName.endsWith("_GEM"));
        if (gemstone != null) {
            return gemstone;
        }
        Integer kuudra = leadingWordTier(internalName, KUUDRA_TIER_MAP, isArmorPiece(internalName));
        if (kuudra != null) {
            return kuudra;
        }
        return 0;
    }

    /**
     * Extracts the family base name from an internal name by stripping the tier marker:
     * numeric suffix ({@code WHEAT_GENERATOR_3} → {@code WHEAT_GENERATOR}), accessory
     * suffix/prefix ({@code ZOMBIE_TALISMAN} → {@code ZOMBIE}, {@code RING_OF_COINS} →
     * {@code COINS}), armor slot suffix ({@code DIAMOND_CHESTPLATE} → {@code DIAMOND}),
     * or gemstone quality prefix ({@code FINE_RUBY_GEM} → {@code RUBY_GEM}).
     * Returns the full name when no marker is found.
     */
    public static String extractBaseName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return "";
        }

        int sep = numericSuffixSeparator(internalName);
        if (sep >= 0) {
            return internalName.substring(0, sep);
        }

        for (String suffix : ACCESSORY_SUFFIX_TIERS.keySet()) {
            String withUnderscore = "_" + suffix;
            if (internalName.endsWith(withUnderscore)) {
                return internalName.substring(0, internalName.length() - withUnderscore.length());
            }
        }
        if (accessoryPrefixTier(internalName) != null) {
            String rest = internalName.substring(internalName.indexOf('_') + 1);
            return rest.startsWith("OF_") ? rest.substring(3) : rest;
        }

        String armorSuffix = getArmorSlotSuffix(internalName);
        if (armorSuffix != null) {
            return internalName.substring(0, internalName.length() - armorSuffix.length());
        }

        if (internalName.endsWith("_GEM")
                && leadingWordTier(internalName, GEMSTONE_TIER_MAP, true) != null) {
            return internalName.substring(internalName.indexOf('_') + 1);
        }

        return internalName;
    }

    /**
     * Index of the {@code _} or {@code ;} introducing an all-digit tier suffix, or -1.
     * {@code ;} (pets, enchantments) takes precedence over {@code _} (minions, drills).
     */
    private static int numericSuffixSeparator(String id) {
        int semi = id.lastIndexOf(';');
        if (semi >= 0 && semi < id.length() - 1 && isAllDigits(id.substring(semi + 1))) {
            return semi;
        }
        int underscore = id.lastIndexOf('_');
        if (underscore >= 0 && underscore < id.length() - 1 && isAllDigits(id.substring(underscore + 1))) {
            return underscore;
        }
        return -1;
    }

    /** The tier of the name's first {@code _}-delimited word per {@code tiers}, when {@code eligible}. */
    private static Integer leadingWordTier(String id, Map<String, Integer> tiers, boolean eligible) {
        if (!eligible) return null;
        int firstUnderscore = id.indexOf('_');
        if (firstUnderscore <= 0) return null;
        return tiers.get(id.substring(0, firstUnderscore));
    }

    /** Tier for prefix-form accessory names ({@code RING_OF_COINS} → 2), or null. */
    private static Integer accessoryPrefixTier(String internalName) {
        int firstUnderscore = internalName.indexOf('_');
        if (firstUnderscore <= 0 || firstUnderscore == internalName.length() - 1) return null;
        return ACCESSORY_PREFIX_TIERS.get(internalName.substring(0, firstUnderscore));
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return !s.isEmpty();
    }

    // -----------------------------------------------------------------
    // Member ordering
    // -----------------------------------------------------------------

    private record MemberSortKey(int tier, int rarity, int armorSlot, String name)
            implements Comparable<MemberSortKey> {
        private static final Comparator<MemberSortKey> ORDER = Comparator
                .comparingInt(MemberSortKey::tier)
                .thenComparingInt(MemberSortKey::rarity)
                .thenComparingInt(MemberSortKey::armorSlot)
                .thenComparing(MemberSortKey::name);

        @Override
        public int compareTo(MemberSortKey other) {
            return ORDER.compare(this, other);
        }
    }

    /**
     * Orders members by tier, then rarity (from the item's lore), then armor slot
     * (helmet → boots, so armor sets display in wear order), then name. Sort keys are
     * computed once per member — the rarity lookup parses lore and is too expensive to
     * run per comparison.
     */
    private static Set<String> tierOrderedMembers(Collection<String> members, ItemRegistry items) {
        Map<String, MemberSortKey> keys = new HashMap<>(members.size() * 2);
        for (String member : members) {
            keys.put(member, new MemberSortKey(
                    extractTier(member), rarityOrdinal(items, member), armorSlotRank(member), member));
        }
        List<String> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(keys::get));
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    /** Rarity position for sorting; 0 when the registry or the rarity is unknown. */
    private static int rarityOrdinal(ItemRegistry items, String id) {
        SkyblockRarity rarity = rarityOf(items, id);
        return rarity != null ? rarity.ordinal() + 1 : 0;
    }

    private static SkyblockRarity rarityOf(ItemRegistry items, String id) {
        if (items == null) return null;
        NeuItem item = items.getByInternalName(id).orElse(null);
        if (item == null || item.lore() == null || item.lore().isEmpty()) return null;
        return SkyblockRarity.fromLoreOrNull(item.lore().getLast());
    }

    /** Position of the armor slot suffix in wear order (helmet first); -1 for non-armor. */
    public static int armorSlotRank(String id) {
        for (int i = 0; i < ARMOR_SLOT_SUFFIXES.length; i++) {
            if (id.endsWith(ARMOR_SLOT_SUFFIXES[i])) return i;
        }
        return -1;
    }

    // -----------------------------------------------------------------
    // Pass 1 — explicit families from parents.json
    // -----------------------------------------------------------------

    private static void buildExplicitFamilies(ConstantsRegistry constants, ItemRegistry items,
                                              Map<String, FamilyInfo> out) {
        Map<String, List<String>> parentToChildren = constants.getAllParents();
        if (parentToChildren == null || parentToChildren.isEmpty()) return;

        // Child->parent map for root detection; first parent wins for duplicate children.
        Map<String, String> childToParent = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : parentToChildren.entrySet()) {
            for (String child : entry.getValue()) {
                childToParent.putIfAbsent(child, entry.getKey());
            }
        }

        for (String parent : parentToChildren.keySet()) {
            if (childToParent.containsKey(parent)) continue; // not a root

            Set<String> members = new LinkedHashSet<>();
            collectDescendants(parent, parentToChildren, members);
            if (members.size() < 2) continue;

            FamilyType type = classifyExplicitFamily(parent, members, items);
            if (type == null) continue; // never-group family: leave members unclaimed

            FamilyInfo info = new FamilyInfo(parent, type, tierOrderedMembers(members, items));
            for (String member : info.members()) {
                out.put(member, info);
            }
        }
    }

    private static void collectDescendants(String node, Map<String, List<String>> parentToChildren,
                                           Set<String> members) {
        // members doubles as the visited set — a cyclic parents.json must not recurse forever
        if (!members.add(node)) return;
        List<String> children = parentToChildren.get(node);
        if (children == null) return;
        for (String child : children) {
            collectDescendants(child, parentToChildren, members);
        }
    }

    /**
     * Classifies a curated parents.json family, or returns null for families that must
     * not group <em>and</em> must not claim their members — vanilla metadata variants
     * (wool/glass colors) and the {@link #NEVER_GROUP_PREFIXES} denylist. Leaving those
     * members unclaimed lets a later pass still chain a base form into its upgrade line
     * (COAL → ENCHANTED_COAL → ENCHANTED_COAL_BLOCK).
     */
    private static FamilyType classifyExplicitFamily(String root, Set<String> members, ItemRegistry items) {
        for (String m : members) {
            if (isVanillaMetaVariant(m)) return null;
            for (String prefix : NEVER_GROUP_PREFIXES) {
                if (m.startsWith(prefix)) return null;
            }
        }

        for (String m : members) {
            if (m.startsWith("STARRED_")) {
                return FamilyType.STARRED;
            }
        }

        if (BRANCHING_ROOTS.contains(root)) {
            return FamilyType.BRANCHING;
        }

        // Kuudra armor ladders: CRIMSON_HELMET → HOT_/BURNING_/FIERY_/INFERNAL_CRIMSON_HELMET.
        if (isKuudraChain(root, members)) {
            return FamilyType.UPGRADE_CHAIN;
        }

        // Gemstone quality ladders: ROUGH → FLAWED → FINE → FLAWLESS → PERFECT_<gem>_GEM.
        // Their tiers are prefixes, so the numeric-suffix check below never fires.
        if (isGemstoneQualityFamily(members)) {
            return FamilyType.TIERED;
        }

        long numericCount = members.stream().filter(m -> numericSuffixSeparator(m) >= 0).count();
        if (numericCount >= 2) {
            return FamilyType.TIERED;
        }

        // Accessory chain: nearly all accessory upgrade lines are curated here rather than
        // found by the implicit suffix scan. Two suffixed or prefixed (RING_OF_COINS)
        // members suffice: chains may include odd-named top tiers (SEAL_OF_THE_FAMILY).
        long accessoryCount = members.stream()
                .filter(m -> getAccessoryBase(m) != null || accessoryPrefixTier(m) != null)
                .count();
        if (accessoryCount >= 2) {
            return FamilyType.ACCESSORY_CHAIN;
        }

        // Rarity ladder: identical display names, tiered only by rarity
        // (BEASTMASTER_CREST_COMMON … LEGENDARY are all "Beastmaster Crest").
        if (isRarityLadder(members, items)) {
            return FamilyType.TIERED;
        }

        // Everything else NEU curated as a family (dyes, party hat colors, trophy
        // fish medals, …) still collapses in the item list, without R-expansion.
        return FamilyType.VARIANT_SET;
    }

    /**
     * True for 1.8 metadata ids like {@code WOOL-5} or {@code LOG-2} — vanilla
     * color/material variants that should never group with their base item.
     */
    private static boolean isVanillaMetaVariant(String id) {
        int dash = id.lastIndexOf('-');
        return dash > 0 && dash < id.length() - 1 && isAllDigits(id.substring(dash + 1));
    }

    /** True when all members share one display name but span at least two rarities. */
    private static boolean isRarityLadder(Set<String> members, ItemRegistry items) {
        if (items == null) return false;
        String sharedName = null;
        Set<SkyblockRarity> rarities = new HashSet<>();
        for (String m : members) {
            NeuItem item = items.getByInternalName(m).orElse(null);
            if (item == null || item.displayName() == null) return false;
            String name = TextUtil.stripColorCodes(item.displayName()).trim();
            if (sharedName == null) {
                sharedName = name;
            } else if (!sharedName.equals(name)) {
                return false;
            }
            SkyblockRarity rarity = rarityOf(items, m);
            if (rarity == null) return false;
            rarities.add(rarity);
        }
        return rarities.size() >= 2;
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
        if (!isArmorPiece(root)) {
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

    // -----------------------------------------------------------------
    // Pass 2 — museum upgrade chains from museum.json "children"
    // -----------------------------------------------------------------

    /**
     * Registers upgrade lines curated in NEU's {@code constants/museum.json}
     * {@code children} map (item → the item it upgrades from). This is the only data
     * source for lines that upgrade outside crafting — shop-bought progressions like
     * HYDRO_CAN_1000 → … → AQUAMASTER_HYDROMAX or the Mithril/Titanium pickaxes.
     *
     * <p>Entries whose ends are not both known, unregistered items are skipped —
     * the map also relates museum armor-set names, which are not item ids.</p>
     */
    private static void buildMuseumChains(ConstantsRegistry constants, ItemRegistry items,
                                          Map<String, FamilyInfo> out) {
        Map<String, String> upgradedFrom = constants.getMuseumChildren();
        if (upgradedFrom == null || upgradedFrom.isEmpty()) return;

        Map<String, String> parentOf = new HashMap<>();
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (Map.Entry<String, String> entry : upgradedFrom.entrySet()) {
            String item = entry.getKey();
            String base = entry.getValue();
            if (base == null || base.isEmpty() || base.equals(item)) continue;
            if (out.containsKey(item) || out.containsKey(base)) continue;
            if (items.getByInternalName(item).isEmpty()
                    || items.getByInternalName(base).isEmpty()) continue;
            parentOf.put(item, base);
            childrenOf.computeIfAbsent(base, k -> new ArrayList<>(2)).add(item);
        }

        registerLinearChains(parentOf, childrenOf, out, null);
    }

    // -----------------------------------------------------------------
    // Pass 3 — implicit families from item name patterns
    // -----------------------------------------------------------------

    private static void buildImplicitFamilies(ItemRegistry items, Map<String, FamilyInfo> out) {
        Set<String> alreadyRegistered = new HashSet<>(out.keySet());
        buildCraftedUpgradeChains(items, alreadyRegistered, out);

        Map<String, Set<String>> armorSets = new HashMap<>();
        Map<String, Set<String>> petFamilies = new HashMap<>();
        Map<String, Set<String>> accessoryChains = new HashMap<>();
        Map<String, Set<String>> drillFamilies = new HashMap<>();

        // First matching pattern claims the item.
        for (NeuItem item : items.getAllItems()) {
            String id = item.internalName();
            if (alreadyRegistered.contains(id)) continue;

            String armorSuffix = getArmorSlotSuffix(id);
            if (armorSuffix != null) {
                String base = id.substring(0, id.length() - armorSuffix.length());
                armorSets.computeIfAbsent(base, k -> new HashSet<>()).add(id);
                continue;
            }
            String petBase = getPetBase(id);
            if (petBase != null) {
                petFamilies.computeIfAbsent(petBase, k -> new HashSet<>()).add(id);
                continue;
            }
            String accessoryBase = getAccessoryBase(id);
            if (accessoryBase != null) {
                accessoryChains.computeIfAbsent(accessoryBase, k -> new HashSet<>()).add(id);
                continue;
            }
            String drillBase = getDrillBase(id);
            if (drillBase != null) {
                drillFamilies.computeIfAbsent(drillBase, k -> new HashSet<>()).add(id);
            }
        }

        registerImplicitFamilies(armorSets, FamilyType.ARMOR_SET, "_SET", items, out);
        registerImplicitFamilies(petFamilies, FamilyType.TIERED, "_PET", items, out);
        registerImplicitFamilies(accessoryChains, FamilyType.ACCESSORY_CHAIN, "_ACCESSORY", items, out);
        registerImplicitFamilies(drillFamilies, FamilyType.TIERED, "_DRILL", items, out);
    }

    /** Registers each group of ≥2 members as a family named {@code <base><idSuffix>}. */
    private static void registerImplicitFamilies(Map<String, Set<String>> groups, FamilyType type,
                                                 String idSuffix, ItemRegistry items,
                                                 Map<String, FamilyInfo> out) {
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            FamilyInfo info = new FamilyInfo(entry.getKey() + idSuffix, type,
                    tierOrderedMembers(entry.getValue(), items));
            for (String member : info.members()) {
                out.put(member, info);
            }
        }
    }

    /**
     * Discovers named armor upgrade lines (MELON_HELMET → CROPIE_HELMET → … →
     * HELIANTHUS_HELMET) by following crafting recipes: a piece whose recipe contains
     * exactly one other piece of the same slot is an upgrade of that piece. Only
     * maximal linear chains qualify — a base with several crafted variants is a set
     * of side-grades, not a chain.
     *
     * <p>Members are registered in chain order (base first) and added to
     * {@code alreadyRegistered} so the armor-set pass skips them.</p>
     */
    private static void buildCraftedUpgradeChains(ItemRegistry items, Set<String> alreadyRegistered,
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
     * The single same-slot armor piece appearing in the item's crafting recipes, or null
     * when there is none, several distinct ones, or the candidate is unknown/already in
     * an explicit family.
     */
    private static String craftedFromSameSlot(NeuItem item, String id, String slot,
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

    private static String sameSlotIngredient(NeuRecipe.CraftingRecipe recipe, String id, String slot,
                                             ItemRegistry items, Set<String> alreadyRegistered,
                                             String found) {
        for (String cell : recipe.grid().values()) {
            String ingredient = ingredientId(cell);
            if (ingredient == null || !ingredient.endsWith(slot) || ingredient.equals(id)) continue;
            if (alreadyRegistered.contains(ingredient)
                    || items.getByInternalName(ingredient).isEmpty()) continue;
            if (found != null && !found.equals(ingredient)) return CONFLICT;
            found = ingredient;
        }
        return found;
    }

    // -----------------------------------------------------------------
    // Pass 4 — general crafted upgrade chains (config-gated)
    // -----------------------------------------------------------------

    /**
     * General crafted-into upgrade chains, discovered from crafting recipes across all
     * remaining items: compaction lines (DIAMOND → ENCHANTED_DIAMOND →
     * ENCHANTED_DIAMOND_BLOCK), weapon lines (ASPECT_OF_THE_END → ASPECT_OF_THE_VOID,
     * SPIDER_SWORD → … → STING), rods, wands, etc.
     *
     * <p>An edge means "crafted from" — see {@link #chainParent}. When alternate recipes
     * yield two candidate parents that are themselves linked (ENCHANTED_DIAMOND from
     * DIAMOND or DIAMOND_BLOCK), the more derived one wins so the chain stays linear.
     * As with armor, only maximal linear chains register — branching bases are side-grades.</p>
     */
    private static void buildGeneralCraftedChains(ItemRegistry items, Map<String, FamilyInfo> out) {
        Set<String> registered = out.keySet();

        Map<String, Set<String>> candidates = new HashMap<>();
        Set<String> vanillaIds = new HashSet<>();
        Map<String, SkyblockItemCategory> categories = new HashMap<>();
        for (NeuItem item : items.getAllItems()) {
            String id = item.internalName();
            if (registered.contains(id)) continue;
            if (item.vanilla()) vanillaIds.add(id);
            Set<String> parents = candidateParents(item, id, items, registered, categories);
            if (!parents.isEmpty()) {
                candidates.put(id, parents);
            }
        }

        // Vanilla items may be chain bases (COAL → ENCHANTED_COAL) but never chain
        // children: their crafted vanilla side-products (Bonemeal from Bone, panes from
        // glass) are not upgrades, and such edges would branch and thereby kill the real
        // compaction line. Their candidates still feed resolveParent's derivation checks.
        Map<String, String> parentOf = new HashMap<>();
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet()) {
            if (vanillaIds.contains(entry.getKey())) continue;
            String parent = resolveParent(entry.getValue(), candidates);
            if (parent != null) {
                parentOf.put(entry.getKey(), parent);
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>(2)).add(entry.getKey());
            }
        }

        // Vanilla item↔block pairs (REDSTONE ↔ REDSTONE_BLOCK) craft into each other
        // and form 2-cycles that would leave the chain rootless. Drop both edges: the
        // enchanted chain hangs off whichever form resolveParent picked as its base.
        for (Iterator<Map.Entry<String, String>> it = parentOf.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getKey().equals(parentOf.get(entry.getValue()))) {
                List<String> siblings = childrenOf.get(entry.getValue());
                if (siblings != null) {
                    siblings.remove(entry.getKey());
                    if (siblings.isEmpty()) childrenOf.remove(entry.getValue());
                }
                it.remove();
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

    /** Possible crafted-from parents of the item, one candidate per qualifying recipe. */
    private static Set<String> candidateParents(NeuItem item, String id, ItemRegistry items,
                                                Set<String> registered,
                                                Map<String, SkyblockItemCategory> categories) {
        Set<String> result = Collections.emptySet();
        NeuRecipe direct = item.recipe();
        List<NeuRecipe> extras = item.recipes();
        int extraCount = extras != null ? extras.size() : 0;
        for (int i = direct != null ? -1 : 0; i < extraCount; i++) {
            NeuRecipe recipe = i < 0 ? direct : extras.get(i);
            if (!(recipe instanceof NeuRecipe.CraftingRecipe crafting)) continue;
            String candidate = chainParent(crafting, item, id, items, registered, categories);
            if (candidate != null) {
                if (result.isEmpty()) result = new HashSet<>(2);
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * The crafted-from parent one recipe implies, or null. A recipe with a single
     * distinct ingredient makes that ingredient the parent — but only when the names
     * show continuity (a compaction tier of the same material, not a product merely
     * crafted out of it). Weapon/tool-like items skip the continuity check since
     * upgrades legitimately rename (ASPECT_OF_THE_END → ASPECT_OF_THE_VOID); for those,
     * a multi-ingredient recipe also chains when exactly one ingredient shares the
     * item's category (SPIDER_SWORD → … → STING).
     */
    private static String chainParent(NeuRecipe.CraftingRecipe crafting, NeuItem item, String id,
                                      ItemRegistry items, Set<String> registered,
                                      Map<String, SkyblockItemCategory> categories) {
        Set<String> distinct = new HashSet<>(4);
        for (String cell : crafting.grid().values()) {
            String ingredient = ingredientId(cell);
            if (ingredient == null || ingredient.equals(id) || registered.contains(ingredient)
                    || items.getByInternalName(ingredient).isEmpty()) continue;
            distinct.add(ingredient);
        }
        if (distinct.isEmpty()) return null;

        SkyblockItemCategory category =
                categories.computeIfAbsent(id, k -> ItemCategoryResolver.resolve(item));
        if (distinct.size() == 1) {
            String only = distinct.iterator().next();
            if (CHAINABLE_CATEGORIES.contains(category) || hasTokenContinuity(only, id)
                    || hasDisplayContinuity(only, item, items)) {
                return only;
            }
            return null;
        }

        if (!CHAINABLE_CATEGORIES.contains(category)) return null;
        String candidate = null;
        for (String ingredient : distinct) {
            SkyblockItemCategory ingredientCategory = categories.computeIfAbsent(
                    ingredient, ing -> items.getByInternalName(ing)
                            .map(ItemCategoryResolver::resolve)
                            .orElse(SkyblockItemCategory.UNKNOWN));
            if (ingredientCategory == category) {
                if (candidate != null) return null; // two same-category ingredients: ambiguous
                candidate = ingredient;
            }
        }
        return candidate;
    }

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

    /**
     * Display-name fallback for {@link #hasTokenContinuity}: NEU's 1.8 Bukkit ids can
     * differ from the material's real name ({@code MYCEL} vs "Mycelium"), so when id
     * tokens miss, compare the two items' display names instead.
     */
    private static boolean hasDisplayContinuity(String parentId, NeuItem child, ItemRegistry items) {
        NeuItem parent = items.getByInternalName(parentId).orElse(null);
        if (parent == null || parent.displayName() == null || child.displayName() == null) {
            return false;
        }
        Set<String> childTokens = displayTokens(child.displayName());
        Set<String> parentTokens = displayTokens(parent.displayName());
        if (parentTokens.isEmpty()) return false;
        for (String token : parentTokens) {
            if (CHAIN_MODIFIER_TOKENS.contains(token)) continue;
            if (!childTokens.contains(token)) return false;
        }
        return true;
    }

    private static Set<String> displayTokens(String display) {
        String stripped = TextUtil.stripColorCodes(display).toUpperCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>(Arrays.asList(stripped.split("[^A-Z0-9]+")));
        tokens.remove("");
        return tokens;
    }

    /**
     * Reduces a candidate-parent set to one parent. Two candidates resolve when one is
     * itself crafted from the other (the more derived wins). When each is crafted from
     * the other — a vanilla item↔block pair like REDSTONE/REDSTONE_BLOCK — the simpler
     * base form wins. Anything else is ambiguous.
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
            if (bFromA) {
                return simplerName(a, b);
            }
        }
        return null;
    }

    /** The name with fewer tokens (then shorter, then lexicographically first). */
    private static String simplerName(String a, String b) {
        int tokensA = a.split("[_;]+").length;
        int tokensB = b.split("[_;]+").length;
        if (tokensA != tokensB) return tokensA < tokensB ? a : b;
        if (a.length() != b.length()) return a.length() < b.length() ? a : b;
        return a.compareTo(b) <= 0 ? a : b;
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

    // -----------------------------------------------------------------
    // Name pattern helpers
    // -----------------------------------------------------------------

    /** SkyBlock id of a recipe grid cell ({@code "INK_SACK:3"} → {@code INK_SACK}), or null. */
    private static String ingredientId(String cell) {
        if (cell == null || cell.isEmpty()) return null;
        int colon = cell.indexOf(':');
        return colon >= 0 ? cell.substring(0, colon) : cell;
    }

    private static boolean isArmorPiece(String id) {
        return getArmorSlotSuffix(id) != null;
    }

    private static String getArmorSlotSuffix(String id) {
        for (String suffix : ARMOR_SLOT_SUFFIXES) {
            if (id.endsWith(suffix)) return suffix;
        }
        return null;
    }

    /** Pet ids are {@code BASE;N} (tier in the suffix): {@code ARMADILLO;2} → {@code ARMADILLO}. */
    private static String getPetBase(String id) {
        int semi = id.lastIndexOf(';');
        if (semi == -1) return null;
        return id.substring(0, semi);
    }

    private static String getAccessoryBase(String id) {
        for (String suffix : ACCESSORY_SUFFIX_TIERS.keySet()) {
            String withUnderscore = "_" + suffix;
            if (id.endsWith(withUnderscore)) {
                return id.substring(0, id.length() - withUnderscore.length());
            }
        }
        return null;
    }

    private static String getDrillBase(String id) {
        int sep = numericSuffixSeparator(id);
        if (sep <= 0 || id.charAt(sep) != '_') return null;
        String prefix = id.substring(0, sep);
        return prefix.endsWith("_DRILL") ? prefix : null;
    }

}
