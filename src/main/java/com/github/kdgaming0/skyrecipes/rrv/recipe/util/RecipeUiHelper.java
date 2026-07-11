package com.github.kdgaming0.skyrecipes.rrv.recipe.util;

import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    // ── Text wrapping ──────────────────────────────────────────────────────────

    /**
     * Splits {@code text} into lines that each fit within {@code maxWidth} pixels.
     * Prefers word boundaries; falls back to mid-word splits. Preserves leading
     * § colour/formatting codes across line breaks.
     */
    public static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String remaining = text;
        String activeCodes = "";

        while (!remaining.isEmpty()) {
            String leading = extractLeadingCodes(remaining);
            if (!leading.isEmpty()) {
                activeCodes = updateActiveCodes(activeCodes, leading);
            }
            String content = remaining.substring(leading.length());

            String prefix = activeCodes;
            int fit = fitLength(font, prefix + content, maxWidth);
            int contentFit = Math.max(1, fit - prefix.length());

            int splitAt = contentFit;
            if (contentFit < content.length()) {
                while (splitAt > 0 && content.charAt(splitAt - 1) != ' ') {
                    splitAt--;
                }
                if (splitAt == 0) {
                    splitAt = contentFit; // force mid-word
                }
            }

            String line = (prefix + content.substring(0, splitAt)).stripTrailing();
            if (!line.isEmpty()) {
                lines.add(line);
            }

            activeCodes = updateActiveCodes(activeCodes, content.substring(0, splitAt));
            remaining = content.substring(splitAt).stripLeading();
        }

        return lines;
    }

    private static String extractLeadingCodes(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '§') {
            sb.append(text.charAt(i));
            sb.append(text.charAt(i + 1));
            i += 2;
        }
        return sb.toString();
    }

    private static String updateActiveCodes(String current, String text) {
        String combined = current + text;
        String color = null;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;

        for (int i = 0; i + 1 < combined.length(); i++) {
            if (combined.charAt(i) == '§') {
                char c = combined.charAt(i + 1);
                switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                         'a', 'b', 'c', 'd', 'e', 'f',
                         'A', 'B', 'C', 'D', 'E', 'F' -> color = String.valueOf(c);
                    case 'k', 'K' -> obf = true;
                    case 'l', 'L' -> bold = true;
                    case 'm', 'M' -> strike = true;
                    case 'n', 'N' -> under = true;
                    case 'o', 'O' -> italic = true;
                    case 'r', 'R' -> {
                        color = null;
                        bold = italic = under = strike = obf = false;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (color != null) sb.append('§').append(color);
        if (obf) sb.append("§k");
        if (bold) sb.append("§l");
        if (strike) sb.append("§m");
        if (under) sb.append("§n");
        if (italic) sb.append("§o");
        return sb.toString();
    }

    /**
     * Binary-search the number of leading characters of {@code text} that fit.
     */
    private static int fitLength(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.length();
        }
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
