package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable representation of a parsed search query.
 *
 * <p>Keywords are AND-ed together. Stat clauses are also AND-ed with keywords.
 * Filter clauses (e.g. {@code rarity:legendary}) are AND-ed.
 * Boolean flags are AND-ed.
 * Phrase clauses (from {@code "..."}) and regex clauses (from {@code /.../}) are
 * AND-ed too; both are verified against the item's stored name+lore text so a
 * phrase only matches when contiguous within a single line.
 * Category path restricts to a single category/subtype.
 * Empty queries return the full item list.</p>
 */
public record SearchQuery(
        List<KeywordClause> keywords,
        List<StatClause> stats,
        List<FilterClause> filters,
        @Nullable CategoryPath categoryPath,
        Set<String> booleanFlags,
        List<PhraseClause> phrases,
        List<RegexClause> regexes
) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
        stats = List.copyOf(stats);
        filters = List.copyOf(filters);
        booleanFlags = Set.copyOf(booleanFlags != null ? booleanFlags : Set.of());
        phrases = List.copyOf(phrases != null ? phrases : List.of());
        regexes = List.copyOf(regexes != null ? regexes : List.of());
    }

    public boolean isEmpty() {
        return keywords.isEmpty()
                && stats.isEmpty()
                && filters.isEmpty()
                && categoryPath == null
                && booleanFlags.isEmpty()
                && phrases.isEmpty()
                && regexes.isEmpty();
    }

    public record KeywordClause(String token) {
    }

    /**
     * Literal phrase from {@code "..."} syntax. {@code text} is already lowercased;
     * it matches only when it appears as a contiguous substring within the item name
     * or a single lore line (case-insensitive).
     */
    public record PhraseClause(String text) {
    }

    /**
     * Regex clause from {@code /.../} syntax. {@code pattern} is pre-compiled
     * {@link Pattern#CASE_INSENSITIVE} from the original-case source so metacharacters
     * are preserved; {@code raw} is kept for diagnostics. Matched with
     * {@link java.util.regex.Matcher#find()} against the item's name+lore text.
     */
    public record RegexClause(Pattern pattern, String raw) {
    }

    public record StatClause(String statName, Operator op, int value) {
        public enum Operator {
            GT, LT, GTE, LTE, EQ
        }
    }

    /**
     * Structured filter clause parsed from {@code key:value} or {@code key>value} syntax.
     */
    public record FilterClause(String key, Operator op,
                               @Nullable String stringValue, int intValue) {
        public static FilterClause of(String key, String stringValue) {
            return new FilterClause(key, Operator.EQ, stringValue, 0);
        }

        public static FilterClause of(String key, int intValue) {
            return new FilterClause(key, Operator.EQ, null, intValue);
        }

        public static FilterClause of(String key, Operator op, int intValue) {
            return new FilterClause(key, op, null, intValue);
        }

        public static FilterClause of(String key, Operator op, String stringValue, int intValue) {
            return new FilterClause(key, op, stringValue, intValue);
        }

        public enum Operator {
            EQ, GT, LT, GTE, LTE
        }
    }

    /**
     * Category path from {@code %CATEGORY} or {@code %CATEGORY/SUBTYPE} syntax.
     */
    public record CategoryPath(SkyblockItemCategory category, @Nullable String subtype) {
    }
}
