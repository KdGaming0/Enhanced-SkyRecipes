package com.github.kdgaming0.skyrecipes.core.recipe.generators;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.EssenceUpgradeData;
import com.github.kdgaming0.skyrecipes.core.recipe.util.SlotRefParser;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.render.item.StarredItemBuilder;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockEssenceUpgradeClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
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

    private EssenceUpgradeGenerator() {
    }

    private static ItemStack buildCoinStack(long amount) {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET, 1);
        String compact = RecipeUiHelper.formatCompactNumber(amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(compact + " Coins"));
        String exact = RecipeUiHelper.formatExact(amount);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§e" + exact + " Coins")
        )));
        return stack;
    }

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
            var neuItem = itemOpt.get();

            String essenceItemName = ESSENCE_ITEM_MAP.getOrDefault(data.essenceType(), "ESSENCE_" + data.essenceType().toUpperCase());
            ItemStack essenceStack = ItemStack.EMPTY;
            var essenceOpt = itemRegistry.getByInternalName(essenceItemName);
            if (essenceOpt.isPresent()) {
                essenceStack = ItemStackBuilder.build(essenceOpt.get());
            }

            var sortedStars = data.costsPerStar().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();

            for (Map.Entry<Integer, Integer> costEntry : sortedStars) {
                int starLevel = costEntry.getKey();
                int essenceAmount = costEntry.getValue();

                ItemStack inputStack = StarredItemBuilder.buildInput(neuItem, starLevel);
                ItemStack outputStack = StarredItemBuilder.buildOutput(neuItem, starLevel);

                long coinAmount = 0;
                List<ItemStack> displayExtras = new ArrayList<>();
                List<Long> extraAmounts = new ArrayList<>();
                List<String> extraReqs = data.extraItemsPerStar().get(starLevel);

                if (extraReqs != null) {
                    // First pass: find coin amount
                    for (String req : extraReqs) {
                        SlotRefParser.IngredientRef ref = SlotRefParser.parse(req);
                        if (ref == null) continue;
                        if ("SKYBLOCK_COIN".equals(ref.internalName())) {
                            coinAmount = ref.count();
                        }
                    }

                    // Coin first (count=1, no vanilla text)
                    if (coinAmount > 0) {
                        displayExtras.add(buildCoinStack(coinAmount));
                        extraAmounts.add(coinAmount);
                    }

                    // Other extras (count=1 if >=1000 to suppress vanilla text)
                    for (String req : extraReqs) {
                        SlotRefParser.IngredientRef ref = SlotRefParser.parse(req);
                        if (ref == null) continue;
                        if ("SKYBLOCK_COIN".equals(ref.internalName())) continue;

                        var reqItem = SlotRefParser.resolve(ref, itemRegistry);
                        if (reqItem != null) {
                            int displayCount = ref.count() >= 1000 ? 1 : ref.count();
                            displayExtras.add(ItemStackBuilder.build(reqItem, displayCount));
                            extraAmounts.add((long) ref.count());
                        }
                    }
                }

                // Essence stack: count=1 if >=1000 to suppress vanilla text,
                // otherwise use actual count so vanilla text shows correctly
                ItemStack displayEssence = essenceStack.copy();
                if (essenceAmount >= 1000) {
                    displayEssence.setCount(1);
                } else {
                    displayEssence.setCount(essenceAmount);
                }

                Identifier recipeId = IdentifierUtil.skyRecipeId("essence/",
                        itemName + "/" + starLevel);

                recipes.add(new SkyblockEssenceUpgradeClientRecipe(
                        recipeId,
                        inputStack,
                        outputStack,
                        displayEssence,
                        starLevel,
                        displayExtras,
                        extraAmounts,
                        neuItem.displayName(),
                        coinAmount,
                        essenceAmount
                ));
            }
        }

        LOGGER.info("Generated {} essence upgrade recipes", recipes.size());
        return recipes;
    }
}
