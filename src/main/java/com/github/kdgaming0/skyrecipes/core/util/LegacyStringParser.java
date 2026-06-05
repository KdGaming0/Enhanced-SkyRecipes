package com.github.kdgaming0.skyrecipes.core.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Parses Minecraft legacy formatting codes (§) into modern {@link Component} objects.
 */
public final class LegacyStringParser {

    private LegacyStringParser() {
    }

    /**
     * Parses a string containing § color/formatting codes into a {@link Component}.
     *
     * @param text Raw text with § codes (e.g. "§9Aspect of the End")
     * @return A Component with proper styling
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                // Flush accumulated text with current style
                if (!currentText.isEmpty()) {
                    result.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                    currentText.setLength(0);
                }

                char code = text.charAt(i + 1);
                ChatFormatting formatting = ChatFormatting.getByCode(code);

                if (formatting == ChatFormatting.RESET) {
                    currentStyle = Style.EMPTY;
                } else if (formatting != null) {
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
            result.append(Component.literal(currentText.toString()).withStyle(currentStyle));
        }

        return result;
    }
}
