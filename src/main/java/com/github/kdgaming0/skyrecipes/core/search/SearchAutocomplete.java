package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;

import java.util.*;

/**
 * Four-tier autocomplete engine for the search bar.
 *
 * <p>Suggestions are drawn from four sources in priority order:</p>
 * <ol>
 *   <li>Display names (highest priority)</li>
 *   <li>Internal names</li>
 *   <li>Aliases / acronyms</li>
 *   <li>Page / recipe type names (lowest priority)</li>
 * </ol>
 */
public final class SearchAutocomplete {

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Build the autocomplete index from registries and alias maps.
     *
     * @param itemRegistry all NEU items
     * @param aliases      map of alias text -> target internal name (for tiering)
     * @param pageNames    list of page / recipe type display names
     */
    public SearchAutocomplete(ItemRegistry itemRegistry,
                              Map<String, String> aliases,
                              List<String> pageNames) {
        Set<String> seen = new HashSet<>();

        // Tier 1: display names
        for (NeuItem item : itemRegistry.getAllItems()) {
            String name = TextUtil.stripColorCodes(item.displayName());
            if (!name.isBlank() && seen.add(name.toLowerCase())) {
                entries.add(new Entry(name, Tier.DISPLAY_NAME, 0));
            }
        }

        // Tier 2: internal names
        for (NeuItem item : itemRegistry.getAllItems()) {
            String name = item.internalName();
            if (!name.isBlank() && seen.add(name.toLowerCase())) {
                entries.add(new Entry(name, Tier.INTERNAL_NAME, 0));
            }
        }

        // Tier 3: aliases resolved to display names
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String aliasKey = alias.getKey();
            String targetId = alias.getValue();
            // Resolve alias to display name for better UX
            String displayName = itemRegistry.getByInternalName(targetId)
                    .map(item -> TextUtil.stripColorCodes(item.displayName()))
                    .filter(name -> !name.isBlank())
                    .orElse(aliasKey);
            if (seen.add(displayName.toLowerCase())) {
                entries.add(new Entry(displayName, Tier.ALIAS, 0));
            }
        }

        // Tier 4: page names
        for (String page : pageNames) {
            if (!page.isBlank() && seen.add(page.toLowerCase())) {
                entries.add(new Entry(page, Tier.PAGE_NAME, 0));
            }
        }

        // Sort by text for deterministic binary search
        entries.sort(Comparator.comparing(e -> e.text().toLowerCase()));
    }

    /**
     * Return up to {@code maxResults} suggestions for the given query prefix.
     *
     * @param query      the user's partial query (case-insensitive)
     * @param maxResults maximum number of suggestions to return
     * @return list of suggestions, sorted by tier then alphabetically
     */
    public List<Suggestion> suggest(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.toLowerCase();
        List<Suggestion> results = new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.text().toLowerCase().startsWith(q)) {
                results.add(new Suggestion(entry.text(), entry.tier()));
                if (results.size() >= maxResults * 2) {
                    // Soft cap before sorting to avoid huge sorts
                    break;
                }
            }
        }

        // Sort by tier priority, then alphabetically
        results.sort(Comparator
                .comparingInt((Suggestion s) -> s.tier().priority)
                .thenComparing(s -> s.text().toLowerCase()));

        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /**
     * Return suggestions that match the query via fuzzy matching.
     *
     * @param query       the user's query
     * @param maxResults  maximum number of suggestions
     * @param maxDistance maximum Damerau–Levenshtein distance
     * @return fuzzy suggestions
     */
    public List<Suggestion> suggestFuzzy(String query, int maxResults, int maxDistance) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.toLowerCase();
        List<Suggestion> results = new ArrayList<>();

        for (Entry entry : entries) {
            if (FuzzyTokenMatcher.matches(q, entry.text().toLowerCase(), maxDistance)) {
                results.add(new Suggestion(entry.text(), entry.tier()));
            }
        }

        results.sort(Comparator
                .comparingInt((Suggestion s) -> s.tier().priority)
                .thenComparing(s -> s.text().toLowerCase()));

        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    /**
     * Suggestion tier determines display priority.
     */
    public enum Tier {
        DISPLAY_NAME(0),
        INTERNAL_NAME(1),
        ALIAS(2),
        PAGE_NAME(3);

        public final int priority;

        Tier(int priority) {
            this.priority = priority;
        }
    }

    private record Entry(String text, Tier tier, int priority) {
    }

    /**
     * A single autocomplete suggestion.
     */
    public record Suggestion(String text, Tier tier) {
    }
}
