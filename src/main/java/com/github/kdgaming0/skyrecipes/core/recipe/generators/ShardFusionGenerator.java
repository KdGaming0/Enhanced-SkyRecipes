package com.github.kdgaming0.skyrecipes.core.recipe.generators;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionData;
import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionRegistry;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.recipe.builders.ShardInfoRecipeBuilder;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.util.IdentifierUtil;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.ShardFusionContext;
import com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockFusionClientRecipe;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates one {@link SkyblockFusionClientRecipe} per attribute shard that has
 * fusion recipes in the SkyShards dataset (~188 cards).
 *
 * <p>Joins fusion shard IDs to NEU items through {@code attribute_shards.json}:
 * primary key is the in-game shard ID ("E1"), with the bazaar product ID
 * ("SHARD_TERRA") as fallback. Pairs referencing unresolvable shards are dropped
 * per-pair; the rest of the card is kept.</p>
 */
public final class ShardFusionGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardFusionGenerator.class);

    private ShardFusionGenerator() {
    }

    public static List<ReliableClientRecipe> generateAll(ConstantsRegistry constantsRegistry,
                                                         ItemRegistry itemRegistry) {
        ShardFusionData data = ShardFusionRegistry.get();
        if (data == null) {
            LOGGER.info("Shard fusion data not loaded — skipping fusion recipes this cycle");
            return List.of();
        }

        // Join keys from NEU constants: shardId primary, bazaarName fallback.
        Map<String, AttributeShardData> byShardId = new HashMap<>();
        Map<String, AttributeShardData> byBazaarName = new HashMap<>();
        for (AttributeShardData shard : constantsRegistry.getAllAttributeShards().values()) {
            if (!shard.shardId().isEmpty()) byShardId.putIfAbsent(shard.shardId(), shard);
            if (!shard.bazaarName().isEmpty()) byBazaarName.putIfAbsent(shard.bazaarName(), shard);
        }

        int n = data.shardCount();
        String[] neuNames = new String[n];
        AttributeShardData[] resolvedShards = new AttributeShardData[n];
        int unresolved = 0;
        for (int i = 0; i < n; i++) {
            AttributeShardData shard = byShardId.get(data.shardId(i));
            if (shard == null) shard = byBazaarName.get(data.internalId(i));
            if (shard != null && itemRegistry.getByInternalName(shard.internalName()).isPresent()) {
                neuNames[i] = shard.internalName();
                resolvedShards[i] = shard;
            } else {
                unresolved++;
                LOGGER.debug("Fusion shard {} ({}) has no matching NEU item",
                        data.shardId(i), data.internalId(i));
            }
        }
        if (unresolved > 0) {
            LOGGER.info("Shard fusion join: {} of {} shards have no NEU item (their pairs are dropped)",
                    unresolved, n);
        }

        ShardFusionContext context = new ShardFusionContext(data, neuNames, itemRegistry);
        List<ReliableClientRecipe> recipes = new ArrayList<>(200);
        int droppedPairs = 0;

        for (int out = 0; out < n; out++) {
            int[] pairs = data.pairsFor(out);
            if (pairs == null || neuNames[out] == null) continue;

            // Filter to pairs whose inputs both resolved; count distinct inputs.
            boolean[] seen = new boolean[n];
            int[] filtered = new int[pairs.length];
            int count = 0;
            int distinctCount = 0;
            for (int packed : pairs) {
                int first = ShardFusionData.pairFirst(packed);
                int second = ShardFusionData.pairSecond(packed);
                if (neuNames[first] == null || neuNames[second] == null) {
                    droppedPairs++;
                    continue;
                }
                filtered[count++] = packed;
                if (!seen[first]) {
                    seen[first] = true;
                    distinctCount++;
                }
                if (!seen[second]) {
                    seen[second] = true;
                    distinctCount++;
                }
            }
            if (count == 0) continue;

            int[] distinctInputs = new int[distinctCount];
            int d = 0;
            for (int i = 0; i < n; i++) {
                if (seen[i]) distinctInputs[d++] = i;
            }

            NeuItem item = itemRegistry.getByInternalName(neuNames[out]).orElse(null);
            if (item == null) continue;
            // NEU shard items have no wiki links; fall back to the attribute's
            // section on the wiki, generated from the attribute name.
            List<String> wikiUrls = "WIKI_URL".equals(item.infoType())
                    && item.info() != null && !item.info().isEmpty()
                    ? item.info() : ShardInfoRecipeBuilder.attributeWikiUrls(resolvedShards[out]);
            String bazaarSearch = TextUtil.stripColorCodes(item.displayName()).trim();

            Identifier id = IdentifierUtil.skyRecipeId("fusion/", neuNames[out]);
            recipes.add(new SkyblockFusionClientRecipe(id, context, out,
                    count == pairs.length ? pairs : java.util.Arrays.copyOf(filtered, count),
                    distinctInputs, wikiUrls, bazaarSearch));
        }

        if (droppedPairs > 0) {
            LOGGER.debug("Dropped {} fusion pairs with unresolved input shards", droppedPairs);
        }
        LOGGER.info("Generated {} shard fusion recipes ({} total pairs)",
                recipes.size(), data.totalPairs());
        return recipes;
    }
}
