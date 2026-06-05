package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Zero-allocation direct-scan parser for the search query bar.
 *
 * <p>Splits on whitespace, detects stat threshold operators ({@code > < >= <= =}),
 * filter prefixes ({@code key:value}, {@code key>value}), category paths
 * ({@code %CATEGORY/SUBTYPE}), boolean flags, and normalises tokens.
 * No regex is used.</p>
 *
 * <h3>Supported syntax</h3>
 * <ul>
 *   <li>Keywords: {@code farming pets} -> AND of {@code farming} and {@code pet}.</li>
 *   <li>Stat thresholds: {@code mining_speed>50}, {@code health<=100}, {@code damage=50}.</li>
 *   <li>Filters: {@code rarity:legendary}, {@code type:sword}, {@code slayer:zombie>3}.
 *       Also {@code skill:combat>20}, {@code cata>=5}.</li>
 *   <li>Category path: {@code %ARMOR/HELMET}, {@code %PET}.</li>
 *   <li>Boolean flags: {@code soulbound}, {@code dungeon}, {@code rift}, {@code bazaar},
 *       {@code craftable}, {@code forgeable}, {@code npc}, {@code vanilla}.</li>
 * </ul>
 */
public final class SearchQueryParser {

    /**
     * Fallback canonical stat names used when runtime NEU data is not yet loaded.
     * This set is merged with compile-time-generated stats from the binary at runtime.
     */
    public static final Set<String> CANONICAL_STAT_NAMES = Set.of(
            "mining_speed", "mining_fortune", "attack_speed", "crit_chance", "crit_damage",
            "health", "defense", "strength", "intelligence", "sea_creature_chance",
            "fishing_speed", "fishing_fortune", "farming_fortune", "foraging_fortune",
            "block_fortune", "pristine", "speed", "magic_find", "breaking_power",
            "true_defense", "vitality", "mending", "trophy_chance", "pet_luck",
            "bonus_pest_chance", "heat_resistance", "pressure_resistance", "rift_time",
            "ability_damage", "ferocity", "health_regen", "mining_wisdom", "farming_wisdom",
            "damage", "combat_wisdom", "treasure_chance", "magical_power", "rift_damage",
            "gemstone_fortune", "crux_fortune", "crop_fortune", "hunter_fortune",
            "fig_fortune", "mana_regen", "max_speed", "minion_speed", "double_hook_chance",
            "global_fortune", "global_wisdom", "taming_wisdom", "social_wisdom",
            "fishing_wisdom", "foraging_wisdom", "walk_speed", "visitor_cooldown",
            "pickaxe_ability_cooldown", "mining_spread", "absorption",
            // Additional stats discovered from NEU gear lore
            "alchemy_wisdom", "hunting_wisdom", "enchanting_wisdom", "carpentry_wisdom",
            "runecrafting_wisdom", "cold_resistance", "respiration", "fear", "tracking",
            "pull", "sweep", "swing_range", "hearts", "ore_fortune", "dwarven_metal_fortune",
            "gemstone_spread", "overbloom", "wheat_fortune", "carrot_fortune", "potato_fortune",
            "pumpkin_fortune", "sugar_cane_fortune", "melon_slice_fortune", "cactus_fortune",
            "cocoa_beans_fortune", "mushroom_fortune", "nether_wart_fortune",
            "sunflower_fortune", "moonflower_fortune", "wild_rose_fortune",
            "mangrove_fortune"
    );
    private static final SearchQuery EMPTY = new SearchQuery(List.of(), List.of(), List.of(), null, Set.of());

    private static final Map<String, String> FILTER_KEY_ALIASES = Map.ofEntries(
            Map.entry("r", "rarity"), Map.entry("rarity", "rarity"),
            Map.entry("t", "type"), Map.entry("type", "type"),
            Map.entry("sl", "slayer"), Map.entry("slayer", "slayer"),
            Map.entry("sk", "skill"), Map.entry("skill", "skill"),
            Map.entry("cata", "catacombs"), Map.entry("catacombs", "catacombs")
    );

    private static final Set<String> BOOLEAN_FLAGS = Set.of(
            "soulbound", "dungeon", "rift", "vanilla",
            "bazaar", "craftable", "forgeable", "npc", "pet", "accessory"
    );

