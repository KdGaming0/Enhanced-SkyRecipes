package com.github.kdgaming0.skyrecipes.core.render;

import com.github.kdgaming0.skyrecipes.core.hypixel.HypixelItemsRegistry;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link ItemStack}s for essence upgrade recipes with star indicators
 * and scaled stat lines in the lore.
 *
 * <p>Resolution order for stat values:</p>
 * <ol>
 *   <li>Hypixel API {@code tiered_stats} — authoritative per-star values when available.</li>
 *   <li>Hypixel API {@code stats} — base values scaled by {@code base × (1 + 0.02 × star)}.</li>
 *   <li>NEU item lore — parsed base stats with the same +2%/star fallback.</li>
 * </ol>
 *
 * <p>Input stacks show {@code star-1} stars; output stacks show {@code star}
 * stars with green delta indicators.</p>
 */
public final class StarredItemBuilder {

    /**
     * Default 2 % scaling per star used when no tiered table exists.
     */
    private static final double PERCENT_PER_STAR = 0.02;

    private static final String DELTA_COLOR = "§a";
    private static final String DELTA_BRACKETS = "§8";

    /**
     * Maps NEU lore stat labels to Hypixel API stat keys (UPPER_SNAKE).
     */
    private static final Map<String, String> STAT_TO_API_KEY = Map.ofEntries(
            Map.entry("Damage", "DAMAGE"),
            Map.entry("Strength", "STRENGTH"),
            Map.entry("Defense", "DEFENSE"),
            Map.entry("Health", "HEALTH"),
            Map.entry("Intelligence", "INTELLIGENCE"),
            Map.entry("Crit Damage", "CRITICAL_DAMAGE"),
            Map.entry("Crit Chance", "CRITICAL_CHANCE"),
            Map.entry("Speed", "WALK_SPEED"),
            Map.entry("Bonus Attack Speed", "ATTACK_SPEED"),
            Map.entry("Ferocity", "FEROCITY"),
            Map.entry("Magic Find", "MAGIC_FIND"),
            Map.entry("Pet Luck", "PET_LUCK"),
            Map.entry("True Defense", "TRUE_DEFENSE"),
            Map.entry("Sea Creature Chance", "SEA_CREATURE_CHANCE"),
            Map.entry("Mining Fortune", "MINING_FORTUNE"),
            Map.entry("Farming Fortune", "FARMING_FORTUNE"),
            Map.entry("Foraging Fortune", "FORAGING_FORTUNE"),
            Map.entry("Mining Speed", "MINING_SPEED"),
            Map.entry("Ability Damage", "ABILITY_DAMAGE")
    );

    /**
     * Stat labels that can appear in NEU lore lines (longest first for greedy matching).
     */
    private static final List<String> STAT_LABELS = List.copyOf(STAT_TO_API_KEY.keySet());

    private StarredItemBuilder() {
    }

    /**
     * Builds the input side — the item before applying this star.
     * For ★1 this is the unstarred base; for ★N (N≥2) this is ★(N-1).
     */
    public static ItemStack buildInput(NeuItem item, int star) {
        int prevStar = Math.max(0, star - 1);
        return buildStack(item, prevStar, null);
    }

    /**
     * Builds the output side — the item at {@code star} with stat deltas.
     */
    public static ItemStack buildOutput(NeuItem item, int star) {
        Map<String, Integer> after = resolveStats(item, star);
        if (after.isEmpty()) {
            return buildStack(item, star, null);
        }

        Map<String, Integer> before = resolveStats(item, star - 1);
        return buildStack(item, star, after, before);
    }

    // ── Stat resolution ─────────────────────────────────────────────────────

    /**
     * Resolves the full stat snapshot for {@code item} at {@code star}.
     * Returns an empty map if no stat data is available.
     */
    private static Map<String, Integer> resolveStats(NeuItem item, int star) {
        if (star <= 0) return Map.of();
        String itemId = item.internalName();

        // 1. Try Hypixel tiered_stats (authoritative)
        Map<String, int[]> tiered = HypixelItemsRegistry.getTieredStats(itemId);
        if (tiered != null && !tiered.isEmpty()) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (var entry : tiered.entrySet()) {
                String apiKey = entry.getKey();
                int[] values = entry.getValue();
                int idx = Math.min(star - 1, values.length - 1);
                String label = apiKeyToLabel(apiKey);
                result.put(label, values[idx]);
            }
            return result;
        }

