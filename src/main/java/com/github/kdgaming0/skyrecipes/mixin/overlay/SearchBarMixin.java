package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionController;
import com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

/**
 * Provides autocomplete ghost-text suggestions in the RRV search bar.
 *
 * <p>Wires SkyRecipes' existing {@link SearchAutocomplete} into RRV's {@link SearchBar}
 * via {@link SearchBar#setSuggestion}. When the user types a prefix that matches a
 * display name, alias, or internal name, the remainder of the best match is shown as
 * gray ghost text after the cursor.</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class SearchBarMixin {

    @Shadow
    private SearchBar searchbar;

    /**
     * Computes the ghost-text suffix for a search suggestion.
     *
     * @param query     what the user has typed
     * @param fullMatch the complete matched text (e.g. "Aspect of the End")
     * @return the part of {@code fullMatch} that follows {@code query}, or null if
     * {@code fullMatch} does not start with {@code query} (case-insensitive)
     */
    @Unique
    private static String computeSuggestionSuffix(String query, String fullMatch) {
        String q = query.toLowerCase(Locale.ROOT);
        String fm = fullMatch.toLowerCase(Locale.ROOT);
        if (!fm.startsWith(q)) {
            return null;
        }
        return fullMatch.substring(query.length());
    }

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void skyrecipes$setSearchSuggestion(String newQuery, CallbackInfo ci) {
        if (searchbar == null) {
            return;
        }

        // Don't suggest on empty query
        if (newQuery == null || newQuery.isBlank()) {
            clearSuggestion();
            return;
        }

        SearchAutocomplete autocomplete = SkyRecipes.getSearchAutocomplete();
        if (autocomplete == null) {
            clearSuggestion();
            return;
        }

        List<SearchAutocomplete.Suggestion> suggestions = autocomplete.suggest(newQuery, 1);
        if (suggestions.isEmpty()) {
            clearSuggestion();
            return;
        }

        String bestMatch = suggestions.getFirst().text();
        String suggestion = computeSuggestionSuffix(newQuery, bestMatch);
        if (suggestion != null && !suggestion.isEmpty()) {
            SearchSuggestionController.show(searchbar, newQuery, suggestion);
        } else {
            clearSuggestion();
        }
    }

    @Unique
    private void clearSuggestion() {
        SearchSuggestionController.clear(searchbar);
    }
}
