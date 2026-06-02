package com.github.kdgaming0.skyrecipes.core.family;

/**
 * Classification of item families for recipe lookup expansion.
 *
 * <p>Only {@link #expandsForResults()} == true families will have their recipes merged
 * when the player presses <b>R</b> on a member item. <b>U</b> lookups never expand
 * because the ingredient index already captures upgrade chains naturally.</p>
 */
public enum FamilyType {
    /** Numeric or semicolon-tiered families: minions, pets, enchantments, perfect armor,
     *  tools, runes, campfire talismans, drills, etc. */
    TIERED,
    /** Accessory upgrade chains: TALISMAN → RING → ARTIFACT → RELIC. */
    ACCESSORY_CHAIN,
    /** Starred dungeon items: BASE → STARRED_BASE. */
    STARRED,
    /** One base item that branches into multiple named variants. */
    BRANCHING,
    /** Armor set pieces (HELMET/CHESTPLATE/LEGGINGS/BOOTS) — do not expand. */
    ARMOR_SET,
    /** Vanilla block color variants (WOOL, BANNER, etc.) — do not expand. */
    COLOR_VARIANT,
    /** Thematically related items that are not craftable upgrades — do not expand. */
    COLLECTION,
    /** Item has no family or an unregistered family — do not expand. */
    SINGLE;

    /**
     * Returns true if pressing <b>R</b> on a member of this family should show recipes
     * for all family members, not just the exact item clicked.
     */
    public boolean expandsForResults() {
        return switch (this) {
            case TIERED, ACCESSORY_CHAIN, STARRED, BRANCHING -> true;
            default -> false;
        };
    }
}
