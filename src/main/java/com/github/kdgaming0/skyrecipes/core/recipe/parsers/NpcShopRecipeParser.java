package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.model.NeuRecipe;
import com.github.kdgaming0.skyrecipes.core.recipe.util.ParserUtil;
import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockNpcShopClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses NEU npc_shop recipes (items purchased from NPCs).
 */
public final class NpcShopRecipeParser {

    private NpcShopRecipeParser() {
    }

    /**
     * Parse an NPC shop recipe.
     */
    public static SkyblockNpcShopClientRecipe parse(NeuItem item, NeuRecipe.NpcShopRecipe recipe, ItemRegistry itemRegistry) {
        return ParserUtil.parseSafely(item.internalName(), "npc_shop", () -> {
            Identifier recipeId = IdentifierUtil.skyRecipeId("npc_shop/", item.internalName());

            // Resolve result item — recipe.result() may include a count suffix (e.g. "ROTTEN_FLESH:1")
            SlotRefParser.IngredientRef resultRef = SlotRefParser.parse(recipe.result());
            if (resultRef == null) {
                resultRef = new SlotRefParser.IngredientRef(recipe.result(), 1);
            }
            NeuItem resultItem = SlotRefParser.resolve(resultRef, itemRegistry);
            ItemStack resultStack = ParserUtil.buildOrBarrier(resultItem, resultRef.count());

            // Parse costs — NEU uses two formats:
            // 1. Array of strings: ["SKYBLOCK_COIN:8"]
            // 2. Array of objects: [{"item": "GLACITE:1", "cost": 3}]
            List<SkyblockNpcShopClientRecipe.ShopCost> costs = new ArrayList<>();
            for (NeuRecipe.NpcShopRecipe.Cost cost : recipe.costs()) {
                String itemName = cost.item();
                int amount = cost.cost();

                // The object format may embed a count in the item id ("GLACITE:1");
                // strip it so registry lookup works, treating it as a unit size.
                int colon = itemName.lastIndexOf(':');
                if (colon > 0) {
                    try {
                        int unitCount = Integer.parseInt(itemName.substring(colon + 1));
                        itemName = itemName.substring(0, colon);
                        amount = Math.max(1, amount) * Math.max(1, unitCount);
                    } catch (NumberFormatException ignored) {
                        // suffix is not a count; keep the full id
                    }
                }

                // Handle SKYBLOCK_COIN special case
                if ("SKYBLOCK_COIN".equals(itemName)) {
                    ItemStack coinStack = new ItemStack(Items.GOLD_INGOT, 1);
                    costs.add(new SkyblockNpcShopClientRecipe.ShopCost(
                            coinStack,
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
                    int displayCount = ref.count() >= 1000 ? 1 : ref.count();
                    ItemStack costStack = ParserUtil.buildOrBarrier(costItem, displayCount);
                    costs.add(new SkyblockNpcShopClientRecipe.ShopCost(costStack, itemName, amount, false));
                }
            }

            // NPC name fallback: NEU data has no 'npc' field, derive from parent item
            String npcDisplayName = item.displayName();
            if (npcDisplayName == null || npcDisplayName.isEmpty()) {
                npcDisplayName = item.internalName();
            }

            // Build NPC head stack for rendering
            ItemStack npcHead = ItemStackBuilder.build(item);

            // Resolve wiki URLs for the result item
            List<String> wikiUrls = List.of();
            String resultIdStr = recipe.result();
            if (resultIdStr != null && !resultIdStr.isEmpty()) {
                int colon = resultIdStr.indexOf(':');
                if (colon >= 0) {
                    resultIdStr = resultIdStr.substring(0, colon);
                }
                NeuItem resultWikiItem = itemRegistry.getByInternalName(resultIdStr).orElse(null);
                if (resultWikiItem != null && resultWikiItem.info() != null && !resultWikiItem.info().isEmpty()) {
                    wikiUrls = resultWikiItem.info();
                }
            }

            return new SkyblockNpcShopClientRecipe(
                    recipeId,
                    npcDisplayName,
                    item.internalName(),
                    costs,
                    resultStack,
                    wikiUrls,
                    npcHead
            );
        });
    }
}
