package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockDropsClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU drops recipes (mob/boss drop sources).
 */
public final class DropsRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(DropsRecipeParser.class);

    private DropsRecipeParser() {
    }

    /**
     * Parse a drops recipe.
     */
    public static SkyblockDropsClientRecipe parse(NeuItem item, NeuRecipe.DropsRecipe recipe, ItemRegistry itemRegistry) {
        try {
            Identifier recipeId = IdentifierUtil.skyRecipeId("drops/", item.internalName());

            List<SkyblockDropsClientRecipe.DropEntry> drops = new ArrayList<>();
            String[] chances = new String[recipe.drops().size()];
            int idx = 0;
            for (NeuRecipe.DropsRecipe.Drop drop : recipe.drops()) {
                SlotRefParser.IngredientRef ref = SlotRefParser.parse(drop.id());
                if (ref == null) continue;

                NeuItem dropItem = SlotRefParser.resolve(ref, itemRegistry);
                ItemStack stack = dropItem != null
                        ? ItemStackBuilder.build(dropItem)
                        : ItemStack.EMPTY;

                drops.add(new SkyblockDropsClientRecipe.DropEntry(stack, drop.id(), drop.chance()));
                chances[idx++] = drop.chance();
            }

            String name = recipe.name() != null ? recipe.name() : "";
            String render = recipe.render() != null ? recipe.render() : "";

            return new SkyblockDropsClientRecipe(recipeId, name, render, drops, chances, item.info());

        } catch (Exception e) {
            LOGGER.warn("Failed to parse drops recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
