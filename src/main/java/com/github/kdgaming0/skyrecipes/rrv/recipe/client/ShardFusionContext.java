package com.github.kdgaming0.skyrecipes.rrv.recipe.client;

import com.github.kdgaming0.skyrecipes.core.fusion.ShardFusionData;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Per-generation-cycle shared state for all shard fusion recipe cards.
 *
 * <p>Holds the fusion snapshot, the shard-index → NEU internal name join, and a
 * lazily filled {@link ItemStack} cache shared by all 188 cards, so no stack is
 * ever built eagerly at generation time and each shard's stack is built at most
 * once per cycle. Lazy fields use the same benign-race pattern as
 * {@link SkyblockInfoClientRecipe}: concurrent callers may at worst build a
 * stack twice, which is harmless.</p>
 */
public final class ShardFusionContext {

    private final ShardFusionData data;
    private final String[] neuInternalNames;
    private final ItemRegistry itemRegistry;
    private final ItemStack[] stacks;
    private final String[] displayNames;

    public ShardFusionContext(ShardFusionData data, String[] neuInternalNames,
                              ItemRegistry itemRegistry) {
        this.data = data;
        this.neuInternalNames = neuInternalNames;
        this.itemRegistry = itemRegistry;
        this.stacks = new ItemStack[data.shardCount()];
        this.displayNames = new String[data.shardCount()];
    }

    public ShardFusionData data() {
        return data;
    }

    /** NEU internal name for a shard index, or {@code null} if unresolved. */
    @Nullable
    public String internalName(int index) {
        return neuInternalNames[index];
    }

    /** Copies of this shard consumed when used as a fusion input. */
    public int fuseAmount(int index) {
        return data.fuseAmount(index);
    }

    /** Lazily built display stack for a shard index; {@link ItemStack#EMPTY} if unresolvable. */
    public ItemStack stack(int index) {
        ItemStack stack = stacks[index];
        if (stack == null) {
            stack = buildStack(index);
            stacks[index] = stack;
        }
        return stack;
    }

    /** Colour-coded display name for a shard index (falls back to the in-game shard ID). */
    public String displayName(int index) {
        String name = displayNames[index];
        if (name == null) {
            name = resolveItem(index).map(NeuItem::displayName)
                    .orElse(data.shardId(index));
            displayNames[index] = name;
        }
        return name;
    }

    private ItemStack buildStack(int index) {
        return resolveItem(index)
                .map(item -> ItemStackBuilder.build(item, 1))
                .orElse(ItemStack.EMPTY);
    }

    private java.util.Optional<NeuItem> resolveItem(int index) {
        String internalName = neuInternalNames[index];
        if (internalName == null) return java.util.Optional.empty();
        return itemRegistry.getByInternalName(internalName);
    }
}
