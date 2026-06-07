package com.github.kdgaming0.skyrecipes.core.model;

import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Item categories derived from NEU lore type strings and item structure.
 *
 * <p>Categories match the {@code %CATEGORY} search path syntax. Subtypes are
 * parsed from the lore type line after rarity and dungeon prefix stripping.</p>
 *
 * <p>Button categories render as toggle buttons in the RRV overlay. Search-only
 * categories are indexed but have no button.</p>
 */
public enum SkyblockItemCategory {
    // ── Button categories (9 total) ───────────────────────────────────────
    ARMOR,
    WEAPON,
    TOOL,
    ACCESSORY,
    PET,
    ENCHANTED_BOOK,
    MINION,
    EQUIPMENT,
    MATERIAL,

    // ── Search-only categories (no button, but searchable via % prefix) ───
    FISHING,
    CONSUMABLE,
    REFORGE_STONE,
    COSMETIC,
    PORTAL,
    DUNGEON_ITEM,
    RIFT_ITEM,
    BLOCK,
    PET_ITEM,
    FARMING,
    MISC,
    NPC,

    UNKNOWN;

    /**
     * Categories that render a toggle button in the RRV overlay.
     */
    public static final List<SkyblockItemCategory> BUTTON_CATEGORIES = List.of(
            ARMOR, WEAPON, TOOL, ACCESSORY, PET, ENCHANTED_BOOK, MINION, EQUIPMENT, MATERIAL
    );