    private static final Map<String, String> RARITY_ALIASES = Map.ofEntries(
            Map.entry("c", "common"), Map.entry("com", "common"),
            Map.entry("u", "uncommon"), Map.entry("unc", "uncommon"),
            Map.entry("ra", "rare"),
            Map.entry("ep", "epic"),
            Map.entry("l", "legendary"), Map.entry("leg", "legendary"),
            Map.entry("m", "mythic"), Map.entry("myth", "mythic"),
            Map.entry("sp", "special"),
            Map.entry("ult", "ultimate"),
            Map.entry("div", "divine")
    );
    private static final Map<String, String> STAT_ALIASES = Map.ofEntries(
            Map.entry("ms", "mining_speed"),
            Map.entry("mf", "mining_fortune"),
            Map.entry("mfort", "mining_fortune"),
            Map.entry("as", "attack_speed"),
            Map.entry("aspd", "attack_speed"),
            Map.entry("cc", "crit_chance"),
            Map.entry("cd", "crit_damage"),
            Map.entry("hp", "health"),
            Map.entry("def", "defense"),
            Map.entry("str", "strength"),
            Map.entry("int", "intelligence"),
            Map.entry("mana", "intelligence"),
            Map.entry("sc", "sea_creature_chance"),
            Map.entry("scc", "sea_creature_chance"),
            Map.entry("fs", "fishing_speed"),
            Map.entry("fspd", "fishing_speed"),
            Map.entry("ff", "fishing_fortune"),
            Map.entry("ffor", "farming_fortune"),
            Map.entry("forgfort", "foraging_fortune"),
            Map.entry("foraf", "foraging_fortune"),
            Map.entry("bf", "block_fortune"),
            Map.entry("pristine", "pristine"),
            Map.entry("speed", "speed"),
            Map.entry("spd", "speed"),
            Map.entry("walk_speed", "speed"),
            Map.entry("mgf", "magic_find"),
            Map.entry("bc", "breaking_power"),
            Map.entry("td", "true_defense"),
            Map.entry("vit", "vitality"),
            Map.entry("mending", "mending"),
            Map.entry("tc", "trophy_chance"),
            Map.entry("pl", "pet_luck"),
            Map.entry("bpc", "bonus_pest_chance"),
            Map.entry("heat", "heat_resistance"),
            Map.entry("pres", "pressure_resistance"),
            Map.entry("rt", "rift_time"),
            Map.entry("ab", "ability_damage"),
            Map.entry("fer", "ferocity"),
            Map.entry("hr", "health_regen"),
            Map.entry("mw", "mining_wisdom"),
            Map.entry("fw", "farming_wisdom"),
            Map.entry("dmg", "damage")
    );

    // -- Category path parsing -------------------------------------------------
    /**
     * Reverse lookup: canonical stat name → set of aliases.
     */
    private static final Map<String, Set<String>> STAT_ALIAS_REVERSE;

    // -- Filter parsing --------------------------------------------------------
    /**
     * Runtime-populated stat names from the compiled NEU binary. Null until data loads.
     */
    private static volatile Set<String> runtimeKnownStats = null;

