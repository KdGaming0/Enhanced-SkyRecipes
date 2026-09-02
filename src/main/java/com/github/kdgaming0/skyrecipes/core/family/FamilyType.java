package com.github.kdgaming0.skyrecipes.core.family;

/**
 * Classification of item families, declaring the two behaviors a family can have:
 *
 * <ul>
 *   <li><b>expands for results</b> — pressing <b>R</b> on a member shows related recipes.
 *       Ordered paths stop at the selected member; unordered sibling families show the
 *       whole family. <b>U</b> lookups never expand: the ingredient index already captures
 *       upgrade chains naturally.</li>
 *   <li><b>forms a stack group</b> — members collapse into one stack in the RRV item list.</li>
 * </ul>
 */
public enum FamilyType {
    /**
     * Numeric or semicolon-tiered families: minions, pets, enchantments, drills,
     * gemstone qualities, rarity ladders, Master Skull tiers, etc.
     */
    TIERED(true, true),
    /**
     * Accessory upgrade chains: TALISMAN → RING → ARTIFACT → RELIC.
     */
    ACCESSORY_CHAIN(true, true),
    /**
     * Starred dungeon items: BASE → STARRED_BASE.
     */
    STARRED(true, true),
    /**
     * Linear upgrade lines: crafted successor armor (MELON → CROPIE → … → HELIANTHUS),
     * Kuudra prefix ladders (CRIMSON → HOT_CRIMSON → … → INFERNAL_CRIMSON),
     * museum/shop progressions, and compaction lines (DIAMOND → ENCHANTED_DIAMOND → BLOCK).
     */
    UPGRADE_CHAIN(true, true),
    /**
     * One base item with multiple side-grade variants (Necron's Blade → Astraea/Hyperion/
     * Scylla/Valkyrie, Wither armor → Necron's/Storm's/Goldor's/Maxor's). Expands so R
     * shows every variant's recipe, but never stacks — the variants are distinct items,
     * not steps of one ladder.
     */
    BRANCHING(true, false),
    /**
     * Helmet/Chestplate/Leggings/Boots of one armor set. Expands so R on any piece shows
     * the whole set's recipes, but never stacks — many sets also have per-piece upgrade
     * tiers, and stacking the set would collide with those groups.
     */
    ARMOR_SET(true, false),
    /**
     * Curated variant sets from parents.json that are not upgrade ladders (dyes,
     * party hat colors, trophy fish medals) — stack in the item list, no R-expansion.
     */
    VARIANT_SET(false, true),
    /**
     * Item has no family or an unregistered family.
     */
    SINGLE(false, false);

    private final boolean expandsForResults;
    private final boolean formsStackGroup;

    FamilyType(boolean expandsForResults, boolean formsStackGroup) {
        this.expandsForResults = expandsForResults;
        this.formsStackGroup = formsStackGroup;
    }

    /** True when pressing <b>R</b> on a member should show recipes for the whole family. */
    public boolean expandsForResults() {
        return expandsForResults;
    }

    /**
     * True when family members form a progression with a meaningful before/after order.
     * Recipe expansion for these families means "the path to this item", not every later
     * upgrade that consumes it.
     */
    public boolean formsOrderedRecipePath() {
        return switch (this) {
            case TIERED, ACCESSORY_CHAIN, STARRED, UPGRADE_CHAIN -> true;
            case BRANCHING, ARMOR_SET, VARIANT_SET, SINGLE -> false;
        };
    }

    /** True when members should collapse into one stack group in the item list. */
    public boolean formsStackGroup() {
        return formsStackGroup;
    }
}