        // 2. Try Hypixel base stats + 2%/star scaling
        Map<String, Integer> base = HypixelItemsRegistry.getBaseStats(itemId);
        if (base != null && !base.isEmpty()) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (var entry : base.entrySet()) {
                String apiKey = entry.getKey();
                int baseValue = entry.getValue();
                String label = apiKeyToLabel(apiKey);
                result.put(label, scale(baseValue, star));
            }
            return result;
        }

        // 3. Fall back to NEU lore parsing
        return parseBaseStatsFromLore(item, star);
    }

    private static String apiKeyToLabel(String apiKey) {
        return switch (apiKey) {
            case "DAMAGE" -> "Damage";
            case "STRENGTH" -> "Strength";
            case "DEFENSE" -> "Defense";
            case "HEALTH" -> "Health";
            case "INTELLIGENCE" -> "Intelligence";
            case "CRITICAL_DAMAGE" -> "Crit Damage";
            case "CRITICAL_CHANCE" -> "Crit Chance";
            case "WALK_SPEED" -> "Speed";
            case "ATTACK_SPEED" -> "Bonus Attack Speed";
            case "FEROCITY" -> "Ferocity";
            case "MAGIC_FIND" -> "Magic Find";
            case "PET_LUCK" -> "Pet Luck";
            case "TRUE_DEFENSE" -> "True Defense";
            case "SEA_CREATURE_CHANCE" -> "Sea Creature Chance";
            case "MINING_FORTUNE" -> "Mining Fortune";
            case "FARMING_FORTUNE" -> "Farming Fortune";
            case "FORAGING_FORTUNE" -> "Foraging Fortune";
            case "MINING_SPEED" -> "Mining Speed";
            case "ABILITY_DAMAGE" -> "Ability Damage";
            default -> apiKey;
        };
    }

    // ── Stack building ──────────────────────────────────────────────────────

    private static ItemStack buildStack(NeuItem item, int star,
                                        @Nullable Map<String, Integer> after) {
        return buildStack(item, star, after, null);
    }

    private static ItemStack buildStack(NeuItem item, int star,
                                        @Nullable Map<String, Integer> after,
                                        @Nullable Map<String, Integer> before) {
        ItemStack stack = ItemStackBuilder.build(item).copy();
        applyStarName(stack, item, star);

        if (after == null || after.isEmpty()) {
            return stack;
        }

        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing == null || existing.lines().isEmpty()) {
            return stack;
        }

        List<Component> updated = new ArrayList<>(existing.lines().size());
        for (Component line : existing.lines()) {
            updated.add(rewriteStatLine(line, after, before));
        }
        stack.set(DataComponents.LORE, new ItemLore(updated));
        return stack;
    }

    private static void applyStarName(ItemStack stack, NeuItem item, int star) {
        String base = item.displayName();
        if (base == null || base.isEmpty()) {
            base = item.internalName();
        }
        String stars = star > 0 ? " §6" + "✪".repeat(star) : "";
        stack.set(DataComponents.CUSTOM_NAME,
                LegacyStringParser.parse(base + stars));
    }

    // ── NEU lore fallback ───────────────────────────────────────────────────

    private static Map<String, Integer> parseBaseStatsFromLore(NeuItem item, int star) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> lore = item.lore();
        if (lore == null || lore.isEmpty()) {
            return result;
        }

        for (String raw : lore) {
            if (raw == null || raw.isEmpty()) continue;
            String clean = TextUtil.stripColorCodes(raw);
            int colonIdx = clean.indexOf(':');
            if (colonIdx <= 0) continue;

            String prefix = clean.substring(0, colonIdx).trim();
            for (String label : STAT_LABELS) {
                if (prefix.endsWith(label)) {
                    int value = extractLeadingInt(clean.substring(colonIdx + 1));
                    if (value != Integer.MIN_VALUE) {
                        result.put(label, scale(value, star));
                    }
                    break;
                }
            }
        }
        return result;
    }

    // ── Lore line rewriting ─────────────────────────────────────────────────

    private static Component rewriteStatLine(Component line,
                                             Map<String, Integer> after,
                                             @Nullable Map<String, Integer> before) {
        String raw = line.getString();

        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            String label = entry.getKey();
            int labelPos = raw.indexOf(label);
            if (labelPos < 0) continue;

            int afterLabel = labelPos + label.length();
            if (afterLabel >= raw.length() || raw.charAt(afterLabel) != ':') continue;

            int pos = afterLabel + 1;
            while (pos < raw.length()) {
                char c = raw.charAt(pos);
                if (c == ' ' || c == '+') {
                    pos++;
                } else if (c == '§' && pos + 1 < raw.length()) {
                    pos += 2;
                } else {
                    break;
                }
            }

            int numStart = pos;
            while (pos < raw.length() && raw.charAt(pos) >= '0' && raw.charAt(pos) <= '9') {
                pos++;
            }
            if (numStart == pos) continue;

            int numberEnd = pos;
            int percentEnd = pos;
            if (pos < raw.length() && raw.charAt(pos) == '%') {
                percentEnd = pos + 1;
            }

            int afterValue = entry.getValue();
            Integer beforeBoxed = before != null ? before.get(label) : null;
            return Component.literal(rebuildWithDelta(
                    raw, numStart, numberEnd, percentEnd, afterValue, beforeBoxed));
        }

        return line;
    }

    private static String rebuildWithDelta(String raw, int numStart, int numberEnd,
                                           int percentEnd, int afterValue,
                                           @Nullable Integer before) {
        String percent = raw.substring(numberEnd, percentEnd);
        String tail = raw.substring(percentEnd);

        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append(raw, 0, numStart).append(afterValue).append(percent);

        if (before != null && !before.equals(afterValue)) {
            int delta = afterValue - before;
            String sign = delta > 0 ? "+" : "";
            sb.append(' ').append(DELTA_BRACKETS).append('(')
                    .append(DELTA_COLOR).append(sign).append(delta)
                    .append(DELTA_BRACKETS).append(')');
        }
        sb.append(tail);
        return sb.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static int scale(int base, int star) {
        if (star <= 0) return base;
        double multiplier = 1.0 + PERCENT_PER_STAR * star;
        return (int) Math.round(base * multiplier);
    }

    private static int extractLeadingInt(String s) {
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
}
