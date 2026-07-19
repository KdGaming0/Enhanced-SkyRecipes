package com.github.kdgaming0.skyrecipes.core.util;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dynamic stat placeholders in pet lore using data from {@code constants/petnums.json}.
 *
 * <p>NEU pet items store their lore with placeholders like {@code {STRENGTH}}, {@code {SPEED}},
 * and {@code {0}}, {@code {1}} for ability values. This class substitutes those placeholders
 * with the actual max-level (level 100) stat values from petnums, producing readable lore.
 *
 * <p>The {@code {LVL}} placeholder in display names is replaced with {@code 100} to match.
 *
 * <p>This resolver operates at compile-time only. Resolved strings are stored in the binary,
 * so runtime {@code ItemStackBuilder} needs no changes.</p>
 */
public final class PetStatResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PetStatResolver.class);

    /**
     * Matches stat-name placeholders like {@code {STRENGTH}} or {@code {SEA_CREATURE_CHANCE}}.
     */
    private static final Pattern STAT_PLACEHOLDER = Pattern.compile("\\{([A-Z][A-Z_]+)}");

    /**
     * Matches numeric placeholders like {@code {0}}, {@code {1}}.
     */
    private static final Pattern INDEX_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    /**
     * Matches the level placeholder in display names.
     */
    private static final Pattern LEVEL_PLACEHOLDER = Pattern.compile("\\{LVL}");

    /**
     * The stat level to display. Using max level keeps things simple and matches what most players want to see.
     */
    private static final int DISPLAY_LEVEL = 100;

    /**
     * Highest rarity index that appears in petnums.json. Pet internalNames use
     * the format {@code "LION;4"} where the suffix is the {@link SkyblockRarity}
     * ordinal: 0=COMMON … 5=MYTHIC.
     */
    private static final int MAX_PET_RARITY_INDEX = SkyblockRarity.MYTHIC.ordinal();

    private final Map<String, Map<String, LevelStats>> petStats;

    private PetStatResolver(Map<String, Map<String, LevelStats>> petStats) {
        this.petStats = petStats;
    }

    /**
     * Parses {@code constants/petnums.json} and returns a resolver containing only the
     * level-100 stats for each pet+rarity combination.
     */
    public static PetStatResolver load(JsonObject json) {
        Map<String, Map<String, LevelStats>> result = new HashMap<>(json.size());

        for (var petEntry : json.entrySet()) {
            String petName = petEntry.getKey();
            JsonElement rarities = petEntry.getValue();
            if (!rarities.isJsonObject()) continue;

            Map<String, LevelStats> rarityMap = new HashMap<>();

            for (var rarityEntry : rarities.getAsJsonObject().entrySet()) {
                String rarityName = rarityEntry.getKey();
                JsonElement levels = rarityEntry.getValue();
                if (!levels.isJsonObject()) continue;

                JsonObject levelsObj = levels.getAsJsonObject();
                String levelKey = String.valueOf(DISPLAY_LEVEL);
                if (!levelsObj.has(levelKey)) continue;

                JsonElement levelData = levelsObj.get(levelKey);
                if (!levelData.isJsonObject()) continue;

                LevelStats stats = parseLevelStats(levelData.getAsJsonObject());
                if (stats != null) {
                    rarityMap.put(rarityName, stats);
                }
            }

            if (!rarityMap.isEmpty()) {
                result.put(petName, Collections.unmodifiableMap(rarityMap));
            }
        }

        LOGGER.info("Loaded petnums for {} pets", result.size());
        return new PetStatResolver(Collections.unmodifiableMap(result));
    }

    private static PetIdentity parsePetIdentity(String internalName) {
        int suffixIndex = SkyblockIdExtractor.petTierSuffix(internalName);
        if (suffixIndex < 0 || suffixIndex > MAX_PET_RARITY_INDEX) {
            return null;
        }

        String petName = internalName.substring(0, internalName.lastIndexOf(';'));
        return new PetIdentity(petName, SkyblockRarity.values()[suffixIndex].name());
    }

    private static String resolveLine(String line, LevelStats stats) {
        if (!line.contains("{")) {
            return line;
        }

        // Replace named stat placeholders: {STRENGTH}, {SPEED}, etc.
        Matcher statMatcher = STAT_PLACEHOLDER.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (statMatcher.find()) {
            String statName = statMatcher.group(1);
            // Skip {LVL} — it only appears in displayName, but guard anyway
            if ("LVL".equals(statName)) {
                continue;
            }

            Double value = stats.statNums.get(statName);
            String replacement = value != null ? formatStat(value) : statMatcher.group(0);
            statMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        statMatcher.appendTail(sb);
        line = sb.toString();

        // Replace indexed placeholders: {0}, {1}, {2}, etc.
        Matcher indexMatcher = INDEX_PLACEHOLDER.matcher(line);
        sb = new StringBuilder();
        while (indexMatcher.find()) {
            int index = Integer.parseInt(indexMatcher.group(1));
            String replacement;
            if (index >= 0 && index < stats.otherNums.size()) {
                replacement = formatStat(stats.otherNums.get(index));
            } else {
                replacement = indexMatcher.group(0); // Leave unresolved
            }
            indexMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        indexMatcher.appendTail(sb);

        return sb.toString();
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    private static String formatStat(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    private static LevelStats parseLevelStats(JsonObject levelObj) {
        Map<String, Double> statNums = Map.of();
        List<Double> otherNums = List.of();

        if (levelObj.has("statNums") && levelObj.get("statNums").isJsonObject()) {
            JsonObject statsObj = levelObj.getAsJsonObject("statNums");
            Map<String, Double> map = new HashMap<>(statsObj.size());
            for (var entry : statsObj.entrySet()) {
                try {
                    map.put(entry.getKey(), entry.getValue().getAsDouble());
                } catch (Exception e) {
                    LOGGER.debug("Skipping non-numeric stat: {}", entry.getKey());
                }
            }
            statNums = Collections.unmodifiableMap(map);
        }

        if (levelObj.has("otherNums") && levelObj.get("otherNums").isJsonArray()) {
            List<Double> list = new ArrayList<>();
            for (JsonElement e : levelObj.getAsJsonArray("otherNums")) {
                try {
                    list.add(e.getAsDouble());
                } catch (Exception ex) {
                    list.add(0.0);
                }
            }
            otherNums = Collections.unmodifiableList(list);
        }

        if (statNums.isEmpty() && otherNums.isEmpty()) {
            return null;
        }
        return new LevelStats(statNums, otherNums);
    }

    /**
     * Returns resolved display name and lore for a pet item, or {@code null} if the item
     * is not a pet or no petnums data exists for it.
     *
     * <p>The returned array has the display name at index 0 and the resolved lore list at index 1.
     * If this method returns non-null, the caller should use these resolved strings instead of
     * the raw {@link NeuItem} values.</p>
     */
    public ResolvedStrings resolve(NeuItem item) {
        String internalName = item.internalName();
        if (internalName == null || internalName.isEmpty()) {
            return null;
        }

        PetIdentity identity = parsePetIdentity(internalName);
        if (identity == null) {
            return null;
        }

        Map<String, LevelStats> rarityMap = petStats.get(identity.petName);
        if (rarityMap == null) {
            return null;
        }

        LevelStats stats = rarityMap.get(identity.rarityName);
        if (stats == null) {
            return null;
        }

        String resolvedName = item.displayName();
        if (resolvedName != null && !resolvedName.isEmpty()) {
            resolvedName = LEVEL_PLACEHOLDER.matcher(resolvedName)
                    .replaceAll(String.valueOf(DISPLAY_LEVEL));
        }

        List<String> resolvedLore = item.lore();
        if (resolvedLore != null && !resolvedLore.isEmpty()) {
            List<String> newLore = new ArrayList<>(resolvedLore.size());
            for (String line : resolvedLore) {
                newLore.add(resolveLine(line, stats));
            }
            resolvedLore = newLore;
        } else if (resolvedLore == null) {
            resolvedLore = List.of();
        }

        return new ResolvedStrings(resolvedName, resolvedLore);
    }

    /**
     * Returns whether any petnums data has been loaded.
     */
    public boolean isLoaded() {
        return !petStats.isEmpty();
    }

    // -----------------------------------------------------------------
    // Data classes
    // -----------------------------------------------------------------

    /**
     * Holds the resolved stat values for a single pet at a specific rarity and level.
     */
    private record LevelStats(Map<String, Double> statNums, List<Double> otherNums) {
    }

    /**
     * Extracted pet identity from an internal name like {@code "LION;4"}.
     */
    private record PetIdentity(String petName, String rarityName) {
    }

    /**
     * Result of resolving placeholders for a single item.
     */
    public record ResolvedStrings(String displayName, List<String> lore) {
    }
}
