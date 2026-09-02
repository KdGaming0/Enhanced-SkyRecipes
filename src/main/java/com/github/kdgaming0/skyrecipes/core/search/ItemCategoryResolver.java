package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Single-responsibility resolver that determines the {@link SkyblockItemCategory}
 * for a {@link NeuItem}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Last lore line (standard NEU rarity/type format)</li>
 *   <li>Item ID heuristics (minecraft material type)</li>
 *   <li>Internal name heuristics (pet tier suffix, etc.)</li>
 * </ol>
 *
 * <p>Also infers a subtype string (e.g. "sword", "helmet") for search indexing.</p>
 */
public final class ItemCategoryResolver {

    private static final Pattern MINION_PATTERN = Pattern.compile(".*_GENERATOR_\\d+");

    private ItemCategoryResolver() {
    }

    /**
     * Resolve the category for the given item.
     *
     * <p>First tries {@link SkyblockItemCategory#fromLore(String)}. If that returns
     * {@link SkyblockItemCategory#UNKNOWN}, falls back to ID-based and name-based
     * heuristics.</p>
     */
    public static SkyblockItemCategory resolve(NeuItem item) {
        String lastLore = (item.lore() != null && !item.lore().isEmpty()) ? item.lore().getLast() : null;
        SkyblockItemCategory category = SkyblockItemCategory.fromLore(lastLore);
        if (category != SkyblockItemCategory.UNKNOWN) {
            return category;
        }
        return inferCategoryFromItemId(item);
    }

    // -----------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------

    /**
     * Infer a subtype string from the item for the {@code type:} search filter.
     *
     * <p>Examples: {@code "sword"}, {@code "helmet"}, {@code "pickaxe"}, {@code "pet"}.</p>
     */
    @Nullable
    public static String inferType(NeuItem item) {
        String itemId = item.itemId() != null ? item.itemId().toLowerCase() : "";
        String internalName = item.internalName() != null ? item.internalName().toLowerCase() : "";

        if (itemId.contains("sword")) return "sword";
        if (itemId.contains("bow")) return "bow";
        if (itemId.contains("axe") && !itemId.contains("pickaxe")) return "axe";
        if (itemId.contains("pickaxe") || itemId.contains("drill")) return "pickaxe";
        if (itemId.contains("hoe")) return "hoe";
        if (itemId.contains("shovel") || itemId.contains("spade")) return "shovel";
        if (itemId.contains("rod") || internalName.contains("rod")) return "rod";
        if (itemId.contains("helmet") || itemId.contains("head") || itemId.contains("hat")) return "helmet";
        // "chestplate" only — a bare "chest" match would classify storage chests as armor
        if (itemId.contains("chestplate") || itemId.contains("jacket")) return "chestplate";
        if (itemId.contains("leggings") || itemId.contains("pants")) return "leggings";
        if (itemId.contains("boots") || itemId.contains("shoes")) return "boots";
        if (internalName.contains("_pet")) return "pet";
        if (internalName.contains("_accessory") || internalName.contains("_talisman")
                || internalName.contains("_ring") || internalName.contains("_artifact")
                || internalName.contains("_relic")) return "accessory";

        if (item.lore() != null && !item.lore().isEmpty()) {
            String last = TextUtil.stripColorCodes(item.lore().getLast()).toLowerCase();
            if (last.endsWith(" sword")) return "sword";
            if (last.endsWith(" bow")) return "bow";
            if (last.endsWith(" axe")) return "axe";
            if (last.endsWith(" helmet") || last.endsWith(" hat") || last.endsWith(" head")) return "helmet";
            if (last.endsWith(" chestplate") || last.endsWith(" chest") || last.endsWith(" cloak")) return "chestplate";
            if (last.endsWith(" leggings") || last.endsWith(" pants")) return "leggings";
            if (last.endsWith(" boots") || last.endsWith(" shoes")) return "boots";
            if (last.endsWith(" pickaxe") || last.endsWith(" drill")) return "pickaxe";
            if (last.endsWith(" hoe")) return "hoe";
            if (last.endsWith(" shovel") || last.endsWith(" spade")) return "shovel";
            if (last.endsWith(" rod") || last.endsWith(" staff")) return "rod";
            if (last.endsWith(" pet")) return "pet";
            if (last.endsWith(" accessory") || last.endsWith(" talisman") || last.endsWith(" ring")
                    || last.endsWith(" artifact") || last.endsWith(" relic")) return "accessory";
            if (last.endsWith(" minion")) return "minion";
            if (last.endsWith(" book")) return "book";
        }

        return null;
    }

