package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.EssenceUpgradeData;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockEssenceUpgradeClientRecipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates essence upgrade recipes from constants data.
 * Each star level for each item produces one recipe.
 */
public final class EssenceUpgradeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EssenceUpgradeGenerator.class);

    private static final Map<String, String> ESSENCE_ITEM_MAP = Map.of(
        "Wither", "ESSENCE_WITHER",
        "Undead", "ESSENCE_UNDEAD",
        "Spider", "ESSENCE_SPIDER",
        "Dragon", "ESSENCE_DRAGON",
        "Diamond", "ESSENCE_DIAMOND",
        "Gold", "ESSENCE_GOLD",
        "Ice", "ESSENCE_ICE",
        "Crimson", "ESSENCE_CRIMSON"
    );

    private EssenceUpgradeGenerator() {}

    /**
     * Generate all essence upgrade recipes.
     */
    public static List<ReliableClientRecipe> generateAll(ConstantsRegistry constantsRegistry, ItemRegistry itemRegistry) {
        List<ReliableClientRecipe> recipes = new ArrayList<>();

        for (Map.Entry<String, EssenceUpgradeData> entry : constantsRegistry.getAllEssenceCosts().entrySet()) {
            String itemName = entry.getKey();
            EssenceUpgradeData data = entry.getValue();

            var itemOpt = itemRegistry.getByInternalName(itemName);
            if (itemOpt.isEmpty()) continue;

            ItemStack baseStack = ItemStackBuilder.build(itemOpt.get());
            String essenceItemName = ESSENCE_ITEM_MAP.getOrDefault(data.essenceType(), "ESSENCE_" + data.essenceType().toUpperCase());
            ItemStack essenceStack = ItemStack.EMPTY;
            var essenceOpt = itemRegistry.getByInternalName(essenceItemName);
            if (essenceOpt.isPresent()) {
                essenceStack = ItemStackBuilder.build(essenceOpt.get());
            }

            for (Map.Entry<Integer, Integer> costEntry : data.costsPerStar().entrySet()) {
                int starLevel = costEntry.getKey();
                int essenceAmount = costEntry.getValue();

                List<ItemStack> extraItems = new ArrayList<>();
                List<String> extraReqs = data.extraItemsPerStar().get(starLevel);
                if (extraReqs != null) {
                    for (String req : extraReqs) {
                        SlotRefParser.IngredientRef ref = SlotRefParser.parse(req);
                        if (ref == null) continue;
                        var reqItem = SlotRefParser.resolve(ref, itemRegistry);
                        if (reqItem != null) {
                            extraItems.add(ItemStackBuilder.build(reqItem, ref.count()));
                        }
                    }
                }

                Identifier recipeId = Identifier.fromNamespaceAndPath("skyrecipes",
                    "essence/" + itemName.toLowerCase() + "/" + starLevel);

                recipes.add(new SkyblockEssenceUpgradeClientRecipe(
                    recipeId,
                    baseStack,
                    essenceStack.copyWithCount(essenceAmount),
                    starLevel,
                    data.essenceType(),
                    extraItems
                ));
            }
        }

        LOGGER.info("Generated {} essence upgrade recipes", recipes.size());
        return recipes;
    }
}
