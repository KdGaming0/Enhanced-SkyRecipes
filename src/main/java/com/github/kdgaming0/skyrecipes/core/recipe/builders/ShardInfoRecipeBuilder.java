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

    private ShardInfoRecipeBuilder() {
    }

    /**
     * Build the info recipe for a shard item.
     *
     * @return the info recipe, or {@code null} if building failed
     */
    public static ReliableClientRecipe build(NeuItem item, AttributeShardData shard) {
        try {
            Identifier id = IdentifierUtil.skyRecipeId("shard_info/", item.internalName());

            List<Component> lines = new ArrayList<>(4);
            if (!shard.abilityName().isEmpty()) {
                lines.add(RecipeUiHelper.labeledLine("Ability:", shard.abilityName()));
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

            String bazaarSearch = TextUtil.stripColorCodes(item.displayName()).trim();

            return new SkyblockInfoClientRecipe(
                    id,
                    item,
                    item.displayName(),
                    lines,
                    item.info(),
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
