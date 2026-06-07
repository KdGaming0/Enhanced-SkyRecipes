package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;

import java.util.*;

/**
 * Maps NEU item lore types to the {@code itemTypes} strings used in
 * {@code reforges.json} and {@code reforgestones.json}.
 *
 * <p>This enables reverse lookups: given a reforge type like {@code "SWORD"},
 * we can find all lore types that map to it ({@code "SWORD"}, {@code "DUNGEON SWORD"},
 * etc.) and therefore all {@link NeuItem}s that the reforge applies to.</p>
 */
public final class ReforgeTypeResolver {

    private ReforgeTypeResolver() {
    }

    /**
     * Forward mapping: lore type (e.g. "SWORD") → list of reforge type strings it matches.
     */
    private static final Map<String, List<String>> LORE_TYPE_TO_REFORGE_TYPES = Map.ofEntries(
            Map.entry("SWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("DUNGEON SWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("LONGSWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("DUNGEON LONGSWORD", List.of("SWORD", "SWORD/ROD")),
            Map.entry("BOW", List.of("BOW")),
            Map.entry("DUNGEON BOW", List.of("BOW")),
            Map.entry("SHORTBOW", List.of("BOW")),
            Map.entry("DUNGEON SHORTBOW", List.of("BOW")),
            Map.entry("HELMET", List.of("ARMOR", "HELMET")),
            Map.entry("CHESTPLATE", List.of("ARMOR", "CHESTPLATE")),
            Map.entry("LEGGINGS", List.of("ARMOR")),
            Map.entry("BOOTS", List.of("ARMOR")),
            Map.entry("DUNGEON HELMET", List.of("ARMOR", "HELMET")),
            Map.entry("DUNGEON CHESTPLATE", List.of("ARMOR", "CHESTPLATE")),
            Map.entry("DUNGEON LEGGINGS", List.of("ARMOR")),
            Map.entry("DUNGEON BOOTS", List.of("ARMOR")),
            Map.entry("PICKAXE", List.of("PICKAXE")),
            Map.entry("DRILL", List.of("PICKAXE")),
            Map.entry("CHISEL", List.of("PICKAXE")),
            Map.entry("HOE", List.of("FARMING_TOOL", "HOE")),
            Map.entry("SHOVEL", List.of("FARMING_TOOL")),
            Map.entry("SHEARS", List.of("FARMING_TOOL")),
            Map.entry("AXE", List.of("AXE")),
            Map.entry("FISHING ROD", List.of("ROD", "SWORD/ROD", "FISHING_ROD")),
            Map.entry("FISHING NET", List.of("ROD", "SWORD/ROD", "FISHING_ROD")),
            Map.entry("ROD", List.of("ROD", "SWORD/ROD", "FISHING_ROD")),
            Map.entry("ACCESSORY", List.of("EQUIPMENT", "ACCESSORY")),
            Map.entry("HATCESSORY", List.of("EQUIPMENT", "ACCESSORY")),
            Map.entry("BELT", List.of("EQUIPMENT", "BELT")),
            Map.entry("NECKLACE", List.of("EQUIPMENT")),
            Map.entry("CLOAK", List.of("EQUIPMENT", "CLOAK")),
            Map.entry("GLOVES", List.of("EQUIPMENT")),
            Map.entry("BRACELET", List.of("EQUIPMENT")),
            Map.entry("DUNGEON ACCESSORY", List.of("EQUIPMENT", "ACCESSORY")),
            Map.entry("DUNGEON NECKLACE", List.of("EQUIPMENT")),
            Map.entry("DUNGEON BELT", List.of("EQUIPMENT", "BELT")),
            Map.entry("DUNGEON CLOAK", List.of("EQUIPMENT", "CLOAK")),
            Map.entry("DUNGEON GLOVES", List.of("EQUIPMENT")),
            Map.entry("CARNIVAL MASK", List.of("EQUIPMENT", "ACCESSORY")),
            Map.entry("RIFT TIMECHARM", List.of("EQUIPMENT")),
            Map.entry("VACUUM", List.of("VACUUM")),
            Map.entry("WATERING CAN", List.of("FARMING_TOOL")),
            Map.entry("FARMING TOOL", List.of("FARMING_TOOL")),
            Map.entry("DEPLOYABLE", List.of("EQUIPMENT")),
            Map.entry("PET", List.of("PET")),
            Map.entry("WAND", List.of("SWORD", "SWORD/ROD")),
            Map.entry("DUNGEON WAND", List.of("SWORD", "SWORD/ROD"))
    );

    /** Reverse index: reforge type → lore types. Built once for O(1) lookups. */
    private static final Map<String, List<String>> REFORGE_TYPE_TO_LORE_TYPES = buildReverseIndex();

    private static final Set<String> RARITY_WORDS = Set.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY",
            "MYTHIC", "SPECIAL", "ULTIMATE", "DIVINE", "ADMIN", "VERY"
    );

    private static Map<String, List<String>> buildReverseIndex() {
        Map<String, List<String>> map = new HashMap<>();
        for (var entry : LORE_TYPE_TO_REFORGE_TYPES.entrySet()) {
            String loreType = entry.getKey();
            for (String reforgeType : entry.getValue()) {
                map.computeIfAbsent(reforgeType, k -> new ArrayList<>()).add(loreType);
            }
        }
        Map<String, List<String>> result = new HashMap<>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the lore types that map to a given reforge type string.
     * For example, {@code "ARMOR"} returns {@code ["HELMET", "CHESTPLATE", ...]}.
     */
    public static List<String> getLoreTypesForReforgeType(String reforgeType) {
        return REFORGE_TYPE_TO_LORE_TYPES.getOrDefault(reforgeType, List.of());
    }

    /**
     * Extracts the raw lore type string from a {@link NeuItem}'s last lore line.
     * Strips rarity and colour codes, leaving e.g. {@code "SWORD"} or {@code "DUNGEON HELMET"}.
     *
     * @return the type string, or {@code null} if the item has no usable lore.
     */
    public static String extractLoreType(NeuItem item) {
        if (item == null || item.lore() == null || item.lore().isEmpty()) {
            return null;
        }
        String last = item.lore().getLast();
        return extractTypeFromLoreLine(last);
    }

    static String extractTypeFromLoreLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String clean = stripColorCodes(line).toUpperCase().trim();
        if (clean.isEmpty()) {
            return null;
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
            return null;
        }

        // Build type string from remaining tokens
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < parts.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
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
