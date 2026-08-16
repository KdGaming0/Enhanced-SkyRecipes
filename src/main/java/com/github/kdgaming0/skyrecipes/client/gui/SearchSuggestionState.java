package com.github.kdgaming0.skyrecipes.client.gui;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/** Tracks acceptance metadata for the ghost text owned by one search widget. */
public final class SearchSuggestionState {

    private static Completion completion;

    private SearchSuggestionState() {
    }

    public static void setCompletion(Object owner, String input, String suffix) {
        Completion current = completion;
        if (current != null && current.isOwnedBy(owner)
                && current.input().equals(input) && current.suffix().equals(suffix)) {
            return;
        }
        completion = new Completion(owner, input, suffix);
    }

    public static void clear(Object owner) {
        if (completion != null && completion.isOwnedBy(owner)) {
            completion = null;
        }
    }

    public static void clear() {
        completion = null;
    }

    public static @Nullable Completion getCompletion(Object owner) {
        Completion current = completion;
        return current != null && current.isOwnedBy(owner) ? current : null;
    }

    public static final class Completion {
        private final WeakReference<Object> owner;
        private final String input;
        private final String suffix;

        private Completion(Object owner, String input, String suffix) {
            this.owner = new WeakReference<>(owner);
            this.input = input;
            this.suffix = suffix;
        }

        private boolean isOwnedBy(Object candidate) {
            return owner.get() == candidate;
        }

        public String input() {
            return input;
        }

        public String suffix() {
            return suffix;
        }
    }
}
