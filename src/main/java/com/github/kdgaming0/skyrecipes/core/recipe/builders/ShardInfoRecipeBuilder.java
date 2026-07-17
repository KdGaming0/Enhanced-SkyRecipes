package com.github.kdgaming0.skyrecipes.core.recipe.builders;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockInfoClientRecipe;
import com.github.kdgaming0.skyrecipes.rrv.recipe.util.RecipeUiHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds SkyBlock info recipe cards for attribute shard items.
 *
 * <p>Shards have no recipe or drop data in the NEU repo, but
 * constants/attribute_shards.json carries ability, rarity, alignment,
 * family, and bazaar metadata — shown here so every shard opens a card.
 * All shards are bazaar-tradeable, so the card gets a Bazaar button.</p>
 */
public final class ShardInfoRecipeBuilder {

    private static final String ATTRIBUTE_WIKI_BASE = "https://hypixelskyblock.minecraft.wiki/w/Attributes#";

    private ShardInfoRecipeBuilder() {
    }

    /**
     * Wiki URL for a shard's attribute section, generated from the attribute
     * name (e.g. "Strong Arms" → {@code .../w/Attributes#Strong_Arms}) — NEU
     * shard items carry no wiki links of their own.
     *
     * @return a single-element list, or an empty list when no attribute name is known
     */
    public static List<String> attributeWikiUrls(AttributeShardData shard) {
        if (shard == null || shard.abilityName().isEmpty()) {
            return List.of();
        }
        return List.of(ATTRIBUTE_WIKI_BASE + shard.abilityName().trim().replace(' ', '_'));
    }

    /**
     * Extracts the attribute's effect text from the shard item's lore: the
     * lines after the attribute title (line 0) up to the first blank line,
     * joined so the info card can re-wrap them to its own width.
     */
    private static String extractEffect(List<String> lore) {
        if (lore == null || lore.size() < 2) {
            return "";
        }
        StringBuilder effect = new StringBuilder();
        for (int i = 1; i < lore.size(); i++) {
            String line = lore.get(i);
            if (TextUtil.stripColorCodes(line).isBlank()) {
                break;
            }
            if (!effect.isEmpty()) {
                effect.append(' ');
            }
            effect.append(line.trim());
        }
        return effect.toString();
    }

    /**
     * Build the info recipe for a shard item.
     *
     * @return the info recipe, or {@code null} if building failed
     */
    public static ReliableClientRecipe build(NeuItem item, AttributeShardData shard) {
        try {
            Identifier id = IdentifierUtil.skyRecipeId("shard_info/", item.internalName());

            List<Component> lines = new ArrayList<>(6);
            if (!shard.abilityName().isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Attribute:", shard.abilityName()));
            }
            String rarityLine = shard.rarity();
            if (!shard.alignment().isEmpty()) {
                rarityLine = rarityLine.isEmpty() ? shard.alignment() : rarityLine + " · " + shard.alignment();
            }
            if (!rarityLine.isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Rarity:", rarityLine));
            }
            if (!shard.shardId().isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Shard ID:", shard.shardId()));
            }
            if (!shard.family().isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Family:", String.join(", ", shard.family())));
            }
            // Last, so a long effect can only ever truncate itself — never the
            // short metadata lines above (the card shows the full text on hover).
            String effect = extractEffect(item.lore());
            if (!effect.isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Effect:", effect));
            }

            String bazaarSearch = TextUtil.stripColorCodes(item.displayName()).trim();

            List<String> wikiUrls = item.info() != null && !item.info().isEmpty()
                    ? item.info() : attributeWikiUrls(shard);

            return new SkyblockInfoClientRecipe(
                    id,
                    item,
                    item.displayName(),
                    lines,
                    wikiUrls,
                    false,
                    "",
                    bazaarSearch
            );
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ShardInfoRecipeBuilder.class)
                    .warn("Failed to build shard info for {}", item.internalName(), e);
            return null;
        }
    }
}
