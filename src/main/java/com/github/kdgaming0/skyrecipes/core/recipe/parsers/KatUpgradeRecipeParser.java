package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockKatUpgradeClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU katgrade recipes (pet rarity upgrades at Kat NPC).
 */
public final class KatUpgradeRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(KatUpgradeRecipeParser.class);

    private KatUpgradeRecipeParser() {}

    /**
     * Parse a katgrade recipe.
     */
    public static SkyblockKatUpgradeClientRecipe parse(NeuItem item, NeuRecipe.KatGradeRecipe recipe, ItemRegistry itemRegistry) {
        try {
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
                inputStack,
                outputStack,
                recipe.coins(),
                recipe.time(),
                itemCosts
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse katgrade recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
