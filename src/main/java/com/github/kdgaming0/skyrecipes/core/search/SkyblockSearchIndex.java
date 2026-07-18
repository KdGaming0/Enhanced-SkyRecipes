package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance inverted search index for all SkyBlock items.
 *
 * <p>Uses {@link BitSet} intersections for O(n/64) filter application.
 * Supports keywords, stat thresholds, structured filters, boolean flags,
 * category paths, and skill/catacombs requirements.</p>
 */
public final class SkyblockSearchIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockSearchIndex.class);

    private final int itemCount;
    private final List<ItemStack> items;
    private final ItemRegistry itemRegistry;

    // Token indices
    private final Map<String, BitSet> anyTokenIndex;
    private final Map<String, BitSet> nameTokenIndex;
    private final String[] sortedTokens;

    // Stat index: statName -> value -> items
    private final Map<String, TreeMap<Integer, BitSet>> statIndex;
    // Fast per-item stat values for sorting: statName -> int[itemCount], Integer.MIN_VALUE = absent
    private final Map<String, int[]> statValuesPerItem;

    // Filter indices
    private final Map<String, BitSet> rarityIndex;
    private final Map<String, BitSet> typeIndex;
    private final Map<SkyblockItemCategory, BitSet> categoryIndex;
    // Pet skill (mining/combat/...) -> pets, parsed from the "§8<Skill> Pet" subtitle.
    private final Map<String, BitSet> petSkillIndex;
    private final Map<String, BitSet> flagIndex;
    private final Map<String, BitSet> slayerTypeIndex;
    private final Map<String, TreeMap<Integer, BitSet>> slayerLevelIndex;
    private final Map<String, BitSet> skillTypeIndex;
    private final Map<String, TreeMap<Integer, BitSet>> skillLevelIndex;
    private final Map<String, BitSet> catacombsTypeIndex;
    private final Map<String, TreeMap<Integer, BitSet>> catacombsLevelIndex;

    // Inventory fast-path
    private final Map<String, Set<String>> idToTokens;

    // Phrase/regex authoritative text: per item, lowercased name + lore lines joined by
    // '\n'. searchText is indexed by item position; idToSearchText mirrors it by SkyBlock
    // id for the inventory-highlight path.
    private final String[] searchText;
    private final Map<String, String> idToSearchText;

    // Precomputed sort helpers
    private final String[] displayNames;
    private final boolean[] hasCraftingRecipe;
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private final boolean[] hasForgeRecipe;
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private final boolean[] hasNpcShop;
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private final boolean[] isBazaar;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<String, String> aliases;
    private final StatParser statParser;

    /**
     * Small LRU over keyword/prefix resolution. A query is re-resolved in full on
     * every keystroke, so the unchanged earlier tokens hit this cache and only the
     * token being edited pays resolution — including the fuzzy fallback scan.
     * Cached BitSets are shared: callers must treat them as read-only.
     */
    private static final int RESOLVE_CACHE_SIZE = 64;
    private final Map<String, BitSet> resolveCache = Collections.synchronizedMap(
            new LinkedHashMap<>(RESOLVE_CACHE_SIZE * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BitSet> eldest) {
                    return size() > RESOLVE_CACHE_SIZE;
                }
            });

    @SuppressWarnings("unused")
    public SkyblockSearchIndex(List<ItemStack> items, ItemRegistry itemRegistry,
                               ConstantsRegistry constantsRegistry) {
        this(items, itemRegistry, constantsRegistry, Map.of());
    }

    public SkyblockSearchIndex(List<ItemStack> items, ItemRegistry itemRegistry,
                               ConstantsRegistry constantsRegistry,
                               Map<String, String> aliases) {
        this.items = List.copyOf(items);
        this.itemCount = this.items.size();
        this.itemRegistry = itemRegistry;
        this.aliases = aliases;

        // Push runtime stats into the query parser so search validation is consistent
        SearchQueryParser.setRuntimeKnownStats(constantsRegistry.getKnownStats());
        this.statParser = new StatParser(constantsRegistry.getKnownStats());

        this.anyTokenIndex = new HashMap<>(8192);
        this.nameTokenIndex = new HashMap<>(4096);
        this.statIndex = new HashMap<>(128);
        this.statValuesPerItem = new HashMap<>(128);
        this.rarityIndex = new HashMap<>(16);
        this.typeIndex = new HashMap<>(64);
        this.categoryIndex = new EnumMap<>(SkyblockItemCategory.class);
        this.petSkillIndex = new HashMap<>(8);
        this.flagIndex = new HashMap<>(8);
        this.slayerTypeIndex = new HashMap<>(8);
        this.slayerLevelIndex = new HashMap<>(8);
        this.skillTypeIndex = new HashMap<>(16);
        this.skillLevelIndex = new HashMap<>(16);
        this.catacombsTypeIndex = new HashMap<>(8);
        this.catacombsLevelIndex = new HashMap<>(8);
        this.idToTokens = new HashMap<>(items.size());
        this.searchText = new String[itemCount];
        this.idToSearchText = new HashMap<>(items.size());

        this.displayNames = new String[itemCount];
        this.hasCraftingRecipe = new boolean[itemCount];
        this.hasForgeRecipe = new boolean[itemCount];
        this.hasNpcShop = new boolean[itemCount];
        this.isBazaar = new boolean[itemCount];

        // Phase 1 (parallel): pure per-item string work — id extraction, color
        // stripping, lowercasing, stat parsing. StatParser and TextUtil are
        // stateless and the registries immutable, so this fans out safely.
        PreparedItem[] prepared = new PreparedItem[itemCount];
        java.util.stream.IntStream.range(0, itemCount).parallel()
                .forEach(i -> prepared[i] = prepareItem(this.items.get(i)));

        // Phase 2 (serial): map/BitSet insertions, in the original item order.
        Map<String, Integer> idToIndex = new HashMap<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            indexItem(i, prepared[i], constantsRegistry);
            String itemId = prepared[i].itemId();
            if (itemId != null) {
                idToIndex.putIfAbsent(itemId.toLowerCase(), i);
            }
        }

        // Index alias tokens so searching "aote" matches Aspect of the End
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            Integer target = idToIndex.get(entry.getValue().toLowerCase());
            if (target != null) {
                String alias = entry.getKey().toLowerCase();
                addAnyToken(alias, target);
                addNameToken(alias, target);
            }
        }

        this.sortedTokens = anyTokenIndex.keySet().stream()
                .sorted()
                .toArray(String[]::new);

        LOGGER.info("SkyblockSearchIndex built: {} items, {} distinct tokens, {} stats, {} aliases",
                itemCount, sortedTokens.length, statIndex.size(), aliases.size());
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    private static BitSet resolveThreshold(TreeMap<Integer, BitSet> valueMap,
                                           SearchQuery.FilterClause.Operator op, int value) {
        BitSet result = new BitSet();
        switch (op) {
            case GT -> unionRange(result, valueMap.tailMap(value, false));
            case LT -> unionRange(result, valueMap.headMap(value, false));
            case GTE -> unionRange(result, valueMap.tailMap(value, true));
            case LTE -> unionRange(result, valueMap.headMap(value, true));
            case EQ -> {
                BitSet bs = valueMap.get(value);
                if (bs != null) result.or(bs);
            }
        }
        return result;
    }

    private static void unionRange(BitSet target, java.util.NavigableMap<Integer, BitSet> range) {
        for (BitSet bs : range.values()) {
            target.or(bs);
        }
    }

    private static <K> void addToken(Map<K, BitSet> index, K key, int itemIndex) {
        index.computeIfAbsent(key, _ -> new BitSet()).set(itemIndex);
    }

    private static void tokenize(String raw, TokenConsumer consumer) {
        if (raw == null) return;
        String clean = raw.toLowerCase();
        int len = clean.length();
        int start = -1;

        for (int i = 0; i < len; i++) {
            char c = clean.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (start < 0) start = i;
            } else {
                if (start >= 0) {
                    if (i - start > 1) {
                        consumer.accept(clean.substring(start, i));
                    }
                    start = -1;
                }
            }
        }
        if (start >= 0 && len - start > 1) {
            consumer.accept(clean.substring(start, len));
        }
    }

    // -----------------------------------------------------------------
    // Query resolution
    // -----------------------------------------------------------------

    private static boolean tokenSetContainsPrefix(Set<String> tokens, String prefix) {
        for (String t : tokens) {
            if (t.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Expects an already color-stripped last lore line (see {@link PreparedItem}). */
    private static String extractRarity(String clean) {
        String[] parts = clean.split("\\s+");
        for (String part : parts) {
            if (part.matches("[A-Z]+")) {
                return part.toLowerCase();
            }
        }
        return null;
    }

    private static String extractSlayerType(String slayerReq) {
        int underscore = slayerReq.indexOf('_');
        return (underscore > 0) ? slayerReq.substring(0, underscore).toLowerCase() : null;
    }

    private static int extractSlayerLevel(String slayerReq) {
        int underscore = slayerReq.lastIndexOf('_');
        if (underscore < 0 || underscore + 1 >= slayerReq.length()) return 0;
        try {
            return Integer.parseInt(slayerReq.substring(underscore + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeFilterToken(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '_') {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static int extractLeadingInt(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-' || s.charAt(i) == ' ')) {
            i++;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (start == i) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Extracts leading Roman numeral from a string, or null if none.
     */
    @Nullable
    private static String extractLeadingRoman(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '!' || s.charAt(i) == '.')) {
            i++;
        }
        int start = i;
        while (i < s.length() && isRomanChar(s.charAt(i))) {
            i++;
        }
        if (start == i) return null;
        String roman = s.substring(start, i);
        // Validate it's a real Roman numeral (at least 1 char, no more than 10)
        return roman.length() <= 10 ? roman : null;
    }

    private static boolean isRomanChar(char c) {
        return c == 'I' || c == 'V' || c == 'X' || c == 'L' || c == 'C' || c == 'D' || c == 'M';
    }

    private static int romanToInt(String roman) {
        int result = 0;
        int prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int val = switch (roman.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            if (val < prev) result -= val;
            else result += val;
            prev = val;
        }
        return result;
    }

    @Nullable
    private static String toSingular(String token) {
        if (token == null || token.length() < 4) return null;
        if (token.endsWith("ss")) return null;
        if (token.endsWith("es")) {
            String base = token.substring(0, token.length() - 2);
            return base.length() >= 3 ? base : null;
        }
        if (token.endsWith("s")) {
            String base = token.substring(0, token.length() - 1);
            return base.length() >= 3 ? base : null;
        }
        return null;
    }

    private static SearchQuery.FilterClause.Operator toFilterOperator(SearchQuery.StatClause.Operator op) {
        return switch (op) {
            case GT -> SearchQuery.FilterClause.Operator.GT;
            case LT -> SearchQuery.FilterClause.Operator.LT;
            case GTE -> SearchQuery.FilterClause.Operator.GTE;
            case LTE -> SearchQuery.FilterClause.Operator.LTE;
            case EQ -> SearchQuery.FilterClause.Operator.EQ;
        };
    }

    /**
     * Filter the item list by the given query string.
     */
    public List<ItemStack> filter(String query) {
        return filter(query, null, null);
    }

    /**
     * Filter with an optional category override (used by category buttons).
     */
    public List<ItemStack> filter(String query,
                                  @Nullable SkyblockItemCategory category,
                                  @Nullable String subtype) {
        SearchQuery parsed = SearchQueryParser.parse(query);
        if (parsed.isEmpty() && category == null) {
            return new ArrayList<>(items);
        }

        BitSet candidates = resolveQuery(parsed, category, subtype);
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        return rankToList(parsed, candidates);
    }

    /**
     * Fast boolean check for inventory slot highlighting.
     *
     * <p>Evaluates keywords, boolean flags, category, and string filters against
     * the item's precomputed token set. Returns {@code false} for numeric filters
     * (stats, slayer level, etc.) so the caller can fall back to lore scanning.</p>
     */
    public boolean itemMatchesInventoryQuery(String itemId, String query) {
        return itemMatchesInventoryQuery(itemId, SearchQueryParser.parse(query));
    }

    // -----------------------------------------------------------------
    // Ranking
    // -----------------------------------------------------------------

    /**
     * Variant of {@link #itemMatchesInventoryQuery(String, String)} taking an
     * already-parsed query, for callers that evaluate many slots against the same
     * query per frame and cache the parse.
     */
    public boolean itemMatchesInventoryQuery(String itemId, SearchQuery parsed) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }

        if (parsed.isEmpty()) {
            return true;
        }

        Set<String> tokens = idToTokens.get(itemId);
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }

        // Category check
        SearchQuery.CategoryPath cp = parsed.categoryPath();
        if (cp != null) {
            if (!tokens.contains(cp.category().name().toLowerCase())) {
                return false;
            }
            if (cp.subtype() != null && !cp.subtype().isEmpty()) {
                if (!tokens.contains(cp.subtype().toLowerCase())) {
                    return false;
                }
            }
        }

        // Boolean flags
        for (String flag : parsed.booleanFlags()) {
            if (!tokens.contains(flag)) {
                return false;
            }
        }

        // Keywords
        for (SearchQuery.KeywordClause kw : parsed.keywords()) {
            if (!tokenSetContainsPrefix(tokens, kw.token())) {
                return false;
            }
        }

        // String-only EQ filters
        for (SearchQuery.FilterClause f : parsed.filters()) {
            if ("pet".equals(f.key())) {
                // Pet-skill filter ("mining pet" / pet:mining): require the exact skill token,
                // not a generic keyword, so highlighting agrees with the canonical item list.
                if (f.stringValue() == null || !tokens.contains("petskill:" + f.stringValue())) {
                    return false;
                }
            } else if (f.stringValue() != null && (f.op() == null || f.op() == SearchQuery.FilterClause.Operator.EQ)) {
                if (!tokenSetContainsPrefix(tokens, f.stringValue())) {
                    return false;
                }
            } else {
                return false; // numeric filter needs lore fallback
            }
        }

        // Phrases and regex: verify against the item's stored name+lore text. Without this
        // a pure-phrase query (empty keywords/filters) would pass every check above and
        // wrongly highlight every slot.
        if (!parsed.phrases().isEmpty() || !parsed.regexes().isEmpty()) {
            String text = idToSearchText.get(itemId);
            if (text == null) {
                return false;
            }
            for (SearchQuery.PhraseClause phrase : parsed.phrases()) {
                if (text.indexOf(phrase.text()) < 0) {
                    return false;
                }
            }
            for (SearchQuery.RegexClause regex : parsed.regexes()) {
                if (!regexFindWithinLine(text, regex.pattern())) {
                    return false;
                }
            }
        }

        // Stat clauses cannot be evaluated for inventory
        return parsed.stats().isEmpty();
    }

    /**
     * Returns the set of SkyBlock IDs that match the given query.
     */
    @SuppressWarnings("unused")
    public Set<String> getMatchingIds(String query) {
        SearchQuery parsed = SearchQueryParser.parse(query);
        if (parsed.isEmpty()) {
            return new HashSet<>(idToTokens.keySet());
        }

        BitSet candidates = resolveQuery(parsed, null, null);
        Set<String> ids = new HashSet<>(candidates.cardinality());
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            String id = SkyblockIdExtractor.extract(items.get(i));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private BitSet resolveQuery(SearchQuery query,
                                @Nullable SkyblockItemCategory category,
                                @Nullable String subtype) {
        BitSet candidates = new BitSet(itemCount);

        // Start with category subset or all items
        if (category != null) {
            BitSet catBits = categoryIndex.get(category);
            if (catBits == null || catBits.isEmpty()) {
                return candidates;
            }
            candidates.or(catBits);
            if (subtype != null && !subtype.isEmpty()) {
                BitSet subBits = typeIndex.get(subtype.toLowerCase());
                if (subBits != null) {
                    candidates.and(subBits);
                } else {
                    return new BitSet(); // no items match subtype
                }
            }
        } else if (query.categoryPath() != null) {
            BitSet catBits = categoryIndex.get(query.categoryPath().category());
            if (catBits == null || catBits.isEmpty()) {
                return candidates;
            }
            candidates.or(catBits);
            String sub = query.categoryPath().subtype();
            if (sub != null && !sub.isEmpty()) {
                BitSet subBits = typeIndex.get(sub.toLowerCase());
                if (subBits != null) {
                    candidates.and(subBits);
                } else {
                    return new BitSet();
                }
            }
        } else {
            candidates.set(0, itemCount);
        }

        // Boolean flags (cheap)
        for (String flag : query.booleanFlags()) {
            BitSet flagBits = flagIndex.get(flag);
            if (flagBits == null) {
                candidates.clear();
                return candidates;
            }
            candidates.and(flagBits);
            if (candidates.isEmpty()) return candidates;
        }

        // Filters (cheap to moderate)
        for (SearchQuery.FilterClause filter : query.filters()) {
            BitSet filterMatches = resolveFilter(filter);
            candidates.and(filterMatches);
            if (candidates.isEmpty()) return candidates;
        }

        // Stats (moderate)
        for (SearchQuery.StatClause stat : query.stats()) {
            BitSet statMatches = resolveStat(stat);
            candidates.and(statMatches);
            if (candidates.isEmpty()) return candidates;
        }

        // Keywords (most expensive token op — do last on smallest candidate set)
        if (!query.keywords().isEmpty()) {
            BitSet keywordMatches = resolveKeywords(query.keywords());
            candidates.and(keywordMatches);
            if (candidates.isEmpty()) return candidates;
        }

        // Phrases and regex are verified against the stored name+lore text. They scan only
        // the surviving candidates, so the cheaper clauses above keep the scan small.
        for (SearchQuery.PhraseClause phrase : query.phrases()) {
            retainPhraseMatches(candidates, phrase.text());
            if (candidates.isEmpty()) return candidates;
        }
        for (SearchQuery.RegexClause regex : query.regexes()) {
            retainRegexMatches(candidates, regex.pattern());
            if (candidates.isEmpty()) return candidates;
        }

        return candidates;
    }

    /**
     * Clears every candidate whose stored search text does not contain {@code phrase} as a
     * contiguous substring. {@code phrase} and {@code searchText} are both lowercased, and
     * the '\n' line separators in the stored text stop a phrase from spanning two lines.
     */
    private void retainPhraseMatches(BitSet candidates, String phrase) {
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            String text = searchText[i];
            if (text == null || text.indexOf(phrase) < 0) {
                candidates.clear(i);
            }
        }
    }

    /**
     * Clears every candidate whose stored search text does not match {@code pattern} within
     * a single line (see {@link #regexFindWithinLine}).
     */
    private void retainRegexMatches(BitSet candidates, Pattern pattern) {
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            String text = searchText[i];
            if (text == null || !regexFindWithinLine(text, pattern)) {
                candidates.clear(i);
            }
        }
    }

    /**
     * Returns whether {@code pattern} matches within any single line of {@code text} (lines
     * split on '\n'). Each line is matched in isolation via {@link Matcher#region}, keeping
     * regex line-scoped like {@code grep} so {@code \s} or {@code .} cannot bridge two lore
     * lines — mirroring the within-line semantics of quoted phrases. Allocates nothing
     * beyond the single reused matcher. Public so the inventory-highlight path can
     * evaluate regex clauses against live stack text with the same semantics.
     */
    public static boolean regexFindWithinLine(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int length = text.length();
        int start = 0;
        while (start <= length) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? length : newline;
            matcher.region(start, end);
            if (matcher.find()) {
                return true;
            }
            if (newline < 0) {
                break;
            }
            start = newline + 1;
        }
        return false;
    }

    private BitSet resolveKeywords(List<SearchQuery.KeywordClause> keywords) {
        return resolveKeywords(keywords, this::resolveKeyword);
    }

    /**
     * AND-combines the per-keyword matches produced by {@code resolver}. Returns an empty
     * set as soon as any keyword matches nothing.
     */
    private BitSet resolveKeywords(List<SearchQuery.KeywordClause> keywords,
                                   Function<String, BitSet> resolver) {
        BitSet result = new BitSet(itemCount);
        boolean first = true;
        for (SearchQuery.KeywordClause kw : keywords) {
            BitSet matches = resolver.apply(kw.token());
            if (matches.isEmpty()) {
                return new BitSet();
            }
            if (first) {
                result.or(matches);
                first = false;
            } else {
                result.and(matches);
            }
        }
        return result;
    }

    private BitSet resolveKeyword(String token) {
        String cacheKey = "kw:" + token;
        BitSet cached = resolveCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BitSet result = resolveKeywordUncached(token);
        resolveCache.put(cacheKey, result);
        return result;
    }

    private BitSet resolveKeywordUncached(String token) {
        // Purely numeric tokens: exact match only. Prefix expansion would make "1"
        // match "10"/"12s", and the fuzzy fallback would make "12" match "13" — both
        // wrong for tier segments split out of IDs like COBBLESTONE_GENERATOR_12.
        if (isAllDigits(token)) {
            BitSet exact = anyTokenIndex.get(token);
            return exact != null ? (BitSet) exact.clone() : new BitSet();
        }

        // Prefix match (also covers exact matches since sortedTokens contains all indexed tokens)
        BitSet prefix = resolvePrefixUnion(token);
        if (!prefix.isEmpty()) {
            return prefix;
        }

        // Fuzzy fallback
        BitSet fuzzy = fuzzyMatch(token);
        if (!fuzzy.isEmpty()) {
            return fuzzy;
        }

        // Singular fallback
        String singular = toSingular(token);
        if (singular != null) {
            prefix = resolvePrefixUnion(singular);
            if (!prefix.isEmpty()) {
                return prefix;
            }
        }

        return new BitSet();
    }

    private BitSet resolvePrefixUnion(String prefix) {
        String cacheKey = "pfx:" + prefix;
        BitSet cached = resolveCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BitSet result = resolvePrefixUnion(prefix, anyTokenIndex);
        resolveCache.put(cacheKey, result);
        return result;
    }

    /**
     * Unions the item sets of every indexed token sharing {@code prefix}, looked up in the
     * supplied token index. Short prefixes are capped to bound expansion on huge fan-outs.
     */
    private BitSet resolvePrefixUnion(String prefix, Map<String, BitSet> tokenIndex) {
        BitSet result = new BitSet(itemCount);
        if (prefix.isEmpty()) return result;

        int idx = Arrays.binarySearch(sortedTokens, prefix);
        if (idx < 0) idx = -idx - 1;

        int maxExpansion = switch (prefix.length()) {
            case 1 -> 256;
            case 2 -> 1024;
            default -> Integer.MAX_VALUE;
        };

        int count = 0;
        for (int i = idx; i < sortedTokens.length && sortedTokens[i].startsWith(prefix); i++) {
            BitSet bs = tokenIndex.get(sortedTokens[i]);
            if (bs != null) result.or(bs);
            if (++count >= maxExpansion) break;
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Index building
    // -----------------------------------------------------------------

    private BitSet fuzzyMatch(String token) {
        BitSet result = new BitSet(itemCount);
        FuzzyTokenMatcher.Scratch scratch = new FuzzyTokenMatcher.Scratch();
        int tokenLen = token.length();
        for (String candidate : sortedTokens) {
            // Edit distance is at least the length difference — skip candidates
            // that can never come under the threshold before entering the DP.
            if (Math.abs(candidate.length() - tokenLen) > 2) {
                continue;
            }
            if (FuzzyTokenMatcher.matches(token, candidate, 2, scratch)) {
                BitSet bs = anyTokenIndex.get(candidate);
                if (bs != null) result.or(bs);
            }
        }
        return result;
    }

    private BitSet resolveStat(SearchQuery.StatClause stat) {
        TreeMap<Integer, BitSet> valueMap = statIndex.get(stat.statName());
        if (valueMap == null || valueMap.isEmpty()) {
            return new BitSet();
        }
        return resolveThreshold(valueMap, toFilterOperator(stat.op()), stat.value());
    }

    private BitSet resolveFilter(SearchQuery.FilterClause filter) {
        return switch (filter.key()) {
            case "rarity" -> resolveRarityFilter(filter);
            case "type" -> resolveTypeFilter(filter);
            case "pet" -> resolvePetSkillFilter(filter);
            case "slayer" -> resolveSlayerFilter(filter);
            case "skill" -> resolveSkillFilter(filter);
            case "catacombs" -> resolveCatacombsFilter(filter);
            default -> new BitSet();
        };
    }

    /**
     * Resolves a {@code pet:<skill>} filter to the pets of that skill. The bare phrase
     * "mining pet" is rewritten to this filter by {@link SearchQueryParser}, so both forms
     * return the same canonical set.
     */
    private BitSet resolvePetSkillFilter(SearchQuery.FilterClause filter) {
        String skill = filter.stringValue();
        if (skill == null || skill.isEmpty()) return new BitSet();
        BitSet exact = petSkillIndex.get(skill);
        return exact != null ? (BitSet) exact.clone() : new BitSet();
    }

    private BitSet resolveRarityFilter(SearchQuery.FilterClause filter) {
        String value = filter.stringValue();
        if (value == null || value.isEmpty()) return new BitSet();
        BitSet exact = rarityIndex.get(value);
        return exact != null ? (BitSet) exact.clone() : new BitSet();
    }

    private BitSet resolveTypeFilter(SearchQuery.FilterClause filter) {
        String value = filter.stringValue();
        if (value == null || value.isEmpty()) return new BitSet();
        BitSet result = new BitSet();
        BitSet exact = typeIndex.get(value);
        if (exact != null) result.or(exact);
        // Prefix / substring match
        for (Map.Entry<String, BitSet> e : typeIndex.entrySet()) {
            if (e.getKey().startsWith(value) || e.getKey().contains("_" + value)) {
                result.or(e.getValue());
            }
        }
        return result;
    }

    private BitSet resolveSlayerFilter(SearchQuery.FilterClause filter) {
        return resolveTypedLevelFilter(filter, slayerTypeIndex, slayerLevelIndex);
    }

    private BitSet resolveSkillFilter(SearchQuery.FilterClause filter) {
        return resolveTypedLevelFilter(filter, skillTypeIndex, skillLevelIndex);
    }

    /**
     * Resolves a requirement filter backed by a type index ({@code type -> items}) and a
     * level index ({@code type -> level -> items}), e.g. slayer or skill requirements.
     *
     * <p>With an explicit type and a comparison operator, applies the threshold to that
     * type's levels. With a type but no operator (or EQ), returns all items requiring that
     * type. With no type, aggregates across every type.</p>
     */
    private BitSet resolveTypedLevelFilter(SearchQuery.FilterClause filter,
                                           Map<String, BitSet> typeIndexMap,
                                           Map<String, TreeMap<Integer, BitSet>> levelIndexMap) {
        String type = filter.stringValue();
        SearchQuery.FilterClause.Operator op = filter.op();

        if (type != null && !type.isEmpty()) {
            if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
                BitSet exact = typeIndexMap.get(type);
                return exact != null ? (BitSet) exact.clone() : new BitSet();
            }
            TreeMap<Integer, BitSet> levelMap = levelIndexMap.get(type);
            if (levelMap == null || levelMap.isEmpty()) return new BitSet();
            return resolveThreshold(levelMap, op, filter.intValue());
        }

        if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
            BitSet result = new BitSet();
            for (BitSet bs : typeIndexMap.values()) result.or(bs);
            return result;
        }
        BitSet result = new BitSet();
        for (TreeMap<Integer, BitSet> levelMap : levelIndexMap.values()) {
            result.or(resolveThreshold(levelMap, op, filter.intValue()));
        }
        return result;
    }

    private BitSet resolveCatacombsFilter(SearchQuery.FilterClause filter) {
        SearchQuery.FilterClause.Operator op = filter.op();
        if (op == null || op == SearchQuery.FilterClause.Operator.EQ) {
            BitSet result = new BitSet();
            for (BitSet bs : catacombsTypeIndex.values()) result.or(bs);
            return result;
        }
        BitSet result = new BitSet();
        for (TreeMap<Integer, BitSet> levelMap : catacombsLevelIndex.values()) {
            result.or(resolveThreshold(levelMap, op, filter.intValue()));
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Token helpers
    // -----------------------------------------------------------------

    private List<ItemStack> rankToList(SearchQuery query, BitSet candidates) {
        List<ItemStack> result = new ArrayList<>(candidates.cardinality());

        if (!query.keywords().isEmpty()) {
            // Tier 1: name-only matches
            BitSet nameMatches = resolveKeywordsInName(query.keywords());
            nameMatches.and(candidates);

            // Tier 2: other matches
            BitSet otherMatches = (BitSet) candidates.clone();
            otherMatches.andNot(nameMatches);

            // Tier 3: prefix-only (single keyword, no other constraints)
            BitSet prefixMatches = new BitSet();
            if (query.keywords().size() == 1
                    && query.stats().isEmpty()
                    && query.filters().isEmpty()
                    && query.booleanFlags().isEmpty()
                    && query.categoryPath() == null) {
                prefixMatches.or(resolvePrefixUnion(query.keywords().getFirst().token()));
                prefixMatches.and(candidates);
                prefixMatches.andNot(nameMatches);
                prefixMatches.andNot(otherMatches);
            }

            addSorted(nameMatches, result);
            addSorted(otherMatches, result);
            addSorted(prefixMatches, result);
        } else if (!query.stats().isEmpty()) {
            // Sort by first stat value
            addSortedByStat(candidates, result, query.stats().getFirst());
        } else {
            addSorted(candidates, result);
        }

        return result;
    }

    private BitSet resolveKeywordsInName(List<SearchQuery.KeywordClause> keywords) {
        return resolveKeywords(keywords, this::resolveKeywordInName);
    }

    private BitSet resolveKeywordInName(String token) {
        String cacheKey = "nm:" + token;
        BitSet cached = resolveCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BitSet result = resolveKeywordInNameUncached(token);
        resolveCache.put(cacheKey, result);
        return result;
    }

    private BitSet resolveKeywordInNameUncached(String token) {
        BitSet exact = nameTokenIndex.get(token);
        BitSet prefix = resolvePrefixUnionInName(token);
        if (exact != null) prefix.or(exact);
        if (!prefix.isEmpty()) return prefix;

        String singular = toSingular(token);
        if (singular != null) {
            exact = nameTokenIndex.get(singular);
            prefix = resolvePrefixUnionInName(singular);
            if (exact != null) prefix.or(exact);
            if (!prefix.isEmpty()) return prefix;
        }
        return new BitSet();
    }

    private BitSet resolvePrefixUnionInName(String prefix) {
        return resolvePrefixUnion(prefix, nameTokenIndex);
    }

    private void addSorted(BitSet bits, List<ItemStack> out) {
        int card = bits.cardinality();
        if (card == 0) return;

        // Preserve original item order (which is family-sorted from buildAllStacks).
        // BitSet iteration naturally yields indices in ascending order.
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            out.add(items.get(i));
        }
    }

    // -----------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------

    private void addSortedByStat(BitSet bits, List<ItemStack> out, SearchQuery.StatClause stat) {
        int card = bits.cardinality();
        if (card == 0) return;

        int[] values = statValuesPerItem.get(stat.statName());
        boolean desc = stat.op() == SearchQuery.StatClause.Operator.GT
                || stat.op() == SearchQuery.StatClause.Operator.GTE;

        Integer[] boxed = new Integer[card];
        int idx = 0;
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            boxed[idx++] = i;
        }

        Arrays.sort(boxed, (a, b) -> {
            int va = values != null ? values[a] : Integer.MIN_VALUE;
            int vb = values != null ? values[b] : Integer.MIN_VALUE;
            int cmp = desc ? Integer.compare(vb, va) : Integer.compare(va, vb);
            if (cmp != 0) return cmp;
            cmp = Boolean.compare(hasCraftingRecipe[b], hasCraftingRecipe[a]);
            if (cmp != 0) return cmp;
            return displayNames[a].compareToIgnoreCase(displayNames[b]);
        });

        for (int i : boxed) {
            out.add(items.get(i));
        }
    }

    /**
     * Pure per-item precomputation for index building: everything derivable from the
     * stack and NEU item without touching the shared index maps. Computed in parallel;
     * consumed by the serial insertion pass. The clean/lower lore arrays also stop each
     * lore line being color-stripped and lowercased five times over by the stat,
     * requirement, and flag scanners.
     */
    private record PreparedItem(@Nullable NeuItem neuItem, @Nullable String itemId,
                                String displayName, String[] cleanLore,
                                String[] cleanLoreLower, List<StatParser.ParsedStat>[] loreStats) {
    }

    @SuppressWarnings("unchecked")
    private PreparedItem prepareItem(ItemStack stack) {
        String itemId = SkyblockIdExtractor.extract(stack);
        NeuItem neuItem = itemId != null ? itemRegistry.getOrNull(itemId) : null;
        String displayName = TextUtil.stripColorCodes(stack.getHoverName().getString());

        String[] cleanLore = null;
        String[] cleanLoreLower = null;
        List<StatParser.ParsedStat>[] loreStats = null;
        List<String> lore = neuItem != null ? neuItem.lore() : null;
        if (lore != null) {
            int n = lore.size();
            cleanLore = new String[n];
            cleanLoreLower = new String[n];
            loreStats = new List[n];
            for (int j = 0; j < n; j++) {
                String clean = TextUtil.stripColorCodes(lore.get(j));
                cleanLore[j] = clean;
                cleanLoreLower[j] = clean.toLowerCase();
                loreStats[j] = statParser.parseLoreLineStripped(clean);
            }
        }
        return new PreparedItem(neuItem, itemId, displayName, cleanLore, cleanLoreLower, loreStats);
    }

    private void indexItem(int itemIndex, PreparedItem prep, ConstantsRegistry constantsRegistry) {
        String itemId = prep.itemId();
        Set<String> itemTokens = itemId != null ? new HashSet<>(32) : null;

        // Display name
        String displayName = prep.displayName();
        displayNames[itemIndex] = displayName;

        // Phrase/regex search text: name + each lore line, lowercased and '\n'-separated so
        // a phrase can never span a line boundary. Built from text already being
        // color-stripped for tokenization, so it adds no extra passes.
        StringBuilder searchBuf = new StringBuilder(128);
        searchBuf.append(displayName.toLowerCase());

        tokenize(displayName, (token) -> {
            addNameToken(token, itemIndex);
            if (itemTokens != null) itemTokens.add(token);
        });

        if (prep.neuItem() != null) {
            indexNeuItem(itemIndex, prep, itemTokens, constantsRegistry, searchBuf);
        }

        String text = searchBuf.toString();
        searchText[itemIndex] = text;
        if (itemId != null) {
            idToSearchText.put(itemId, text);
        }

        //noinspection ConstantValue
        if (itemId != null && itemTokens != null && !itemTokens.isEmpty()) {
            idToTokens.put(itemId, Set.copyOf(itemTokens));
        }
    }

    private void indexNeuItem(int itemIndex, PreparedItem prep,
                              Set<String> itemTokens, ConstantsRegistry constantsRegistry,
                              StringBuilder searchBuf) {
        try {
            indexNeuItemInternal(itemIndex, prep, itemTokens, constantsRegistry, searchBuf);
        } catch (Exception e) {
            LOGGER.warn("Failed to index item {}", prep.neuItem().internalName(), e);
        }
    }

    private void indexNeuItemInternal(int itemIndex, PreparedItem prep,
                                      Set<String> itemTokens, ConstantsRegistry constantsRegistry,
                                      StringBuilder searchBuf) {
        NeuItem neuItem = prep.neuItem();
        // Internal name
        if (neuItem.internalName() != null) {
            String lower = neuItem.internalName().toLowerCase();
            addAnyToken(lower, itemIndex);
            if (itemTokens != null) {
                itemTokens.add(lower);
                for (String part : lower.split("[_;]")) {
                    if (part.length() > 1 || (part.length() == 1 && Character.isDigit(part.charAt(0)))) {
                        itemTokens.add(part);
                        addAnyToken(part, itemIndex);
                    }
                }
            }
        }

        // Lore (pre-stripped/lowercased/stat-parsed in the parallel phase)
        String[] cleanLore = prep.cleanLore();
        if (cleanLore != null) {
            for (int j = 0; j < cleanLore.length; j++) {
                String clean = cleanLore[j];
                searchBuf.append('\n').append(prep.cleanLoreLower()[j]);
                tokenize(clean, (token) -> {
                    addAnyToken(token, itemIndex);
                    if (itemTokens != null) itemTokens.add(token);
                });
                for (StatParser.ParsedStat stat : prep.loreStats()[j]) {
                    indexStat(itemIndex, stat.statName(), stat.value(), itemTokens);
                }
            }
        }

        // Rarity from last lore line
        if (cleanLore != null && cleanLore.length > 0) {
            String rarity = extractRarity(cleanLore[cleanLore.length - 1]);
            if (rarity != null) {
                addAnyToken(rarity, itemIndex);
                if (itemTokens != null) itemTokens.add(rarity);
                addToken(rarityIndex, rarity, itemIndex);
            }
        }

        // Type inference & category
        String type = ItemCategoryResolver.inferType(neuItem);
        if (type != null) {
            addAnyToken(type, itemIndex);
            if (itemTokens != null) itemTokens.add(type);
            addToken(typeIndex, type, itemIndex);
        }

        SkyblockItemCategory category = ItemCategoryResolver.resolve(neuItem);
        if (category != SkyblockItemCategory.UNKNOWN) {
            addToken(categoryIndex, category, itemIndex);
            String catName = category.name().toLowerCase();
            addAnyToken(catName, itemIndex);
            if (itemTokens != null) itemTokens.add(catName);

            // Also index under the button category so button toggles catch these items
            SkyblockItemCategory buttonCat = category.getButtonCategory();
            if (buttonCat != category) {
                addToken(categoryIndex, buttonCat, itemIndex);
                String btnName = buttonCat.name().toLowerCase();
                addAnyToken(btnName, itemIndex);
                if (itemTokens != null) itemTokens.add(btnName);
            }
        } else if (cleanLore != null && cleanLore.length > 0) {
            // Items with lore but no recognized type go to MISC
            addToken(categoryIndex, SkyblockItemCategory.MISC, itemIndex);
            addAnyToken("misc", itemIndex);
            if (itemTokens != null) itemTokens.add("misc");
        }

        // The "pet"/"accessory" boolean flags are declared in SearchQueryParser but were
        // never added to flagIndex, so resolveQuery returned nothing for them. Populate them
        // from the resolved category (the matching inventory tokens already exist via catName).
        // For pets, also index the skill from the "§8<Skill> Pet" subtitle so "mining pet" and
        // pet:mining resolve to exactly that skill's pets.
        if (category == SkyblockItemCategory.PET) {
            addToken(flagIndex, "pet", itemIndex);
            String petSkill = PetSkillResolver.resolve(neuItem);
            if (petSkill != null) {
                addToken(petSkillIndex, petSkill, itemIndex);
                if (itemTokens != null) itemTokens.add("petskill:" + petSkill);
            }
        } else if (category == SkyblockItemCategory.ACCESSORY) {
            addToken(flagIndex, "accessory", itemIndex);
        }

        // Reforge stone — explicit constant data first, then lore fallback
        boolean reforgeIndexed = false;
        ReforgeStoneData stone = constantsRegistry.getReforgeStone(neuItem.internalName());
        if (stone != null && stone.reforgeName() != null) {
            String reforgeName = stone.reforgeName().toLowerCase();
            tokenize(reforgeName, (token) -> {
                addAnyToken(token, itemIndex);
                if (itemTokens != null) itemTokens.add(token);
            });
            reforgeIndexed = true;
        }

        // Reverse map lookup: if this item is a known reforge stone, index the reforge name
        if (!reforgeIndexed) {
            for (Map.Entry<String, String> entry : constantsRegistry.getReforgeNameToStone().entrySet()) {
                if (entry.getValue().equalsIgnoreCase(neuItem.internalName())) {
                    String reforgeName = entry.getKey().toLowerCase();
                    tokenize(reforgeName, (token) -> {
                        addAnyToken(token, itemIndex);
                        if (itemTokens != null) itemTokens.add(token);
                    });
                    reforgeIndexed = true;
                    break;
                }
            }
        }

        // Lore fallback for stones missing from constants (e.g. GEOMETRIC_ODDITY)
        if (!reforgeIndexed && cleanLore != null) {
            for (int j = 0; j < cleanLore.length; j++) {
                String clean = cleanLore[j];
                String lower = prep.cleanLoreLower()[j];
                int appliesIdx = lower.indexOf("applies the ");
                if (appliesIdx >= 0) {
                    int reforgeStart = appliesIdx + 12;
                    int reforgeEnd = lower.indexOf(" reforge", reforgeStart);
                    if (reforgeEnd > reforgeStart) {
                        String reforgeName = clean.substring(reforgeStart, reforgeEnd).trim().toLowerCase();
                        tokenize(reforgeName, (token) -> {
                            addAnyToken(token, itemIndex);
                            if (itemTokens != null) itemTokens.add(token);
                        });
                        //noinspection UnusedAssignment
                        reforgeIndexed = true;
                        break;
                    }
                }
            }
        }

        // Slayer requirement (explicit field + lore fallback)
        indexSlayerRequirements(itemIndex, prep, itemTokens);

        // Skill requirements from lore
        indexSkillRequirements(itemIndex, prep, itemTokens);

        // Catacombs requirements from lore
        indexCatacombsRequirements(itemIndex, prep, itemTokens);

        // Boolean flags
        indexFlags(itemIndex, prep, itemTokens, constantsRegistry);

        // Craft text
        if (neuItem.craftText() != null && !neuItem.craftText().isEmpty()) {
            tokenize(neuItem.craftText(), (token) -> {
                addAnyToken(token, itemIndex);
                if (itemTokens != null) itemTokens.add(token);
            });
        }

        // Precomputed sort helpers
        hasCraftingRecipe[itemIndex] = neuItem.recipe() != null;
        if (neuItem.recipes() != null) {
            for (NeuRecipe r : neuItem.recipes()) {
                if (r instanceof NeuRecipe.ForgeRecipe) hasForgeRecipe[itemIndex] = true;
                if (r instanceof NeuRecipe.NpcShopRecipe) hasNpcShop[itemIndex] = true;
            }
        }
        isBazaar[itemIndex] = constantsRegistry.isBazaarItem(neuItem.internalName());
    }

    private void indexStat(int itemIndex, String statName, int value, Set<String> itemTokens) {
        // Index stat
        TreeMap<Integer, BitSet> valueMap = statIndex.computeIfAbsent(statName, _ -> new TreeMap<>());
        valueMap.computeIfAbsent(value, _ -> new BitSet()).set(itemIndex);

        // Store per-item value for sorting
        int[] perItem = statValuesPerItem.computeIfAbsent(statName, _ -> {
            int[] arr = new int[itemCount];
            Arrays.fill(arr, Integer.MIN_VALUE);
            return arr;
        });
        perItem[itemIndex] = value;

        // Tokens
        String statToken = statName + ":" + value;
        addAnyToken(statToken, itemIndex);
        if (itemTokens != null) itemTokens.add(statToken);
        addAnyToken(statName, itemIndex);
        if (itemTokens != null) itemTokens.add(statName);

        // Also index alias tokens so typing "cd" finds items with Crit Damage
        for (String alias : SearchQueryParser.getAliasesForStat(statName)) {
            addAnyToken(alias, itemIndex);
            if (itemTokens != null) itemTokens.add(alias);
        }
    }

    private void indexSkillRequirements(int itemIndex, PreparedItem prep, Set<String> itemTokens) {
        String[] cleanLoreLower = prep.cleanLoreLower();
        if (cleanLoreLower == null || cleanLoreLower.length == 0) return;
        for (String clean : cleanLoreLower) {
            if (!clean.contains("requires") || !clean.contains(" skill ")) continue;
            if (clean.contains("catacombs")) continue;

            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx < 0) continue;

            String afterSkill = clean.substring(skillIdx + 7).trim();
            int end = 0;
            for (int i = 0; i < afterSkill.length(); i++) {
                char c = afterSkill.charAt(i);
                if (c >= '0' && c <= '9') end = i + 1;
            }
            if (end == 0) continue;

            int level;
            try {
                level = Integer.parseInt(afterSkill.substring(0, end));
            } catch (NumberFormatException e) {
                continue;
            }

            String beforeSkill = clean.substring(0, skillIdx).trim();
            int lastSpace = beforeSkill.lastIndexOf(' ');
            String skillName = lastSpace >= 0 ? beforeSkill.substring(lastSpace + 1) : beforeSkill;
            skillName = normalizeFilterToken(skillName);
            if (skillName.isEmpty()) continue;

            addAnyToken(skillName, itemIndex);
            if (itemTokens != null) itemTokens.add(skillName);
            addToken(skillTypeIndex, skillName, itemIndex);
            addAnyToken("skill", itemIndex);
            if (itemTokens != null) itemTokens.add("skill");

            TreeMap<Integer, BitSet> levelMap = skillLevelIndex.computeIfAbsent(skillName, _ -> new TreeMap<>());
            levelMap.computeIfAbsent(level, _ -> new BitSet()).set(itemIndex);
        }
    }

    private void indexCatacombsRequirements(int itemIndex, PreparedItem prep, Set<String> itemTokens) {
        String[] cleanLoreLower = prep.cleanLoreLower();
        if (cleanLoreLower == null || cleanLoreLower.length == 0) return;
        for (String clean : cleanLoreLower) {
            if (!clean.contains("catacombs")) continue;

            // "Requires The Catacombs Floor [ROMAN] Completion."
            int floorIdx = clean.indexOf("floor ");
            if (floorIdx >= 0 && clean.contains("requires") && clean.contains("completion")) {
                String afterFloor = clean.substring(floorIdx + 6).trim();
                // Remove trailing punctuation
                int end = 0;
                for (int i = 0; i < afterFloor.length(); i++) {
                    char c = afterFloor.charAt(i);
                    if (isRomanChar(c)) end = i + 1;
                    else break;
                }
                if (end > 0) {
                    int level = romanToInt(afterFloor.substring(0, end));
                    if (level > 0) {
                        addAnyToken("catacombs", itemIndex);
                        if (itemTokens != null) itemTokens.add("catacombs");
                        addToken(catacombsTypeIndex, "catacombs", itemIndex);
                        TreeMap<Integer, BitSet> levelMap = catacombsLevelIndex
                                .computeIfAbsent("catacombs", _ -> new TreeMap<>());
                        levelMap.computeIfAbsent(level, _ -> new BitSet()).set(itemIndex);
                    }
                }
                continue;
            }

            // "Requires Catacombs Skill [N]."
            int skillIdx = clean.indexOf(" skill ");
            if (skillIdx >= 0 && clean.contains("requires")) {
                String afterSkill = clean.substring(skillIdx + 7).trim();
                int end = 0;
                for (int i = 0; i < afterSkill.length(); i++) {
                    char c = afterSkill.charAt(i);
                    if (c >= '0' && c <= '9') end = i + 1;
                }
                if (end > 0) {
                    try {
                        int level = Integer.parseInt(afterSkill.substring(0, end));
                        addAnyToken("catacombs", itemIndex);
                        if (itemTokens != null) itemTokens.add("catacombs");
                        addToken(catacombsTypeIndex, "catacombs", itemIndex);
                        TreeMap<Integer, BitSet> levelMap = catacombsLevelIndex
                                .computeIfAbsent("catacombs", _ -> new TreeMap<>());
                        levelMap.computeIfAbsent(level, _ -> new BitSet()).set(itemIndex);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    private void indexFlags(int itemIndex, PreparedItem prep, Set<String> itemTokens,
                            ConstantsRegistry constantsRegistry) {
        NeuItem neuItem = prep.neuItem();
        String[] cleanLore = prep.cleanLore();
        if (cleanLore == null) return;

        boolean soulbound = false;
        boolean dungeon = false;
        boolean rift = false;

        for (String clean : cleanLore) {
            if (clean.contains("* Soulbound *") || clean.contains("* Co-op Soulbound *")) {
                soulbound = true;
            }
            if (clean.contains("Dungeons only!")) {
                dungeon = true;
            }
            if (clean.contains("Rift") && !clean.contains("Requirements")) {
                rift = true;
            }
        }

        // Dungeon also from type
        String cleanLast = (cleanLore.length == 0 ? "" : cleanLore[cleanLore.length - 1]).toUpperCase();
        if (cleanLast.contains("DUNGEON")) {
            dungeon = true;
        }
        if (cleanLast.contains("RIFT")) {
            rift = true;
        }

        if (soulbound) {
            addToken(flagIndex, "soulbound", itemIndex);
            if (itemTokens != null) itemTokens.add("soulbound");
        }
        if (dungeon) {
            addToken(flagIndex, "dungeon", itemIndex);
            if (itemTokens != null) itemTokens.add("dungeon");
        }
        if (rift) {
            addToken(flagIndex, "rift", itemIndex);
            if (itemTokens != null) itemTokens.add("rift");
        }
        if (neuItem.vanilla()) {
            addToken(flagIndex, "vanilla", itemIndex);
            if (itemTokens != null) itemTokens.add("vanilla");
        }
        if (constantsRegistry.isBazaarItem(neuItem.internalName())) {
            addToken(flagIndex, "bazaar", itemIndex);
            if (itemTokens != null) itemTokens.add("bazaar");
        }
        if (neuItem.recipe() != null) {
            addToken(flagIndex, "craftable", itemIndex);
            if (itemTokens != null) itemTokens.add("craftable");
        }
        if (neuItem.recipes() != null) {
            for (NeuRecipe r : neuItem.recipes()) {
                if (r instanceof NeuRecipe.ForgeRecipe) {
                    addToken(flagIndex, "forgeable", itemIndex);
                    if (itemTokens != null) itemTokens.add("forgeable");
                }
                if (r instanceof NeuRecipe.NpcShopRecipe) {
                    addToken(flagIndex, "npc", itemIndex);
                    if (itemTokens != null) itemTokens.add("npc");
                }
            }
        }
    }

    private void addNameToken(String token, int itemIndex) {
        if (isTooShortToIndex(token)) return;
        addToken(nameTokenIndex, token, itemIndex);
        addToken(anyTokenIndex, token, itemIndex);
    }

    private void addAnyToken(String token, int itemIndex) {
        if (isTooShortToIndex(token)) return;
        addToken(anyTokenIndex, token, itemIndex);
    }

    /**
     * Single-character tokens are dropped except digits: internal-name segments like the
     * "0" in {@code GIRAFFE;0} or the "1" in {@code COBBLESTONE_GENERATOR_1} must be
     * indexed, because the query parser emits them as standalone keyword clauses and
     * {@link #resolveKeywordUncached} resolves them by exact lookup.
     */
    private static boolean isTooShortToIndex(String token) {
        if (token.isEmpty()) return true;
        return token.length() == 1 && !Character.isDigit(token.charAt(0));
    }

    private static boolean isAllDigits(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private void indexSlayerRequirements(int itemIndex, PreparedItem prep, Set<String> itemTokens) {
        NeuItem neuItem = prep.neuItem();

        // 1. Explicit slayerReq field
        if (neuItem.slayerReq() != null && !neuItem.slayerReq().isEmpty()) {
            String slayerType = extractSlayerType(neuItem.slayerReq());
            int slayerLevel = extractSlayerLevel(neuItem.slayerReq());
            if (slayerType != null) {
                indexSlayer(itemIndex, slayerType, slayerLevel, itemTokens);
            }
        }

        // 2. Lore fallback: "Requires §5Zombie Slayer 5§c." or "§4☠ §cRequires §5Spider Slayer 4§c."
        String[] cleanLore = prep.cleanLore();
        if (cleanLore != null) {
            for (int j = 0; j < cleanLore.length; j++) {
                String clean = cleanLore[j];
                // Match "Requires <Name> Slayer <Level>" or "<Name> Slayer <Level>"
                int slayerIdx = prep.cleanLoreLower()[j].indexOf(" slayer ");
                if (slayerIdx > 0) {
                    String before = clean.substring(0, slayerIdx).trim();
                    String after = clean.substring(slayerIdx + 8).trim(); // after " slayer "

                    // Extract slayer type (last word before "Slayer")
                    int lastSpace = before.lastIndexOf(' ');
                    String slayerType = (lastSpace >= 0 ? before.substring(lastSpace + 1) : before).toLowerCase();

                    // Extract level (leading digits or Roman numerals after "Slayer")
                    int level = extractLeadingInt(after);
                    if (level <= 0) {
                        String roman = extractLeadingRoman(after);
                        if (roman != null) {
                            level = romanToInt(roman);
                        }
                    }
                    if (level > 0 && !slayerType.isEmpty()) {
                        indexSlayer(itemIndex, slayerType, level, itemTokens);
                    }
                }
            }
        }

        // 3. Crafttext fallback: "Requires: Zombie Slayer 5"
        if (neuItem.craftText() != null && !neuItem.craftText().isEmpty()) {
            String clean = TextUtil.stripColorCodes(neuItem.craftText());
            int slayerIdx = clean.toLowerCase().indexOf(" slayer ");
            if (slayerIdx > 0) {
                String before = clean.substring(0, slayerIdx).trim();
                String after = clean.substring(slayerIdx + 8).trim();
                int lastSpace = before.lastIndexOf(' ');
                String slayerType = (lastSpace >= 0 ? before.substring(lastSpace + 1) : before).toLowerCase();
                int level = extractLeadingInt(after);
                if (level > 0 && !slayerType.isEmpty()) {
                    indexSlayer(itemIndex, slayerType, level, itemTokens);
                }
            }
        }
    }

    private void indexSlayer(int itemIndex, String slayerType, int slayerLevel, Set<String> itemTokens) {
        // Canonicalize slayer type names
        if ("ender".equals(slayerType)) slayerType = "enderman";
        addAnyToken(slayerType, itemIndex);
        if (itemTokens != null) itemTokens.add(slayerType);
        addToken(slayerTypeIndex, slayerType, itemIndex);

        if (slayerLevel > 0) {
            String composite = slayerType + slayerLevel;
            addAnyToken(composite, itemIndex);
            if (itemTokens != null) itemTokens.add(composite);

            TreeMap<Integer, BitSet> levelMap = slayerLevelIndex
                    .computeIfAbsent(slayerType, _ -> new TreeMap<>());
            levelMap.computeIfAbsent(slayerLevel, _ -> new BitSet()).set(itemIndex);
        }
    }

    @FunctionalInterface
    private interface TokenConsumer {
        void accept(String token);
    }
}
