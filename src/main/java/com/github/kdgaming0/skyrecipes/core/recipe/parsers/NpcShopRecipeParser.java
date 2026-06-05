package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockNpcShopClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU npc_shop recipes (items purchased from NPCs).
 */
public final class NpcShopRecipeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcShopRecipeParser.class);

    private NpcShopRecipeParser() {
    }

    /**
     * Parse an NPC shop recipe.
     */
    public static SkyblockNpcShopClientRecipe parse(NeuItem item, NeuRecipe.NpcShopRecipe recipe, ItemRegistry itemRegistry) {
        try {
            Identifier recipeId = IdentifierUtil.skyRecipeId("npc_shop/", item.internalName());

            // Resolve result item
            NeuItem resultItem = itemRegistry.getByInternalName(recipe.result()).orElse(item);
            ItemStack resultStack = ItemStackBuilder.build(resultItem);

            // Parse costs — NEU uses two formats:
            // 1. Array of strings: ["SKYBLOCK_COIN:8"]
            // 2. Array of objects: [{"item": "GLACITE:1", "cost": 3}]
            List<SkyblockNpcShopClientRecipe.ShopCost> costs = new ArrayList<>();
            for (NeuRecipe.NpcShopRecipe.Cost cost : recipe.costs()) {
                String itemName = cost.item();
                int amount = cost.cost();

                // Handle SKYBLOCK_COIN special case
                if ("SKYBLOCK_COIN".equals(itemName)) {
                    costs.add(new SkyblockNpcShopClientRecipe.ShopCost(
                            new ItemStack(Items.GOLD_INGOT, amount),
                            itemName,
                            amount,
                            true
                    ));
                } else {
                    SlotRefParser.IngredientRef ref = SlotRefParser.parse(itemName + ":" + amount);
                    if (ref == null) {
                        ref = new SlotRefParser.IngredientRef(itemName, amount);
                    }
                    NeuItem costItem = SlotRefParser.resolve(ref, itemRegistry);
                    ItemStack costStack = costItem != null
                            ? ItemStackBuilder.build(costItem, ref.count())
                            : new ItemStack(Items.BARRIER, amount);
                    costs.add(new SkyblockNpcShopClientRecipe.ShopCost(costStack, itemName, amount, false));
                }
            }

            return new SkyblockNpcShopClientRecipe(
                    recipeId,
                    recipe.npc(),
                    costs,
                    resultStack
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to parse npc_shop recipe for {}: {}", item.internalName(), e.getMessage());
            return null;
        }
    }
}
