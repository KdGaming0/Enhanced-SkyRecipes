package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import com.github.kdgaming0.skyrecipes.core.util.SkyRecipesExecutors;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.StackGroupManagerAccessor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup.SkyblockFamilyStackGroup;
import com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup.SkyblockStackGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memo for RRV's {@code StackGroupManager.getGroupItems(AbstractStackGroup)}, which walks the
 * entire item registry constructing a fresh {@code ItemStack} per item on every call — and is
 * invoked per matched group per keystroke ({@code appendMatchingGroups}) and per visible group
 * slot per frame ({@code ItemSlot}). Group membership only changes when stack groups reload or
 * the RRV recipe cache / stack sensitives are rebuilt, so results are cached until
 * {@link #invalidate()} is called from those points.
 *
 * <p>{@link #prewarm()} additionally computes every group's contents ahead of time so the first
 * keystroke never pays the registry sweep on the render thread: it walks the registry <em>once</em>
 * matching all groups per item (vs RRV's one full sweep per group), on a background worker.
 * Everything RRV-owned that the sweep touches is either snapshotted on the render thread first
 * (group list, stack sensitives) or verified read-only ({@code match()} implementations, the
 * frozen item registry); results are published back on the render thread, where a generation
 * check discards them if an invalidation happened mid-computation.</p>
 *
 * <p>The memo map itself is only ever touched on the render thread (every RRV caller and every
 * invalidation site runs there), so it needs no synchronization.</p>
 */
public final class StackGroupItemsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackGroupItemsCache.class);

    private static final Map<AbstractStackGroup, List<ItemStack>> groupItems = new IdentityHashMap<>();
    /** Bumped by every invalidation (render thread); read by in-flight prewarm publishes. */
    private static volatile int generation = 0;

    private StackGroupItemsCache() {
    }

    public static List<ItemStack> get(AbstractStackGroup group) {
        return groupItems.get(group);
    }

    public static void put(AbstractStackGroup group, List<ItemStack> items) {
        if (items != null) {
            groupItems.put(group, List.copyOf(items));
        }
    }

    public static void invalidate() {
        generation++;
        groupItems.clear();
    }

    /**
     * Precomputes all groups' contents off-thread and publishes them on the render thread.
     * Must be called on the render thread (snapshots RRV state). Results computed against a
     * generation that has since been invalidated are discarded; on any failure the memo simply
     * stays cold and groups fall back to lazy on-demand computation.
     */
    public static void prewarm() {
        if (!Configs.STACK_GROUPS.areStackGroupsEnabled()) return;
        List<AbstractStackGroup> groups = List.copyOf(StackGroupManager.stackGroups);
        if (groups.isEmpty()) return;

        int gen = generation;

        // Snapshot the stack sensitives on the render thread: the backing map is mutated
        // there (batched injection, RRV rebuilds) with no synchronization.
        Map<Item, List<ItemStack>> sensitives = new IdentityHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            List<ItemStack> stacks = ClientRecipeCache.INSTANCE.streamStackSensitives(item).toList();
            if (!stacks.isEmpty()) {
                sensitives.put(item, stacks);
            }
        }

        SkyRecipesExecutors.worker().execute(() -> {
            try {
                Map<AbstractStackGroup, List<ItemStack>> computed = computeAllGroups(groups, sensitives);
                Minecraft.getInstance().execute(() -> publish(gen, computed));
            } catch (Exception e) {
                LOGGER.debug("Stack group prewarm failed; groups will compute lazily", e);
            }
        });
    }

    /**
     * Single registry sweep matching every group per item. Per-group insertion order is
     * identical to RRV's {@code getGroupItems} (each item's matching stack sensitives, then
     * the plain stack); final ordering is normalized by {@code sortByGroupOrder} at publish.
     *
     * <p>Stacks carrying a SkyBlock ID resolve to their family group via one hash lookup
     * and never test the other groups — with 1000+ generated family groups, matching each
     * of ~9000 SkyBlock stacks against every group would turn this sweep quadratic. Plain
     * and foreign stacks only test the non-family groups, which is what RRV ships (~75).</p>
     */
    private static Map<AbstractStackGroup, List<ItemStack>> computeAllGroups(
            List<AbstractStackGroup> groups, Map<Item, List<ItemStack>> sensitives) {
        Map<AbstractStackGroup, List<ItemStack>> computed = new IdentityHashMap<>();
        List<AbstractStackGroup> foreignGroups = new ArrayList<>();
        for (AbstractStackGroup group : groups) {
            computed.put(group, new ArrayList<>());
            if (!(group instanceof SkyblockFamilyStackGroup)) {
                foreignGroups.add(group);
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack plain = new ItemStack(item);
            List<ItemStack> itemSensitives = sensitives.get(item);
            if (itemSensitives != null) {
                for (ItemStack sensitive : itemSensitives) {
                    String skyblockId = SkyblockIdExtractor.extract(sensitive);
                    if (skyblockId != null) {
                        SkyblockFamilyStackGroup family = SkyblockStackGroups.groupFor(skyblockId);
                        if (family != null) {
                            // Absent when the snapshot changed mid-computation; the
                            // generation check at publish discards the result anyway.
                            List<ItemStack> members = computed.get(family);
                            if (members != null) {
                                members.add(sensitive);
                            }
                        }
                        continue; // SkyBlock stacks never join vanilla groups
                    }
                    for (AbstractStackGroup group : foreignGroups) {
                        if (group.match(sensitive)) {
                            computed.get(group).add(sensitive);
                        }
                    }
                }
            }
            for (AbstractStackGroup group : foreignGroups) {
                if (group.match(plain)) {
                    computed.get(group).add(plain);
                }
            }
        }
        return computed;
    }

    /** Render thread: sort with RRV's own ordering (touches its lazy registry-order cache) and store. */
    private static void publish(int gen, Map<AbstractStackGroup, List<ItemStack>> computed) {
        if (generation != gen) return;
        for (Map.Entry<AbstractStackGroup, List<ItemStack>> entry : computed.entrySet()) {
            StackGroupManagerAccessor.skyrecipes$sortByGroupOrder(entry.getValue(), entry.getKey().getId());
            put(entry.getKey(), entry.getValue());
        }
        LOGGER.debug("Prewarmed {} stack groups", computed.size());
    }

    /**
     * Hashable stand-in for {@code ItemStack.isSameItemSameComponents} (verified against MC
     * 26.1.2 source: same {@code Item} + {@code Objects.equals} on the components maps, with
     * both-empty stacks equal regardless of components). {@code PatchedDataComponentMap} has
     * value-based equals/hashCode. Empty-vs-non-empty pairs never match here, which is at
     * worst slightly stricter than vanilla and only for empty stacks, which don't occur in
     * the item list.
     */
    public static Object dedupKey(ItemStack stack) {
        boolean empty = stack.isEmpty();
        return new DedupKey(stack.getItem(), empty, empty ? null : stack.getComponents());
    }

    private record DedupKey(Item item, boolean empty, DataComponentMap components) {
    }
}
