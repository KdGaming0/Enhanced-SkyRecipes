package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;
import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIslandNames;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockDropsClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU drops recipes (mob/boss drop sources).
 */
public final class DropsRecipeParser {

    private DropsRecipeParser() {
    }

    /**
     * Parse a drops recipe.
     */
    public static SkyblockDropsClientRecipe parse(NeuItem item, NeuRecipe.DropsRecipe recipe, ItemRegistry itemRegistry) {
        return ParserUtil.parseSafely(item.internalName(), "drops", () -> {
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

            // The source item (the mob/boss itself) so RRV's result index resolves a click
            // on the source to this recipe. Built in its own guard: build() can throw, and a
            // throw here must not null the whole recipe (which would also break drop-clicks).
            // On failure the source simply isn't clickable; the recipe is otherwise intact.
            ItemStack sourceStack;
            try {
                sourceStack = ItemStackBuilder.build(item);
            } catch (Exception e) {
                sourceStack = ItemStack.EMPTY;
            }

            return new SkyblockDropsClientRecipe(recipeId, name, render, sourceStack, drops, chances,
                    recipe.level(), recipe.xp(), recipe.combatXp(), recipe.coins(),
                    SkyblockIslandNames.displayName(recipe.panorama()), recipe.extra(),
                    findLoreValue(item.lore(), "Health"), findLoreValue(item.lore(), "Damage"),
                    item.info());
        });
    }

    private static String findLoreValue(List<String> lore, String label) {
        for (String rawLine : lore) {
            String line = TextUtil.stripColorCodes(rawLine);
            int colon = line.indexOf(':');
            String heading = colon >= 0 ? line.substring(0, colon).trim() : "";
            if (heading.toLowerCase().endsWith(label.toLowerCase()) && colon + 1 < line.length()) {
                return line.substring(colon + 1).trim();
            }
        }
        return "";
    }
}
