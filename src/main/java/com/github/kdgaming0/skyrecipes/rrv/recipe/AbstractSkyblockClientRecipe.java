package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import net.minecraft.resources.Identifier;

/**
 * Base class for all SkyRecipes custom client recipes.
 *
 * <p>Eliminates boilerplate for {@code id}, {@code getId()}, and {@code isVisualOnly()}
 * that is identical across every SkyBlock recipe type.</p>
 */
public abstract class AbstractSkyblockClientRecipe implements ReliableClientRecipe {

    protected final Identifier id;

    protected AbstractSkyblockClientRecipe(Identifier id) {
        this.id = id;
    }

    @Override
    public final Identifier getId() {
        return id;
    }

    @Override
    public boolean isVisualOnly() {
        return true;
    }
}
