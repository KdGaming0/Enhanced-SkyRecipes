package com.github.kdgaming0.skyrecipes.core.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Item categories derived from NEU lore type strings and item structure.
 *
 * <p>Categories match the {@code %CATEGORY} search path syntax. Subtypes are
 * parsed from the lore type line after rarity and dungeon prefix stripping.</p>
 *
 * <p>There are 9 button categories (displayed as toggle buttons) and several
 * search-only categories that are indexed but have no button.</p>
 */
public enum SkyblockItemCategory {
    // ── Button categories (9 total) ───────────────────────────────────────
    ARMOR,
    WEAPON,
    ACCESSORY,
    PET,
    TOOL,
    MINION,
    EQUIPMENT,
    MATERIAL,
    MISC,

    // ── Search-only categories (no button, but searchable via % prefix) ───
    FISHING,
    CONSUMABLE,
    ENCHANTED_BOOK,
    REFORGE_STONE,
    COSMETIC,
    PORTAL,
    DUNGEON_ITEM,
    RIFT_ITEM,
    BLOCK,
    PET_ITEM,

    UNKNOWN;

    /** Categories that render a toggle button in the RRV overlay. */
    public static final List<SkyblockItemCategory> BUTTON_CATEGORIES = List.of(
        ARMOR, WEAPON, ACCESSORY, PET, TOOL, MINION, EQUIPMENT, MATERIAL, MISC
    );

    private static final Set<String> RARITY_WORDS = Set.of(
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY",
        "MYTHIC", "SPECIAL", "ULTIMATE", "DIVINE", "ADMIN", "VERY"
    );

    /**
     * Returns the button category that this category rolls up into.
     * Button categories return themselves. Search-only categories map
     * to their button parent so items appear when the corresponding
     * button is toggled.
     */
    public SkyblockItemCategory getButtonCategory() {
        return switch (this) {
            case ARMOR, WEAPON, ACCESSORY, PET, TOOL, MINION,
                 EQUIPMENT, MATERIAL, MISC, UNKNOWN -> this;
            case FISHING -> TOOL;
            case CONSUMABLE, COSMETIC, PORTAL, ENCHANTED_BOOK, RIFT_ITEM -> MISC;
            case REFORGE_STONE, PET_ITEM -> EQUIPMENT;
            case BLOCK, DUNGEON_ITEM -> MATERIAL;
        };
    }

    /**
     * Derives category from the last lore line which typically contains
     * {@code RARITY [DUNGEON] TYPE}.
     */
    public static SkyblockItemCategory fromLore(@Nullable String lastLoreLine) {
        if (lastLoreLine == null || lastLoreLine.isEmpty()) {
            return UNKNOWN;
        }

        String clean = stripColorCodes(lastLoreLine).toUpperCase().trim();
        if (clean.isEmpty()) {
            return UNKNOWN;
        }

        String[] parts = clean.split("\\s+");
        int idx = 0;

        // Skip rarity words (handles "VERY SPECIAL")
        while (idx < parts.length) {
            String part = parts[idx];
            if (part.equals("VERY") && idx + 1 < parts.length && parts[idx + 1].equals("SPECIAL")) {
                idx += 2;
            } else if (RARITY_WORDS.contains(part)) {
                idx++;
            } else {
                break;
            }
        }

        // Skip DUNGEON prefix
        if (idx < parts.length && parts[idx].equals("DUNGEON")) {
            idx++;
        }

        if (idx >= parts.length) {
            return UNKNOWN;
        }

        // Build type string from remaining tokens
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < parts.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(parts[i]);
        }

