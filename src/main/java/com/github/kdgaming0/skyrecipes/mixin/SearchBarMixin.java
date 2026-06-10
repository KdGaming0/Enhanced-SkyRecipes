package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.SearchBarCalculator;
import com.github.kdgaming0.skyrecipes.core.search.SearchAutocomplete;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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
    private static String computeSuggestionSuffix(String query, String fullMatch) {
        String q = query.toLowerCase();
        String fm = fullMatch.toLowerCase();
        if (!fm.startsWith(q)) {
            return null;
        }
        return fullMatch.substring(query.length());
    }

    private static boolean looksLikeMath(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '^' || c == '%' || c == '=' || c == 'x' || c == 'X') {
                return true;
            }
            if (c == '-' && i > 0 && Character.isDigit(s.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void skyrecipes$setSearchSuggestion(String newQuery, CallbackInfo ci) {
        if (searchbar == null) {
            return;
        }

        // Don't suggest on empty query
        if (newQuery == null || newQuery.isBlank()) {
            searchbar.setSuggestion(null);
            return;
        }

        // Calculator: evaluate math expressions and show result as ghost text
        if (SkyRecipesConfig.calculatorEnabled && looksLikeMath(newQuery)) {
            String suggestion = SearchBarCalculator.calculateSuggestion(newQuery);
            searchbar.setSuggestion(suggestion);
            return;
        }

        SearchAutocomplete autocomplete = SkyRecipes.getSearchAutocomplete();
        if (autocomplete == null) {
            searchbar.setSuggestion(null);
            return;
        }

        List<SearchAutocomplete.Suggestion> suggestions = autocomplete.suggest(newQuery, 1);
        if (suggestions.isEmpty()) {
            searchbar.setSuggestion(null);
            return;
        }

        String bestMatch = suggestions.getFirst().text();
        String suggestion = computeSuggestionSuffix(newQuery, bestMatch);
        if (suggestion != null && !suggestion.isEmpty()) {
            searchbar.setSuggestion(suggestion);
        } else {
            searchbar.setSuggestion(null);
        }
    }
}
