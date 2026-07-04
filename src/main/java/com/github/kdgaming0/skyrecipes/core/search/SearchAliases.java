package com.github.kdgaming0.skyrecipes.core.search;

import java.util.Map;

/**
 * Community shorthand → NEU internal name aliases. Plain shared data used by
 * {@link SearchAutocomplete}, {@link SkyblockSearchIndex}, and the RRV plugin's
 * alias registration.
 */
public final class SearchAliases {

    public static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("aote", "ASPECT_OF_THE_END"),
            Map.entry("aotv", "ASPECT_OF_THE_VOID"),
            Map.entry("juju", "JUJU_SHORTBOW"),
            Map.entry("livid", "LIVID_DAGGER"),
            Map.entry("fs", "FLOWER_OF_TRUTH"),
            Map.entry("yeti", "YETI_SWORD"),
            Map.entry("term", "TERMINATOR"),
            Map.entry("hype", "HYPERION"),
            Map.entry("aotd", "ASPECT_OF_THE_DRAGON"),
            Map.entry("bonemerang", "BONE_BOOMERANG"),
            Map.entry("daed", "DAEDALUS_AXE"),
            Map.entry("gdrag", "GOLDEN_DRAGON"),
            Map.entry("edrag", "ENDER_DRAGON_PET"),
            Map.entry("wither", "WITHER_SHIELD_SCROLL"),
            Map.entry("sf", "SHADOW_FURY"),
            Map.entry("valk", "VALKYRIE"),
            Map.entry("astrea", "ASTREA"),
            Map.entry("scs", "SCORPION_FOIL"),
            Map.entry("spirit", "SPIRIT_SCEPTRE"),
            Map.entry("giant", "GIANTS_SWORD"),
            Map.entry("midas", "MIDAS_SWORD"),
            Map.entry("pooch", "POOCH_SWORD"),
            Map.entry("reef", "REEF_SCALES"),
            Map.entry("rod", "SPEEDSTER_ROD"),
            Map.entry("inferno", "INFERNO_ROD"),
            Map.entry("hell", "HELLFIRE_ROD"),
            Map.entry("soul", "SOUL_WHIP"),
            Map.entry("wand", "WAND_OF_RESTORATION"),
            Map.entry("ice", "ICE_SPRAY_WAND"),
            Map.entry("plasma", "PLASMAFLUX_POWER_ORB"),
            Map.entry("overflux", "OVERFLUX_POWER_ORB"),
            Map.entry("manaflux", "MANAFLUX_POWER_ORB"),
            Map.entry("rory", "RORY"),
            Map.entry("boo", "BOO_STAFF")
    );

    private SearchAliases() {
    }
}
