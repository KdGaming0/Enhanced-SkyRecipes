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

        // One registry pass; internal names are deferred so display names keep
        // first claim on colliding lowercase keys (tier priority).
        List<String> internalNames = new ArrayList<>();
        for (NeuItem item : itemRegistry.getAllItems()) {
            String name = TextUtil.stripColorCodes(item.displayName());
            if (!name.isBlank() && seen.add(name.toLowerCase())) {
                entries.add(new Entry(name, Tier.DISPLAY_NAME));
            }
            String internalName = item.internalName();
            if (!internalName.isBlank()) {
                internalNames.add(internalName);
            }
        }

        // Tier 2: internal names
        for (String name : internalNames) {
            if (seen.add(name.toLowerCase())) {
                entries.add(new Entry(name, Tier.INTERNAL_NAME));
            }
        }

        // Tier 3: aliases resolved to display names
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String aliasKey = alias.getKey();
            String targetId = alias.getValue();
            // Resolve alias to display name for better UX
            NeuItem target = itemRegistry.getOrNull(targetId);
            String displayName = aliasKey;
            if (target != null) {
                String stripped = TextUtil.stripColorCodes(target.displayName());
                if (!stripped.isBlank()) {
                    displayName = stripped;
                }
            }
            if (seen.add(displayName.toLowerCase())) {
                entries.add(new Entry(displayName, Tier.ALIAS));
            }
        }

        // Tier 4: page names
        for (String page : pageNames) {
            if (!page.isBlank() && seen.add(page.toLowerCase())) {
                entries.add(new Entry(page, Tier.PAGE_NAME));
            }
        }

        // Sort by text for deterministic binary search
        entries.sort(Comparator.comparing(Entry::lower));
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

        // Entries are sorted by lowercase text, so all prefix matches form a contiguous
        // range. Binary-search to its start instead of scanning every entry per keystroke.
        for (int i = lowerBound(q); i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.lower().startsWith(q)) {
                break;
            }
            results.add(new Suggestion(entry.text(), entry.tier()));
            if (results.size() >= maxResults * 2) {
                // Soft cap before sorting to avoid huge sorts
                break;
            }
        }

        // The prefix scan above visits entries in ascending Entry.lower order, so
        // results are already alphabetical; the stable sort by tier alone keeps that
        // order within each tier without re-lowercasing text in the comparator.
        results.sort(Comparator.comparingInt((Suggestion s) -> s.tier().priority));

        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /**
     * Index of the first entry whose lowercase text is &ge; {@code key}.
     *
     * <p>{@code entries} is sorted by {@link Entry#lower()} using natural string ordering,
     * so this is the start of the contiguous prefix-match range for {@code key}.</p>
     */
    private int lowerBound(String key) {
        int lo = 0;
        int hi = entries.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (entries.get(mid).lower().compareTo(key) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
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

    private record Entry(String text, String lower, Tier tier) {
        Entry(String text, Tier tier) {
            this(text, text.toLowerCase(), tier);
        }
    }

    /**
     * A single autocomplete suggestion.
     */
    public record Suggestion(String text, Tier tier) {
    }
}
