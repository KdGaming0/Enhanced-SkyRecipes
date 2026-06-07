package com.github.kdgaming0.skyrecipes.rrv.recipe.util;

import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

/**
 * Shared UI helpers for SkyRecipes recipe rendering.
 */
public final class RecipeUiHelper {

    private RecipeUiHelper() {
    }

    /**
     * Formats a number with k/m suffixes when the value is large enough
     * that the suffix representation is exact with at most 3 decimals.
     * <p>Examples: 1000 → "1k", 1200 → "1.2k", 1230 → "1.23k",
     * 1230000 → "1.23m", 999 → "999".</p>
     *
     * @param value the raw number to format
     * @return compact string if it fits exactly, otherwise the raw number
     */
    public static String formatCompactNumber(int value) {
        return formatCompactNumber((long) value);
    }

    /**
     * Long overload of {@link #formatCompactNumber(int)}.
     */
    public static String formatCompactNumber(long value) {
        if (value >= 1_000_000) {
            BigDecimal divided = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(1_000_000));
            String s = divided.stripTrailingZeros().toPlainString();
            int decimals = s.contains(".") ? s.length() - s.indexOf(".") - 1 : 0;
            if (decimals <= 3) {
                return s + "m";
            }
        }
        if (value >= 1_000) {
            BigDecimal divided = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(1_000));
            String s = divided.stripTrailingZeros().toPlainString();
            return s + "k";
        }
        return String.valueOf(value);
    }

    /**
     * Strips common "Requires:" / "Requires " prefixes from NEU crafttext.
     *
     * @return the cleaned requirement text, or empty string if input is null/empty
     */
    public static String formatCraftText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int i = 0;
        int len = raw.length();
        while (i < len && raw.charAt(i) <= ' ') {
            i++;
        }
        if (i + 8 <= len) {
            boolean isRequires = (raw.charAt(i) == 'R' || raw.charAt(i) == 'r')
                    && (raw.charAt(i + 1) == 'E' || raw.charAt(i + 1) == 'e')
                    && (raw.charAt(i + 2) == 'Q' || raw.charAt(i + 2) == 'q')
                    && (raw.charAt(i + 3) == 'U' || raw.charAt(i + 3) == 'u')
                    && (raw.charAt(i + 4) == 'I' || raw.charAt(i + 4) == 'i')
                    && (raw.charAt(i + 5) == 'R' || raw.charAt(i + 5) == 'r')
                    && (raw.charAt(i + 6) == 'E' || raw.charAt(i + 6) == 'e')
                    && (raw.charAt(i + 7) == 'S' || raw.charAt(i + 7) == 's');
            if (isRequires) {
                i += 8;
                while (i < len && (raw.charAt(i) == ':' || raw.charAt(i) <= ' ')) {
                    i++;
                }
                return raw.substring(i);
            }
        }
        return raw.substring(i);
    }

    /**
     * Builds the standard requirement tooltip component.
     */
    public static Component requirementTooltip(String craftText) {
        return Component.literal("§cRequirement: §e" + formatCraftText(craftText));
    }

    /**
     * Formats a NEU slayer requirement (e.g. "WOLF_3") into readable text
     * (e.g. "Wolf Slayer 3").
     *
     * @return the formatted text, or empty string if input is null/empty
     */
    public static String formatSlayerReq(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int underscore = raw.lastIndexOf('_');
        if (underscore <= 0 || underscore >= raw.length() - 1) {
            return raw;
        }
        String type = raw.substring(0, underscore);
        String level = raw.substring(underscore + 1);
        return capitalize(type) + " Slayer " + level;
    }

    private static String capitalize(String s) {
        if (s.length() <= 1) {
            return s.toUpperCase();
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
