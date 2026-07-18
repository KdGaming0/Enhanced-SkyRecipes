package com.github.kdgaming0.skyrecipes.core.util;

import net.minecraft.resources.Identifier;

/**
 * Utilities for creating Minecraft {@link Identifier}s from NEU internal names.
 *
 * <p>NEU internal names may contain characters that are illegal in Identifier paths
 * (e.g. {@code ;} in pet tiers like {@code MAGMA_CUBE;1}). This utility sanitises
 * those names so they can be used as recipe IDs.</p>
 */
public final class IdentifierUtil {

    private IdentifierUtil() {
    }

    /**
     * Sanitise a string so it is valid for use as an {@link Identifier} path.
     *
     * <p>Valid characters are {@code [a-z0-9/._-]}. Everything else is replaced
     * with {@code _}.</p>
     */
    public static String sanitizePath(String input) {
        if (input == null || input.isEmpty()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    /**
     * Create a {@code skyrecipes:} identifier with a sanitised path.
     *
     * @param prefix path prefix, e.g. {@code "crafting/"} or {@code "forge/"}
     * @param name   the NEU internal name to sanitise and append
     * @return a valid Identifier
     */
    public static Identifier skyRecipeId(String prefix, String name) {
        return Identifier.fromNamespaceAndPath("skyrecipes", prefix + sanitizePath(name));
    }

    /**
     * Create a {@code skyrecipes:}-namespaced identifier for an already-valid path
     * (e.g. a bundled texture path). No sanitisation is applied.
     *
     * @param path a path that is already valid for an {@link Identifier}
     * @return a {@code skyrecipes:path} identifier
     */
    public static Identifier skyRecipes(String path) {
        return Identifier.fromNamespaceAndPath("skyrecipes", path);
    }
}