    private static final Set<String> RARITY_WORDS = Set.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY",
            "MYTHIC", "SPECIAL", "ULTIMATE", "DIVINE", "ADMIN", "VERY"
    );

    /**
     * Derives category from the last lore line which typically contains
     * {@code RARITY [DUNGEON] TYPE}.
     */
    public static SkyblockItemCategory fromLore(@Nullable String lastLoreLine) {
        if (lastLoreLine == null || lastLoreLine.isEmpty()) {
            return UNKNOWN;
        }

        String clean = TextUtil.stripColorCodes(lastLoreLine).toUpperCase().trim();
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

    /**
     * Derives category from a raw type string (e.g. "SWORD", "DUNGEON HELMET").
     */
    public static SkyblockItemCategory fromTypeString(String type) {
        if (type == null || type.isEmpty()) {
            return UNKNOWN;
        }
        String t = type.toUpperCase().trim();

        return switch (t) {
            // ── Weapons ──────────────────────────────────────────────────────
            case "SWORD", "BOW", "SHORTBOW", "LONGSWORD", "WAND", "FISHING WEAPON",
                 "DUNGEON SWORD", "DUNGEON BOW", "DUNGEON SHORTBOW",
                 "DUNGEON LONGSWORD", "DUNGEON WAND" -> WEAPON;

            // ── Armor ─────────────────────────────────────────────────────────
            case "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
                 "DUNGEON HELMET", "DUNGEON CHESTPLATE",
                 "DUNGEON LEGGINGS", "DUNGEON BOOTS" -> ARMOR;

            // ── Equipment (Powerstone equipment slots) ─────────────────────────
            case "BELT", "NECKLACE", "CLOAK", "GLOVES", "BRACELET" -> EQUIPMENT;

            // ── Accessories ───────────────────────────────────────────────────
            case "ACCESSORY", "TALISMAN", "RING", "ARTIFACT", "RELIC",
                 "HATCESSORY", "CARNIVAL MASK" -> ACCESSORY;

            // ── Pets & pet items ──────────────────────────────────────────────
            case "PET" -> PET;
            case "PET ITEM" -> PET_ITEM;

            // ── Tools ──────────────────────────────────────────────────────────
            case "PICKAXE", "DRILL", "AXE", "HOE", "SHOVEL", "SHEARS",
                 "FARMING TOOL", "WATERING CAN", "DEPLOYABLE",
                 "GARDEN CHIP", "VACUUM", "CHISEL" -> TOOL;

            // ── Fishing ───────────────────────────────────────────────────────
            case "ROD", "FISHING ROD", "FISHING NET", "ROD PART",
                 "TROPHY FISH", "BAIT", "TROPHY" -> FISHING;

            // ── Minions ───────────────────────────────────────────────────────
            case "MINION" -> MINION;

            // ── Materials ─────────────────────────────────────────────────────
            case "GEMSTONE", "ORE", "DWARVEN METAL" -> MATERIAL;

            // ── Blocks ────────────────────────────────────────────────────────
            case "BLOCK", "SALT" -> BLOCK;

            // ── Dungeon items ─────────────────────────────────────────────────
            case "DUNGEON ITEM" -> DUNGEON_ITEM;

            // ── Consumables ───────────────────────────────────────────────────
            case "POTION" -> CONSUMABLE;

            // ── Enchanted books ───────────────────────────────────────────────
            case "ENCHANTED BOOK", "BOOK" -> ENCHANTED_BOOK;

            // ── Reforge stones ────────────────────────────────────────────────
            case "REFORGE STONE" -> REFORGE_STONE;

            // ── Cosmetics ──────────────────────────────────────────────────────
            case "COSMETIC", "DYE", "MEMENTO", "SKIN" -> COSMETIC;

            // ── Portals ───────────────────────────────────────────────────────
            case "PORTAL", "TRAVEL SCROLL" -> PORTAL;

            // ── Rift items ─────────────────────────────────────────────────────
            case "RIFT TIMECHARM" -> RIFT_ITEM;

            // ── Farming ────────────────────────────────────────────────────────
            case "MUTATION" -> FARMING;

            // ── Miscellaneous ──────────────────────────────────────────────────
            case "POWER STONE", "COMBAT SHARD", "WATER SHARD", "FOREST SHARD",
                 "FOOD", "ARROW", "SACK" -> MISC;

            default -> {
                // Broad fallback: if it contains known type words
                if (t.contains("SWORD") || t.contains("BOW") || t.contains("WAND") || t.contains("LONGSWORD"))
                    yield WEAPON;
                if (t.contains("HELMET") || t.contains("CHESTPLATE") || t.contains("LEGGINGS") || t.contains("BOOTS"))
                    yield ARMOR;
                if (t.contains("BELT") || t.contains("NECKLACE") || t.contains("CLOAK") || t.contains("GLOVES") || t.contains("BRACELET"))
                    yield EQUIPMENT;
                if (t.contains("ACCESSORY") || t.contains("TALISMAN") || t.contains("RING") || t.contains("ARTIFACT") || t.contains("RELIC") || t.contains("HATCESSORY") || t.contains("CARNIVAL MASK"))
                    yield ACCESSORY;
                if (t.contains("PET") && !t.contains("PET ITEM")) yield PET;
                if (t.contains("MINION")) yield MINION;
                if (t.contains("PICKAXE") || t.contains("DRILL") || t.contains("HOE") || t.contains("AXE") || t.contains("SHOVEL") || t.contains("SHEARS") || t.contains("CHISEL") || t.contains("FARMING TOOL") || t.contains("DEPLOYABLE") || t.contains("GARDEN CHIP") || t.contains("VACUUM"))
                    yield TOOL;
                if (t.contains("ROD") || t.contains("BAIT") || t.contains("TROPHY") || t.contains("FISHING"))
                    yield FISHING;
                if (t.contains("POTION")) yield CONSUMABLE;
                if (t.contains("BOOK") && !t.contains("ROD")) yield ENCHANTED_BOOK;
                if (t.contains("REFORGE STONE") || t.contains("REFORGE")) yield REFORGE_STONE;
                if (t.contains("DYE") || t.contains("SKIN") || t.contains("COSMETIC") || t.contains("MEMENTO"))
                    yield COSMETIC;
                if (t.contains("PORTAL") || t.contains("TRAVEL SCROLL")) yield PORTAL;
                if (t.contains("RIFT") && !t.contains("TIMECHARM")) yield RIFT_ITEM;
                if (t.contains("DUNGEON") && t.contains("ITEM")) yield DUNGEON_ITEM;
                if (t.contains("GEMSTONE") || t.contains("ORE") || t.contains("BLOCK") || t.contains("METAL") || t.contains("SALT"))
                    yield MATERIAL;
                if (t.contains("MUTATION")) yield FARMING;
                if (t.contains("POWER STONE") || t.contains("SHARD") || t.contains("SACK") || t.contains("FOOD") || t.contains("ARROW"))
                    yield MISC;
                yield UNKNOWN;
            }
        };
    }

    /**
     * Parse a {@code %CATEGORY} or {@code %CATEGORY/SUBTYPE} path string.
     */
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

    /**
     * Extract subtype from a {@code %CATEGORY/SUBTYPE} path string.
     */
    @Nullable
    public static String subtypeFromPath(String path) {
        if (path == null) return null;
        int slash = path.indexOf('/');
        return slash >= 0 && slash + 1 < path.length()
                ? path.substring(slash + 1).toUpperCase()
                : null;
    }

    /**
     * Returns the button category that this category rolls up into.
     * Button categories return themselves. Search-only categories map
     * to their button parent so items appear when the corresponding
     * button is toggled.
     */
    public SkyblockItemCategory getButtonCategory() {
        return switch (this) {
            case ARMOR, WEAPON, ACCESSORY, PET, TOOL, MINION,
                 EQUIPMENT, MATERIAL, ENCHANTED_BOOK, UNKNOWN -> this;
            case FISHING -> TOOL;
            case BLOCK -> MATERIAL;
            case CONSUMABLE, COSMETIC, PORTAL, RIFT_ITEM, FARMING,
                 PET_ITEM, REFORGE_STONE, MISC, NPC -> MISC;
            case DUNGEON_ITEM -> DUNGEON_ITEM; // search-only, no button rollup
        };
    }

    /**
     * Returns a representative vanilla item ID for rendering a category icon button.
     */
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
            case FARMING -> "minecraft:wheat";
            case NPC -> "minecraft:player_head";
            case UNKNOWN -> "minecraft:barrier";
        };
    }

    /**
     * Human-readable name for tooltips.
     */
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
            case FARMING -> "Farming";
            case NPC -> "NPCs";
            case UNKNOWN -> "Unknown";
        };
    }

    /**
     * Returns the sprite base name for the category toggle button, or {@code null}
     * if this category has no button texture.
     */
    @Nullable
    public String getSpriteName() {
        return switch (this) {
            case ARMOR -> "armour";
            case WEAPON -> "weaponry";
            case TOOL -> "tools";
            case ACCESSORY -> "accessories";
            case PET -> "pets";
            case ENCHANTED_BOOK -> "enchants";
            case MINION -> "minions";
            case EQUIPMENT -> "equipment";
            case MATERIAL -> "materials";
            default -> null;
        };
    }
}
