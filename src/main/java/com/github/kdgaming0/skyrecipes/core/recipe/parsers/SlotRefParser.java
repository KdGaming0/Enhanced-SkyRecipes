package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses NEU ingredient references in the format {@code ITEM_INTERNAL_NAME:COUNT}.
 */
public final class SlotRefParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlotRefParser.class);

    private SlotRefParser() {}

    /**
     * Parsed ingredient reference.
     *
     * @param internalName The NEU internal name of the ingredient
     * @param count        The quantity required
     */
    public record IngredientRef(String internalName, int count) {}

    /**
     * Parse an ingredient string like "ENCHANTED_EYE_OF_ENDER:16" or "STICK".
     *
     * @param raw The raw ingredient string
     * @return Parsed reference, or null if empty/invalid
     */
    public static IngredientRef parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        int colonIndex = raw.lastIndexOf(':');
        if (colonIndex == -1) {
            return new IngredientRef(raw, 1);
        }

        String name = raw.substring(0, colonIndex);
        String countStr = raw.substring(colonIndex + 1);
        int count;
        try {
            count = (int) Double.parseDouble(countStr);
        } catch (NumberFormatException e) {
            count = 1;
        }

        return new IngredientRef(name, count);
    }

    /**
     * Resolve an ingredient reference to a {@link NeuItem} from the registry.
     *
     * @param ref      The parsed reference
     * @param registry The item registry
     * @return The NeuItem, or null if not found
     */
    public static NeuItem resolve(IngredientRef ref, ItemRegistry registry) {
        if (ref == null || registry == null) {
            return null;
        }
        return registry.getByInternalName(ref.internalName()).orElse(null);
    }
}