    static {
        Map<String, Set<String>> reverse = new HashMap<>();
        for (Map.Entry<String, String> e : STAT_ALIASES.entrySet()) {
            reverse.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        STAT_ALIAS_REVERSE = Collections.unmodifiableMap(reverse);
    }

    private SearchQueryParser() {
    }

    public static SearchQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        int len = lower.length();
        int i = 0;

        while (i < len && Character.isWhitespace(lower.charAt(i))) i++;
        if (i >= len) return EMPTY;

        List<SearchQuery.KeywordClause> keywords = null;
        List<SearchQuery.StatClause> stats = null;
        List<SearchQuery.FilterClause> filters = null;
        SearchQuery.CategoryPath categoryPath = null;
        Set<String> booleanFlags = null;

        while (i < len) {
            while (i < len && Character.isWhitespace(lower.charAt(i))) {
                i++;
            }
            if (i >= len) break;

            int start = i;
            while (i < len && !Character.isWhitespace(lower.charAt(i))) {
                i++;
            }
            String token = lower.substring(start, i);

            // 1. Category path
            if (token.startsWith("%")) {
                SearchQuery.CategoryPath cp = tryParseCategory(token);
                if (cp != null) {
                    categoryPath = cp;
                    continue;
                }
            }

            // 2. Boolean flag (standalone keyword)
            if (BOOLEAN_FLAGS.contains(token)) {
                if (booleanFlags == null) booleanFlags = new java.util.HashSet<>(4);
                booleanFlags.add(token);
                continue;
            }

            // 3. Filter
            SearchQuery.FilterClause filter = tryParseFilter(token);
            if (filter != null) {
                if (filters == null) filters = new ArrayList<>(4);
                filters.add(filter);
                continue;
            }

            // 4. Stat threshold
            SearchQuery.StatClause stat = tryParseStat(token);
            if (stat != null) {
                if (stats == null) stats = new ArrayList<>(4);
                stats.add(stat);
                continue;
            }

            // 5. Keyword
            for (String part : splitOnNonAlphanumeric(token)) {
                if (part.length() > 1 || (part.length() == 1 && Character.isDigit(part.charAt(0)))) {
                    if (keywords == null) keywords = new ArrayList<>(4);
                    keywords.add(new SearchQuery.KeywordClause(part));
                }
            }
        }

        return new SearchQuery(
                keywords != null ? keywords : List.of(),
                stats != null ? stats : List.of(),
                filters != null ? filters : List.of(),
                categoryPath,
                booleanFlags != null ? booleanFlags : Set.of()
        );
    }

    @Nullable
    private static SearchQuery.CategoryPath tryParseCategory(String token) {
        if (!token.startsWith("%")) return null;
        String path = token.substring(1).toUpperCase(Locale.ROOT);
        if (path.isEmpty()) return null;

        int slash = path.indexOf('/');
        String catStr = slash >= 0 ? path.substring(0, slash) : path;
        String sub = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : null;

        SkyblockItemCategory cat = SkyblockItemCategory.fromPath(catStr);
        if (cat == null) return null;

        return new SearchQuery.CategoryPath(cat, sub);
    }

    // -- Stat parsing ----------------------------------------------------------

    @Nullable
    private static SearchQuery.FilterClause tryParseFilter(String token) {
        int colonPos = token.indexOf(':');
        if (colonPos > 0) {
            String rawKey = token.substring(0, colonPos);
            String canonicalKey = resolveFilterKey(rawKey);
            if (canonicalKey == null) {
                return null;
            }

            String rest = token.substring(colonPos + 1);
            if (rest.isEmpty()) {
                return null;
            }

            OpParse op = parseOperatorSuffix(rest);
            if (op != null) {
                String normValue = normalizeFilterValue(canonicalKey, op.stringValue);
                return SearchQuery.FilterClause.of(canonicalKey, op.op, normValue, op.intValue);
            }

            String normValue = normalizeFilterValue(canonicalKey, rest);
            return SearchQuery.FilterClause.of(canonicalKey, normValue);
        }

        OpParse op = parseOperatorSuffix(token);
        if (op != null) {
            String canonicalKey = resolveFilterKey(op.stringValue);
            if (canonicalKey != null) {
                return SearchQuery.FilterClause.of(canonicalKey, op.op, null, op.intValue);
            }
        }

        return null;
    }

