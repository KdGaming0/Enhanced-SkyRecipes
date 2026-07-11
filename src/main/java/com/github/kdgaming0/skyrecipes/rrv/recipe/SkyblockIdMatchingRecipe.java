package com.github.kdgaming0.skyrecipes.rrv.recipe;

/**
 * A client recipe that applies to a dynamic set of SkyBlock items beyond the
 * sample stacks it exposes through {@code getResults()}/{@code getIngredients()}.
 *
 * <p>{@link SkyblockRecipeCache} indexes recipes by the SkyBlock IDs of their
 * exposed stacks. Recipes whose applicability spans many items sharing one
 * vanilla base item (e.g. every reforge) would need one stack per item to be
 * findable that way. Implementing this interface instead lets the cache match
 * them by ID at lookup time, so every applicable item resolves the recipe.</p>
 */
public interface SkyblockIdMatchingRecipe {

    /**
     * @param skyblockId a non-null {@code ExtraAttributes.id}
     * @return whether this recipe applies to the item with the given ID
     */
    boolean matchesSkyblockId(String skyblockId);
}
