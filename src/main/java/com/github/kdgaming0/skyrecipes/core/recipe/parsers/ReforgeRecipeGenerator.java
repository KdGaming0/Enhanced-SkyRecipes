package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.ReforgeStoneData;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.rrv.recipe.SkyblockReforgeClientRecipe;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates reforge recipes from NEU constants.
 * Each reforge stone produces one recipe showing the stone and applicable item types.
 */
public final class ReforgeRecipeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReforgeRecipeGenerator.class);

    private ReforgeRecipeGenerator() {}

    /**
     * Generate all reforge recipes.
     */
    public static List<ReliableClientRecipe> generateAll(ConstantsRegistry constantsRegistry, ItemRegistry itemRegistry) {
        List<ReliableClientRecipe> recipes = new ArrayList<>();

        for (Map.Entry<String, ReforgeStoneData> entry : constantsRegistry.getAllReforgeStones().entrySet()) {
            String stoneName = entry.getKey();
            ReforgeStoneData data = entry.getValue();

            var stoneOpt = itemRegistry.getByInternalName(data.internalName());
            if (stoneOpt.isEmpty()) {
                stoneOpt = itemRegistry.getByInternalName(stoneName);
            }
            ItemStack stoneStack = stoneOpt.isPresent()
                ? ItemStackBuilder.build(stoneOpt.get())
                : ItemStack.EMPTY;

            // Build a sample item stack for the applicable type (use a generic representative)
            ItemStack sampleItem = findSampleItem(data.itemTypes(), itemRegistry);

            Identifier recipeId = IdentifierUtil.skyRecipeId("reforge/", stoneName);

            recipes.add(new SkyblockReforgeClientRecipe(
                recipeId,
                stoneStack,
                sampleItem,
                data.reforgeName(),
                data.itemTypes(),
                data.reforgeCosts()
            ));
        }

        LOGGER.info("Generated {} reforge recipes", recipes.size());
        return recipes;
    }

    private static ItemStack findSampleItem(String itemTypes, ItemRegistry itemRegistry) {
        // Try to find a representative item for the type
        String[] types = itemTypes.split(",");
        for (String type : types) {
            String sampleName = switch (type.trim().toUpperCase()) {
                case "SWORD" -> "DIAMOND_SWORD";
                case "BOW" -> "BOW";
                case "HELMET" -> "DIAMOND_HELMET";
                case "CHESTPLATE" -> "DIAMOND_CHESTPLATE";
                case "LEGGINGS" -> "DIAMOND_LEGGINGS";
                case "BOOTS" -> "DIAMOND_BOOTS";
                case "PICKAXE" -> "DIAMOND_PICKAXE";
                case "AXE" -> "DIAMOND_AXE";
                case "HOE" -> "DIAMOND_HOE";
                case "SHOVEL" -> "DIAMOND_SHOVEL";
                case "FISHING_ROD" -> "FISHING_ROD";
                case "ACCESSORY" -> "POTION"; // Placeholder
                case "EQUIPMENT" -> "LEATHER_HELMET";
                default -> null;
            };
            if (sampleName != null) {
                var opt = itemRegistry.getByInternalName(sampleName);
                if (opt.isPresent()) {
                    return ItemStackBuilder.build(opt.get());
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
