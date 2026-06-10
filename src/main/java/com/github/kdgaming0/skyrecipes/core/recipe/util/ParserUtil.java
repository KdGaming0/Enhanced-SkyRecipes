package com.github.kdgaming0.skyrecipes.core.recipe.util;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared parsing utilities that eliminate repetitive try/catch/LOGGER
 * boilerplate across SkyRecipes recipe parsers.
 */
public final class ParserUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserUtil.class);

    private ParserUtil() {
    }

    /**
     * Executes a parser lambda inside a standard safety wrapper.
     * If the parser throws, the exception is logged at WARN (with full stack trace)
     * and {@code null} is returned.
     *
     * @param internalName the item being parsed (for logging context)
     * @param recipeType   human-readable recipe type (e.g. "crafting", "forge")
     * @param parser       the actual parsing logic
     * @param <T>          the recipe type being produced
     * @return the parsed recipe, or {@code null} on failure
     */
    public static <T> T parseSafely(String internalName, String recipeType, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse {} recipe for {}", recipeType, internalName, e);
            return null;
        }
    }

    /**
     * Resolves the output item for a recipe that may specify an
     * {@code overrideOutputId}. Falls back to the parent item when the override
     * is missing or unknown.
     *
     * @param defaultItem      the parent NeuItem
     * @param overrideOutputId optional output ID from the recipe data
     * @param registry         item registry for lookups
     * @return the resolved output item
     */
    public static NeuItem resolveOutputItem(NeuItem defaultItem, String overrideOutputId, ItemRegistry registry) {
        if (overrideOutputId != null && !overrideOutputId.isEmpty()) {
            return registry.getByInternalName(overrideOutputId).orElse(defaultItem);
        }
        return defaultItem;
    }

    /**
     * Returns the wiki URL list for an item, or an empty list if none.
     *
     * @param item the NEU item definition
     * @return list of wiki URLs, never {@code null}
     */
    public static List<String> wikiUrlsForItem(NeuItem item) {
        return ("WIKI_URL".equals(item.infoType()) && item.info() != null)
                ? item.info()
                : List.of();
    }
}
