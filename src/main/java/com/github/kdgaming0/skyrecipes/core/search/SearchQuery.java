package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Immutable representation of a parsed search query.
 *
 * <p>Keywords are AND-ed together. Stat clauses are also AND-ed with keywords.
 * Filter clauses (e.g. {@code rarity:legendary}) are AND-ed.
 * Boolean flags are AND-ed.
 * Category path restricts to a single category/subtype.
 * Empty queries return the full item list.</p>
 */
public record SearchQuery(
        List<KeywordClause> keywords,
        List<StatClause> stats,
        List<FilterClause> filters,
        @Nullable CategoryPath categoryPath,
        Set<String> booleanFlags
) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
        stats = List.copyOf(stats);
        filters = List.copyOf(filters);
        booleanFlags = Set.copyOf(booleanFlags != null ? booleanFlags : Set.of());
    }

    public boolean isEmpty() {
        return keywords.isEmpty()
                && stats.isEmpty()
                && filters.isEmpty()
                && categoryPath == null
                && booleanFlags.isEmpty();
    }

    public record KeywordClause(String token) {
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
