package com.github.kdgaming0.skyrecipes.core.search;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parses stat lines from NEU item lore.
 *
 * <p>SkyBlock item stats typically appear as:</p>
 * <ul>
 *   <li>{@code §7Strength: §c+10}  — colon format</li>
 *   <li>{@code §7+10☠ Crit Damage} — no-colon format</li>
 * </ul>
 *
 * <p>A compile-time-generated {@code knownStats} whitelist prevents false
 * positives (e.g. "Cost: 1000" or "Requires: Zombie Slayer 5").</p>
 */
public final class StatParser {

    private final Set<String> knownStats;

    /** Canonical stat names sorted by length (longest first) for no-colon parsing. */
    private final List<String> sortedStatNames;

    public StatParser(Set<String> knownStats) {
        this.knownStats = knownStats != null ? knownStats : Set.of();
        List<String> list = new ArrayList<>(this.knownStats);
        list.sort((a, b) -> Integer.compare(b.length(), a.length()));
        this.sortedStatNames = List.copyOf(list);
    }

    /**
     * Parsed stat result.
     */
    public record ParsedStat(String statName, int value) {}

    /**
     * Extract all stats from a single lore line.
     *
     * @param loreLine raw lore line with § color codes
     * @return list of parsed stats (empty if none)
     */
    public List<ParsedStat> parseLoreLine(String loreLine) {
        if (loreLine == null || loreLine.isEmpty()) {
            return List.of();
        }

        List<ParsedStat> result = new ArrayList<>(2);

        ParsedStat colon = parseColonFormat(loreLine);
        if (colon != null) {
            result.add(colon);
        }

        result.addAll(parseNoColonFormat(loreLine));

        return result.isEmpty() ? List.of() : result;
    }

    // -----------------------------------------------------------------
    // Colon format: "Strength: +10"
    // -----------------------------------------------------------------

    @Nullable
    private ParsedStat parseColonFormat(String loreLine) {
        String clean = stripColorCodes(loreLine);
        int colonIdx = clean.indexOf(':');
        if (colonIdx <= 0) return null;

        String statName = clean.substring(0, colonIdx).trim().toLowerCase();
        statName = normalizeStatName(statName);
        if (statName.isEmpty()) return null;

        // Validate against known stat names to avoid false positives like "Archer: +2"
        if (!knownStats.contains(statName)) return null;

        String valuePart = clean.substring(colonIdx + 1).trim();
        int value = extractLeadingInt(valuePart);
        if (value == Integer.MIN_VALUE) return null;

        return new ParsedStat(statName, value);
    }

    // -----------------------------------------------------------------
    // No-colon format: "+10☠ Crit Damage"
    // -----------------------------------------------------------------

    private List<ParsedStat> parseNoColonFormat(String loreLine) {
        String clean = stripColorCodes(loreLine);
        // Skip lines that already have a colon (handled by parseColonFormat)
        if (clean.indexOf(':') >= 0) return List.of();
        // Quick reject: no + or - sign means no stat value
        if (clean.indexOf('+') < 0 && clean.indexOf('-') < 0) return List.of();

        String lower = clean.toLowerCase();
        List<ParsedStat> result = new ArrayList<>(2);

        for (String statName : sortedStatNames) {
            String spaced = statName.replace('_', ' ');
            int pos = indexOfWord(lower, spaced);
            if (pos >= 0) {
                int value = extractIntBeforePosition(lower, pos);
                if (value != Integer.MIN_VALUE) {
                    result.add(new ParsedStat(statName, value));
                }
            }
        }
        return result;
    }

    /** Finds the given word/phrase as a whole-word match in text. */
    private static int indexOfWord(String text, String phrase) {
        int pos = text.indexOf(phrase);
        while (pos >= 0) {
            boolean leftBoundary = pos == 0 || !Character.isLetterOrDigit(text.charAt(pos - 1));
            int end = pos + phrase.length();
            boolean rightBoundary = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftBoundary && rightBoundary) {
                return pos;
            }
            pos = text.indexOf(phrase, pos + 1);
        }
        return -1;
    }

    /**
     * Extracts the integer immediately before the given position in text.
     * For example, in "+46☠ crit damage", extractIntBeforePosition(text, 11) returns 46.
     */
    public static int extractIntBeforePosition(String text, int pos) {
        // Scan backwards to find the last digit before pos
        int end = -1;
        for (int i = pos - 1; i >= 0; i--) {
            if (Character.isDigit(text.charAt(i))) {
                end = i + 1;
                break;
            }
        }
        if (end <= 0) return Integer.MIN_VALUE;

        int start = end - 1;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            start--;
        }
        // Include a preceding + or - sign
        if (start > 0 && (text.charAt(start - 1) == '+' || text.charAt(start - 1) == '-')) {
            start--;
        }

        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static int extractLeadingInt(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-' || s.charAt(i) == ' ')) {
            i++;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (start == i) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static String normalizeStatName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String normalized = sb.toString();
        // Canonicalize walk_speed → speed for consistency
        if ("walk_speed".equals(normalized)) return "speed";
        return normalized;
    }

    private static String stripColorCodes(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