    @Nullable
    private static OpParse parseOperatorSuffix(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '>' || c == '<' || c == '=') {
                String before = token.substring(0, i);
                if (before.isEmpty()) {
                    return null;
                }

                String numStr;
                SearchQuery.FilterClause.Operator op;

                if (i + 1 < token.length() && token.charAt(i + 1) == '=') {
                    numStr = token.substring(i + 2);
                    op = (c == '>')
                            ? SearchQuery.FilterClause.Operator.GTE
                            : SearchQuery.FilterClause.Operator.LTE;
                } else {
                    numStr = token.substring(i + 1);
                    op = switch (c) {
                        case '>' -> SearchQuery.FilterClause.Operator.GT;
                        case '<' -> SearchQuery.FilterClause.Operator.LT;
                        case '=' -> SearchQuery.FilterClause.Operator.EQ;
                        default -> null;
                    };
                }

                if (numStr.isEmpty() || op == null) {
                    return null;
                }

                try {
                    int value = Integer.parseInt(numStr);
                    return new OpParse(op, before, value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String resolveFilterKey(String raw) {
        return FILTER_KEY_ALIASES.get(raw);
    }

    private static String normalizeFilterValue(String key, String value) {
        if (value == null) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if ("rarity".equals(key)) {
            String alias = RARITY_ALIASES.get(lower);
            return alias != null ? alias : lower;
        }
        return lower;
    }

    @Nullable
    private static SearchQuery.StatClause tryParseStat(String token) {
        int opPos = -1;
        char opChar = '\0';
        for (int j = 0; j < token.length(); j++) {
            char c = token.charAt(j);
            if (c == '>' || c == '<' || c == '=') {
                opPos = j;
                opChar = c;
                break;
            }
        }
        if (opPos <= 0) {
            return null;
        }

        String statName = normalizeStatName(token.substring(0, opPos));
        if (statName.isEmpty()) {
            return null;
        }

        String valueStr;
        SearchQuery.StatClause.Operator op;

        if (opPos + 1 < token.length() && token.charAt(opPos + 1) == '=') {
            valueStr = token.substring(opPos + 2);
            op = (opChar == '>')
                    ? SearchQuery.StatClause.Operator.GTE
                    : SearchQuery.StatClause.Operator.LTE;
        } else {
            valueStr = token.substring(opPos + 1);
            op = switch (opChar) {
                case '>' -> SearchQuery.StatClause.Operator.GT;
                case '<' -> SearchQuery.StatClause.Operator.LT;
                case '=' -> SearchQuery.StatClause.Operator.EQ;
                default -> null;
            };
        }

        if (valueStr.isEmpty() || op == null) {
            return null;
        }

        try {
            int value = Integer.parseInt(valueStr);
            return new SearchQuery.StatClause(statName, op, value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Set the runtime-known stats from the compiled NEU binary.
     * Call this once after {@link com.github.kdgaming0.skyrecipes.core.data.BinaryDataLoader}
     * finishes loading.
     */
    public static void setRuntimeKnownStats(Set<String> stats) {
        runtimeKnownStats = stats != null ? Set.copyOf(stats) : null;
    }

    /**
     * Returns the effective set of known stat names.
     * Prefers runtime-generated stats; falls back to the hardcoded set.
     */
    public static Set<String> getKnownStats() {
        return runtimeKnownStats != null ? runtimeKnownStats : CANONICAL_STAT_NAMES;
    }

    /**
     * Returns all aliases that map to the given canonical stat name.
     * For example, "crit_damage" → {"cd"}.
     */
    public static Set<String> getAliasesForStat(String canonicalStatName) {
        return STAT_ALIAS_REVERSE.getOrDefault(canonicalStatName, Set.of());
    }

    private static String normalizeStatName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int j = 0; j < raw.length(); j++) {
            char c = raw.charAt(j);
            if (c == '_' || Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        String normalized = sb.toString();
        String alias = STAT_ALIASES.get(normalized);
        if (alias != null) return alias;
        // Canonicalize walk_speed → speed for consistency
        if ("walk_speed".equals(normalized)) return "speed";
        return normalized;
    }

    private static List<String> splitOnNonAlphanumeric(String token) {
        List<String> parts = new ArrayList<>(4);
        int len = token.length();
        int start = -1;

        for (int j = 0; j < len; j++) {
            char c = token.charAt(j);
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) {
                    start = j;
                }
            } else {
                if (start >= 0) {
                    if (j - start > 1 || (j - start == 1 && Character.isDigit(token.charAt(start)))) {
                        parts.add(token.substring(start, j));
                    }
                    start = -1;
                }
            }
        }

        if (start >= 0 && (len - start > 1 || (len - start == 1 && Character.isDigit(token.charAt(start))))) {
            parts.add(token.substring(start, len));
        }

        return parts.isEmpty() ? List.of(token) : parts;
    }

    // -- Keyword splitting -----------------------------------------------------

    private record OpParse(SearchQuery.FilterClause.Operator op, String stringValue, int intValue) {
    }
}