        return fromTypeString(sb.toString());
    }

    /** Derives category from a raw type string (e.g. "SWORD", "DUNGEON HELMET"). */
    public static SkyblockItemCategory fromTypeString(String type) {
        if (type == null || type.isEmpty()) {
            return UNKNOWN;
        }
        String t = type.toUpperCase().trim();

        return switch (t) {
            case "SWORD", "LONGSWORD", "WAND", "DUNGEON SWORD", "DUNGEON LONGSWORD" -> WEAPON;
            case "BOW", "DUNGEON BOW" -> WEAPON;
            case "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
                 "DUNGEON HELMET", "DUNGEON CHESTPLATE",
                 "DUNGEON LEGGINGS", "DUNGEON BOOTS" -> ARMOR;
            case "ACCESSORY", "TALISMAN", "RING", "ARTIFACT", "RELIC", "POWER STONE",
                 "DUNGEON ACCESSORY", "HATCESSORY", "CARNIVAL MASK" -> ACCESSORY;
            case "BELT", "NECKLACE", "CLOAK", "GLOVES", "BRACELET",
                 "DUNGEON NECKLACE", "DUNGEON BELT",
                 "DUNGEON CLOAK", "DUNGEON GLOVES" -> ACCESSORY;
            case "PET" -> PET;
            case "PET ITEM" -> PET_ITEM;
            case "PICKAXE", "DRILL", "AXE", "HOE", "SHOVEL", "SHEARS",
                 "FARMING TOOL", "WATERING CAN", "DEPLOYABLE",
                 "GARDEN CHIP", "VACUUM", "CHISEL" -> TOOL;
            case "ROD", "FISHING ROD", "FISHING NET" -> FISHING;
            case "BAIT", "TROPHY", "ROD PART" -> FISHING;
            case "MINION" -> MINION;
            case "GEMSTONE", "ORE", "DWARVEN METAL" -> MATERIAL;
            case "BLOCK", "SALT" -> BLOCK;
            case "DUNGEON ITEM", "TROPHY FISH", "MUTATION",
                 "COMBAT SHARD", "WATER SHARD", "FOREST SHARD" -> DUNGEON_ITEM;
            case "POTION", "FOOD", "ARROW", "ARROW POISON" -> CONSUMABLE;
            case "ENCHANTED BOOK", "BOOK" -> ENCHANTED_BOOK;
            case "REFORGE STONE" -> REFORGE_STONE;
            case "DYE", "MEMENTO", "SKIN", "COSMETIC" -> COSMETIC;
            case "PORTAL", "TRAVEL SCROLL" -> PORTAL;
            case "RIFT TIMECHARM" -> RIFT_ITEM;
            default -> {
                // Broad fallback: if it contains known type words
                if (t.contains("SWORD") || t.contains("BOW") || t.contains("WAND") || t.contains("LONGSWORD")) yield WEAPON;
                if (t.contains("HELMET") || t.contains("CHESTPLATE") || t.contains("LEGGINGS") || t.contains("BOOTS")) yield ARMOR;
                if (t.contains("ACCESSORY") || t.contains("TALISMAN") || t.contains("RING") || t.contains("ARTIFACT") || t.contains("RELIC") || t.contains("POWER STONE")) yield ACCESSORY;
                if (t.contains("PET") && !t.contains("PET ITEM")) yield PET;
                if (t.contains("MINION")) yield MINION;
                if (t.contains("PICKAXE") || t.contains("DRILL") || t.contains("HOE") || t.contains("AXE") || t.contains("SHOVEL") || t.contains("SHEARS") || t.contains("CHISEL")) yield TOOL;
                if (t.contains("ROD") || t.contains("BAIT") || t.contains("TROPHY") || t.contains("FISHING")) yield FISHING;
                if (t.contains("POTION") || t.contains("FOOD") || t.contains("ARROW")) yield CONSUMABLE;
                if (t.contains("BOOK") && !t.contains("ROD")) yield ENCHANTED_BOOK;
                if (t.contains("REFORGE STONE") || t.contains("REFORGE")) yield REFORGE_STONE;
                if (t.contains("DYE") || t.contains("SKIN") || t.contains("COSMETIC") || t.contains("MEMENTO")) yield COSMETIC;
                if (t.contains("PORTAL") || t.contains("TRAVEL SCROLL")) yield PORTAL;
                if (t.contains("RIFT") && !t.contains("TIMECHARM")) yield RIFT_ITEM;
                if (t.contains("DUNGEON") && !t.contains("ITEM")) yield DUNGEON_ITEM;
                if (t.contains("GEMSTONE") || t.contains("ORE") || t.contains("BLOCK") || t.contains("METAL") || t.contains("SHARD") || t.contains("SALT")) yield MATERIAL;
                yield UNKNOWN;
            }
        };
    }

    /** Parse a {@code %CATEGORY} or {@code %CATEGORY/SUBTYPE} path string. */
    @Nullable
    public static SkyblockItemCategory fromPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String p = path.toUpperCase();
        int slash = p.indexOf('/');
        String cat = slash >= 0 ? p.substring(0, slash) : p;
        try {
            return valueOf(cat);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Extract subtype from a {@code %CATEGORY/SUBTYPE} path string. */
    @Nullable
    public static String subtypeFromPath(String path) {
        if (path == null) return null;
        int slash = path.indexOf('/');
        return slash >= 0 && slash + 1 < path.length()
            ? path.substring(slash + 1).toUpperCase()
            : null;
    }

    /** Returns a representative vanilla item ID for rendering a category icon button. */
    public String getIconItemId() {
        return switch (this) {
            case WEAPON -> "minecraft:diamond_sword";
            case ARMOR -> "minecraft:diamond_chestplate";
            case ACCESSORY -> "minecraft:emerald";
            case PET -> "minecraft:bone";
            case TOOL -> "minecraft:diamond_pickaxe";
            case MINION -> "minecraft:player_head";
            case EQUIPMENT -> "minecraft:leather_boots";
            case MATERIAL -> "minecraft:diamond";
            case MISC -> "minecraft:chest";
            case FISHING -> "minecraft:fishing_rod";
            case CONSUMABLE -> "minecraft:potion";
            case ENCHANTED_BOOK -> "minecraft:enchanted_book";
            case REFORGE_STONE -> "minecraft:anvil";
            case COSMETIC -> "minecraft:painting";
            case PORTAL -> "minecraft:ender_pearl";
            case DUNGEON_ITEM -> "minecraft:skull";
            case RIFT_ITEM -> "minecraft:ender_eye";
            case BLOCK -> "minecraft:stone";
            case PET_ITEM -> "minecraft:wheat_seeds";
            case UNKNOWN -> "minecraft:barrier";
        };
    }

    /** Human-readable name for tooltips. */
    public String getDisplayName() {
        return switch (this) {
            case WEAPON -> "Weapons";
            case ARMOR -> "Armor";
            case ACCESSORY -> "Accessories";
            case PET -> "Pets";
            case TOOL -> "Tools";
            case MINION -> "Minions";
            case EQUIPMENT -> "Equipment";
            case MATERIAL -> "Materials";
            case MISC -> "Miscellaneous";
            case FISHING -> "Fishing";
            case CONSUMABLE -> "Consumables";
            case ENCHANTED_BOOK -> "Enchanted Books";
            case REFORGE_STONE -> "Reforge Stones";
            case COSMETIC -> "Cosmetics";
            case PORTAL -> "Portals";
            case DUNGEON_ITEM -> "Dungeon Items";
            case RIFT_ITEM -> "Rift Items";
            case BLOCK -> "Blocks";
            case PET_ITEM -> "Pet Items";
            case UNKNOWN -> "Unknown";
        };
    }

    private static String stripColorCodes(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
