package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;
import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockKatUpgradeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU katgrade recipes (pet rarity upgrades at Kat NPC).
 */
public final class KatUpgradeRecipeParser {

    private KatUpgradeRecipeParser() {
    }

    /**
     * Parse a katgrade recipe.
     */
    public static SkyblockKatUpgradeClientRecipe parse(NeuItem item, NeuRecipe.KatGradeRecipe recipe, ItemRegistry itemRegistry) {
        return ParserUtil.parseSafely(item.internalName(), "katgrade", () -> {
            Identifier recipeId = IdentifierUtil.skyRecipeId("katgrade/", item.internalName());

            // Resolve input pet
            NeuItem inputItem = itemRegistry.getByInternalName(recipe.input()).orElse(null);
            ItemStack inputStack = inputItem != null ? ItemStackBuilder.build(inputItem) : new ItemStack(Items.BARRIER);

            // Resolve output pet
            NeuItem outputItem = itemRegistry.getByInternalName(recipe.output()).orElse(null);
            ItemStack outputStack = outputItem != null ? ItemStackBuilder.build(outputItem) : new ItemStack(Items.BARRIER);

            // Parse additional item requirements
            List<ItemStack> itemCosts = new ArrayList<>();
            for (String itemStr : recipe.items()) {
                SlotRefParser.IngredientRef ref = SlotRefParser.parse(itemStr);
                if (ref == null) continue;
                NeuItem costItem = SlotRefParser.resolve(ref, itemRegistry);
                if (costItem != null) {
                    itemCosts.add(ItemStackBuilder.build(costItem, ref.count()));
                }
            }

            return new SkyblockKatUpgradeClientRecipe(
                    recipeId,
                    inputItem,
                    inputStack,
                    outputItem,
                    outputStack,
                    recipe.coins(),
                    recipe.time(),
                    itemCosts
            );
        });
    }
}
