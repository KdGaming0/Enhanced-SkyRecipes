package com.github.kdgaming0.skyrecipes.client.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SearchSuggestionStateTest {

    @AfterEach
    void clearState() {
        SearchSuggestionState.clear();
    }

    @Test
    void completionBelongsToTheWidgetThatCreatedIt() {
        Object owner = new Object();
        Object other = new Object();
        SearchSuggestionState.setCompletion(
                owner, "=sq", "rt(");

        SearchSuggestionState.Completion completion = SearchSuggestionState.getCompletion(owner);
        assertNull(SearchSuggestionState.getCompletion(other));

        SearchSuggestionState.clear(other);
        assertSame(completion, SearchSuggestionState.getCompletion(owner));
        SearchSuggestionState.clear(owner);
        assertNull(SearchSuggestionState.getCompletion(owner));
    }

    @Test
    void installingTheSameCompletionIsIdempotent() {
        Object owner = new Object();
        SearchSuggestionState.setCompletion(owner, "a", "ns");
        SearchSuggestionState.Completion first = SearchSuggestionState.getCompletion(owner);

        SearchSuggestionState.setCompletion(owner, "a", "ns");

        assertSame(first, SearchSuggestionState.getCompletion(owner));
    }
}
