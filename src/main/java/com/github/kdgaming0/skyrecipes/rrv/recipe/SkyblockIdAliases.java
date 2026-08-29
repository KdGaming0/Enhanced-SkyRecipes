package com.github.kdgaming0.skyrecipes.rrv.recipe;

import java.util.Collection;
import java.util.List;

/** Extra SkyBlock IDs under which a recipe should be discoverable. */
public interface SkyblockIdAliases {
    default Collection<String> ingredientAliases() {
        return List.of();
    }

    default Collection<String> resultAliases() {
        return List.of();
    }
}
