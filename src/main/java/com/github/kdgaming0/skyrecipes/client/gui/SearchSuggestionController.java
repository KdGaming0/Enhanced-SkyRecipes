package com.github.kdgaming0.skyrecipes.client.gui;

import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.mixin.accessor.EditBoxAccessor;

/** Keeps an RRV search bar's native ghost text and acceptance metadata in sync. */
public final class SearchSuggestionController {

    private SearchSuggestionController() {
    }

    public static void show(SearchBar searchbar, String input, String suffix) {
        if (!suffix.equals(((EditBoxAccessor) searchbar).skyrecipes$getSuggestion())) {
            searchbar.setSuggestion(suffix);
        }
        SearchSuggestionState.setCompletion(searchbar, input, suffix);
    }

    public static void clear(SearchBar searchbar) {
        if (((EditBoxAccessor) searchbar).skyrecipes$getSuggestion() != null) {
            searchbar.setSuggestion(null);
        }
        SearchSuggestionState.clear(searchbar);
    }
}
