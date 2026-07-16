package com.github.kdgaming0.skyrecipes.client.gui;

/**
 * Tracks whether the ghost text currently shown in the RRV search bar is something Tab may accept.
 *
 * <p>The search bar shows two different kinds of ghost text, and only one of them is a completion.
 * An autocomplete suggestion is the <em>suffix</em> of a matched name ("aspect" → "of the End"), so
 * appending it to the query is meaningful. A calculator result is standalone display text (" = 42"),
 * so appending it would corrupt the query into "5+3 = 42".
 *
 * <p>{@code SearchBarMixin} is the single place that decides which kind is being shown, so it
 * records the completion here and clears it whenever the ghost text is a calculator result or
 * absent. Consumers must additionally verify this value still matches the box's live suggestion
 * before acting on it, since the search bar can be rebuilt or cleared independently.
 */
public final class SearchSuggestionState {

    private static String completion = null;

    private SearchSuggestionState() {
    }

    /** Records the acceptable completion suffix currently shown as ghost text. */
    public static void setCompletion(String completion) {
        SearchSuggestionState.completion = completion;
    }

    /** Clears the completion, e.g. when the ghost text is a calculator result or absent. */
    public static void clear() {
        completion = null;
    }

    /** The completion suffix Tab may accept, or {@code null} if there is nothing to accept. */
    public static String getCompletion() {
        return completion;
    }
}
