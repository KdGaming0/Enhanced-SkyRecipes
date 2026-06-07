package com.github.kdgaming0.skyrecipes.core.util;

/**
 * Shared text-processing utilities for SkyRecipes.
 *
 * <p>Centralises common string operations that were previously duplicated
 * across search, rendering, data compilation, and recipe packages.</p>
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * Strips Minecraft section-formatting codes ({@code §}) from a string.
     *
     * @param text the raw text, may be {@code null}
     * @return the cleaned text, or an empty string if the input is {@code null}
     */
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++; // skip formatting code
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Capitalises the first character of a string and lower-cases the rest.
     *
     * @param s the input string, may be {@code null} or empty
     * @return capitalised form, or the original string if {@code null}/empty
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.length() == 1) {
            return s.toUpperCase();
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
