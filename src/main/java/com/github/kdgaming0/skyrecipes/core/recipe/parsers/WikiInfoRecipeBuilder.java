package com.github.kdgaming0.skyrecipes.core.recipe.parsers;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.render.ItemStackBuilder;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.builtin.info.InfoClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds RRV info recipes for items with WIKI_URL infoType.
 */
public final class WikiInfoRecipeBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiInfoRecipeBuilder.class);

    private WikiInfoRecipeBuilder() {}

    /**
     * Build wiki info recipes for an item.
     * Returns a list (usually 1) of InfoClientRecipe instances.
     */
    public static List<ReliableClientRecipe> build(NeuItem item) {
        List<ReliableClientRecipe> recipes = new ArrayList<>();
        if (item.info() == null || item.info().isEmpty()) {
            return recipes;
        }

        try {
            ItemStack stack = ItemStackBuilder.build(item);
            Identifier id = Identifier.fromNamespaceAndPath("skyrecipes", "wiki/" + item.internalName().toLowerCase());

            StringBuilder text = new StringBuilder();
            text.append("§eWiki Links:\n\n");
            for (String url : item.info()) {
                text.append("§b").append(url).append("\n");
            }

            recipes.add(new InfoClientRecipe(id, SlotContent.of(stack), Component.literal(text.toString())));

        } catch (Exception e) {
            LOGGER.warn("Failed to build wiki info for {}: {}", item.internalName(), e.getMessage());
        }

        return recipes;
    }
}