    private static SkyblockItemCategory inferCategoryFromItemId(NeuItem item) {
        String itemId = item.itemId();
        String internalName = item.internalName();

        if (itemId == null) return SkyblockItemCategory.UNKNOWN;
        String id = itemId.toLowerCase();

        // Enchanted books by item ID (reliable structural check)
        if ("minecraft:enchanted_book".equals(itemId)) {
            return SkyblockItemCategory.ENCHANTED_BOOK;
        }

        // Minions by internal name pattern (reliable structural check)
        if (isMinion(item)) {
            return SkyblockItemCategory.MINION;
        }

        // NPC items by internal name suffix
        if (isNpc(item)) {
            return SkyblockItemCategory.NPC;
        }

        // Pet detection: must have pet indicators, not just ;N suffix
        if (isLikelyPet(item)) {
            return SkyblockItemCategory.PET;
        }

        // Internal name patterns (reliable structural checks)
        if (internalName != null) {
            String in = internalName.toUpperCase();
            if (in.endsWith("_SKIN") || in.contains("_DYE") || in.contains("_RUNE")) {
                return SkyblockItemCategory.COSMETIC;
            }
            if (in.contains("_PHONE") || in.contains("ABIPHONE") || in.contains("ABICASE") || in.contains("_SACK")) {
                return SkyblockItemCategory.MISC;
            }
            if (in.startsWith("ENCHANTED_") && !in.startsWith("ENCHANTED_BOOK")) {
                return SkyblockItemCategory.MATERIAL;
            }
        }

        // Item ID heuristics (least reliable — many functional items use skulls/heads)
        if (id.contains("sword") || id.contains("bow") || id.contains("wand")) return SkyblockItemCategory.WEAPON;
        if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots"))
            return SkyblockItemCategory.ARMOR;
        if (id.contains("pickaxe") || id.contains("drill") || id.contains("hoe") || id.contains("axe") || id.contains("shovel"))
            return SkyblockItemCategory.TOOL;
        if (id.contains("rod")) return SkyblockItemCategory.FISHING;
        if (id.contains("potion")) return SkyblockItemCategory.CONSUMABLE;
        return SkyblockItemCategory.UNKNOWN;
    }

    /**
     * Minions are identified by their internal name pattern {@code *_GENERATOR_\d+},
     * not by lore text (NEU minion lore often lacks a "MINION" type suffix).
     */
    private static boolean isNpc(NeuItem item) {
        String internalName = item.internalName();
        return internalName != null && internalName.endsWith("_NPC");
    }

    private static boolean isMinion(NeuItem item) {
        String internalName = item.internalName();
        if (internalName == null) return false;
        return MINION_PATTERN.matcher(internalName).matches();
    }

    /**
     * Determines whether an item is likely a pet based on reliable indicators.
     *
     * <p>NEU uses {@code ;N} suffixes for pets, enchanted books, runes, and other
     * tiered items. We must NOT classify all {@code ;N} items as pets.</p>
     */
    private static boolean isLikelyPet(NeuItem item) {
        String displayName = item.displayName();
        if (displayName != null && displayName.contains("[Lvl ")) {
            return true;
        }

        // Check last lore line for PET type indicator
        if (item.lore() != null && !item.lore().isEmpty()) {
            String last = TextUtil.stripColorCodes(item.lore().getLast()).toUpperCase();
            if (last.contains("PET") && !last.contains("PET ITEM")) {
                return true;
            }
        }

        String internalName = item.internalName();
        if (internalName != null && internalName.matches(".*;[0-5]")) {
            // Has tier suffix but no pet indicators — require the display name
            // to contain the pet level pattern as a stronger signal
            return displayName != null && displayName.contains("[Lvl ");
        }

        return false;
    }

}
