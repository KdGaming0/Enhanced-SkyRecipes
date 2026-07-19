package com.github.kdgaming0.skyrecipes.core.recipe.util;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
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
     * Builds a display stack for a resolved item, or a barrier placeholder when
     * the item is unknown, so recipe layouts stay intact.
     *
     * @param item  the resolved NeuItem, or {@code null} when unknown
     * @param count stack count for both the real stack and the placeholder
     * @return the built stack, never {@code null}
     */
    public static ItemStack buildOrBarrier(@Nullable NeuItem item, int count) {
        return item != null
                ? ItemStackBuilder.build(item, count)
                : new ItemStack(Items.BARRIER, count);
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
