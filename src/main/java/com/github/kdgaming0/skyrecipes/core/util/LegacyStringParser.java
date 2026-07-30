package com.github.kdgaming0.skyrecipes.core.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
//? if >= 26.2
//import net.minecraft.network.chat.TextColor;

/**
 * Parses Minecraft legacy formatting codes (§) into modern {@link Component} objects.
 */
public final class LegacyStringParser {

    private LegacyStringParser() {
    }

    /**
     * Parses a string containing § color/formatting codes into a {@link Component}.
     *
     * <p>A string with a single style run — which is most NEU display names and lore lines —
     * returns that styled literal directly rather than an empty parent holding one sibling.
     * The wrapper renders identically but costs an extra {@code MutableComponent} plus its
     * sibling {@code ArrayList} per line, and these components are retained for the session
     * inside ~8.5k item stacks (roughly 90k redundant objects). It also deepens every
     * tooltip walk and component hash.</p>
     *
     * @param text Raw text with § codes (e.g. "§9Aspect of the End")
     * @return A Component with proper styling
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // `first` holds the sole segment until a second one appears; only then is the
        // parent allocated and both spliced into it.
        MutableComponent first = null;
        MutableComponent result = null;
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                // Flush accumulated text with current style
                if (!currentText.isEmpty()) {
                    MutableComponent segment =
                            Component.literal(currentText.toString()).withStyle(currentStyle);
                    currentText.setLength(0);
                    if (first == null) {
                        first = segment;
                    } else {
                        if (result == null) {
                            result = Component.empty().append(first);
                        }
                        result.append(segment);
                    }
                }

                char code = text.charAt(i + 1);
                ChatFormatting formatting = ChatFormatting.getByCode(code);

                if (formatting == ChatFormatting.RESET) {
                    currentStyle = Style.EMPTY;
                } else if (formatting != null) {
                    //$ legacy_color_check
                    if (formatting.isColor()) {
                        // Color resets previous color but keeps formatting
                        currentStyle = currentStyle
                                .withColor(formatting)
                                .withBold(currentStyle.isBold())
                                .withItalic(currentStyle.isItalic())
                                .withUnderlined(currentStyle.isUnderlined())
                                .withStrikethrough(currentStyle.isStrikethrough())
                                .withObfuscated(currentStyle.isObfuscated());
                    } else {
                        // Formatting modifier
                        currentStyle = currentStyle.applyFormat(formatting);
                    }
                }

                i++; // Skip the code character
            } else {
                currentText.append(c);
            }
        }

        // Flush remaining text
        if (!currentText.isEmpty()) {
            MutableComponent segment =
                    Component.literal(currentText.toString()).withStyle(currentStyle);
            if (first == null) {
                first = segment;
            } else {
                if (result == null) {
                    result = Component.empty().append(first);
                }
                result.append(segment);
            }
        }

        if (result != null) {
            return result;
        }
        // Single segment, or none at all (input was only formatting codes).
        return first != null ? first : Component.empty();
    }
}
