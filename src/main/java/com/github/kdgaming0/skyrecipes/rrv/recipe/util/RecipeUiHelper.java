package com.github.kdgaming0.skyrecipes.rrv.recipe.util;

import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

/**
 * Shared UI helpers for SkyRecipes recipe rendering.
 */
public final class RecipeUiHelper {

    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int WIKI_BUTTON_SIZE = 12;
    public static final int WIKI_BUTTON_OFFSET = 16;

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
     * Formats a duration in seconds into a human-readable string.
     *
     * @param totalSeconds total duration in seconds
     * @param includeDays  if {@code true}, days are shown (e.g. "2d 3h 4m 5s");
     *                     if {@code false}, hours wrap (e.g. "27h 4m 5s")
     * @param prefix       text to prepend (e.g. "Duration: " or "Time: ")
     * @return formatted component
     */
    public static Component formatDuration(int totalSeconds, boolean includeDays, String prefix) {
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        int days = 0;
        if (includeDays) {
            days = hours / 24;
            hours = hours % 24;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return Component.literal(sb.toString());
    }

    /**
     * Returns the Minecraft section-formatting colour code for a SkyBlock rarity name.
     *
     * @param rarity the rarity string (e.g. "LEGENDARY"), may be {@code null}
     * @return the colour code (e.g. "§6"), or "§7" if unknown
     */
    public static String rarityColorCode(String rarity) {
        if (rarity == null) {
            return "§7";
        }
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            case "SPECIAL", "VERY_SPECIAL" -> "§c";
            case "ULTIMATE", "ADMIN" -> "§4";
            default -> "§7";
        };
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
        return TextUtil.capitalize(type) + " Slayer " + level;
    }
}
