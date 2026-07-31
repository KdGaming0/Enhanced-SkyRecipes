package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.config.Configs;
import com.github.kdgaming0.skyrecipes.mixin.recipe.ClientRecipeCacheAccessor;
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
import java.util.Objects;

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

    /**
     * Monotonic stamp bumped by every {@link #invalidate()}.
     *
     * <p>Exposed so per-instance memos further down the render path — currently
     * {@code ItemSlotGroupCacheMixin}, which caches a slot's resolved group and member list
     * for the slot's lifetime — can be invalidated by the same eight call sites that drop
     * this cache, instead of each having to hook them separately. A consumer that stores the
     * stamp alongside its value and re-resolves on mismatch can only ever miss, never serve
     * a stale answer.</p>
     */
    public static int generation() {
        return generation;
    }

    public static void invalidate() {
        generation++;
        groupItems.clear();
        // Group contents feed applyGrouping's representative stacks, so its memo is stale too.
        // Covers the RRV-rebuild invalidation site, which does not go through reload().
        GroupingResultCache.invalidate();
    }

    /** Counts memo misses that fell through to RRV's registry sweep; reported at each prewarm. */
    private static int lazySweeps = 0;

    /**
     * Records that a group's contents were computed by RRV's own {@code getGroupItems} — a
     * full {@code BuiltInRegistries.ITEM} walk constructing an {@code ItemStack} per item.
     * Every one of these is a group the prewarm should have covered, so the count is the
     * diagnostic for whether the memo is being served or bypassed.
     */
    public static void recordLazySweep() {
        lazySweeps++;
    }

    /**
     * Seeds every family group's members from the stack-sensitives map in a single pass.
     *
     * <p><b>Why this exists:</b> {@link #invalidate()} is called at the start of a batched
     * injection cycle without a matching {@link #prewarm()} (the prewarm only runs once the
     * cycle finishes), so the memo is cold for the whole injection window. Any group
     * representative rendered in that window missed the memo and fell through to RRV's
     * {@code getGroupItems}, which walks the entire item registry allocating an
     * {@code ItemStack} per item — ~8,000 allocations, each firing every installed mod's
     * ItemStack-init mixin, per group, per frame until the RETURN inject memoized it. Spark
     * measured this at ~1.07% of render-thread time under {@code ItemSlot.extractRenderState}.</p>
     *
     * <p>The registry walk is only there to feed <em>foreign</em> group matching — family
     * membership is decided purely by each stack-sensitive's SkyBlock id (see
     * {@code computeAllGroups}). So a family group never needs the registry at all: one pass
     * over the ~200 populated sensitive entries answers all ~900 of them at once.</p>
     *
     * <p>An entry is stored for every active family group, including those that end up empty,
     * so a miss can never recur for the rest of the generation. Contents are ordered with the
     * same {@code sortByGroupOrder} the prewarm publish uses, so a lazily filled group is
     * indistinguishable from a prewarmed one. Render thread only — it reads the sensitives map
     * that the render thread mutates.</p>
     *
     * @return true when at least one family group was seeded
     */
    public static boolean fillFamilyGroups() {
        List<SkyblockFamilyStackGroup> families = SkyblockStackGroups.activeGroups();
        if (families.isEmpty()) {
            return false;
        }

        Map<AbstractStackGroup, List<ItemStack>> members = new IdentityHashMap<>();
        for (SkyblockFamilyStackGroup family : families) {
            members.put(family, new ArrayList<>());
        }

        var backing = ((ClientRecipeCacheAccessor) ClientRecipeCache.INSTANCE)
                .skyrecipes$getStackSensitives();
        for (List<ItemView.StackSensitive> list : backing.values()) {
            for (ItemView.StackSensitive sensitive : list) {
                ItemStack stack = sensitive.stack();
                String skyblockId = SkyblockIdExtractor.extract(stack);
                if (skyblockId == null) {
                    continue;
                }
                SkyblockFamilyStackGroup family = SkyblockStackGroups.groupFor(skyblockId);
                if (family == null) {
                    continue;
                }
                List<ItemStack> bucket = members.get(family);
                if (bucket != null) {
                    bucket.add(stack);
                }
            }
        }

        for (Map.Entry<AbstractStackGroup, List<ItemStack>> entry : members.entrySet()) {
            StackGroupManagerAccessor.skyrecipes$sortByGroupOrder(entry.getValue(), entry.getKey().getId());
            put(entry.getKey(), entry.getValue());
        }
        LOGGER.debug("Lazily filled {} family stack groups", members.size());
        return true;
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
        // there (batched injection, RRV rebuilds) with no synchronization. One pass over
        // the ~200 populated map entries via the accessor, not a stream per registry item.
        Map<Item, List<ItemStack>> sensitives = new IdentityHashMap<>();
        var backing = ((ClientRecipeCacheAccessor) ClientRecipeCache.INSTANCE)
                .skyrecipes$getStackSensitives();
        for (Map.Entry<Item, List<ItemView.StackSensitive>> entry : backing.entrySet()) {
            List<ItemView.StackSensitive> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>(list.size());
            for (ItemView.StackSensitive sensitive : list) {
                stacks.add(sensitive.stack());
            }
            sensitives.put(entry.getKey(), stacks);
        }

        SkyRecipesExecutors.worker().execute(() -> {
            try {
                Map<AbstractStackGroup, List<ItemStack>> computed = computeAllGroups(groups, sensitives);
                Minecraft.getInstance().execute(() -> publish(gen, computed));
            } catch (Exception e) {
                // WARN, not DEBUG: a swallowed failure here is invisible and costs a full
                // item-registry sweep per group per frame until something re-warms the memo.
                LOGGER.warn("Stack group prewarm failed; groups will compute lazily", e);
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
        if (generation != gen) {
            // The cycle this was computed for is gone. Whoever bumped the generation is
            // responsible for the next prewarm; say so rather than vanishing silently,
            // because the memo is cold until they do.
            LOGGER.debug("Discarded prewarm of {} groups: generation moved {} -> {}",
                    computed.size(), gen, generation);
            return;
        }
        for (Map.Entry<AbstractStackGroup, List<ItemStack>> entry : computed.entrySet()) {
            StackGroupManagerAccessor.skyrecipes$sortByGroupOrder(entry.getValue(), entry.getKey().getId());
            put(entry.getKey(), entry.getValue());
        }
        if (lazySweeps > 0) {
            // Each of these was a full item-registry walk that the memo should have absorbed.
            LOGGER.info("Prewarmed {} stack groups ({} groups had fallen through to RRV's"
                    + " registry sweep first)", computed.size(), lazySweeps);
            lazySweeps = 0;
        } else {
            LOGGER.debug("Prewarmed {} stack groups", computed.size());
        }
    }

    /**
     * Hashable stand-in for {@code ItemStack.isSameItemSameComponents} (verified against MC
     * 26.1.2 source: same {@code Item} + {@code Objects.equals} on the components maps, with
     * both-empty stacks equal regardless of components). {@code PatchedDataComponentMap} has
     * value-based equals/hashCode. Empty-vs-non-empty pairs never match here, which is at
     * worst slightly stricter than vanilla and only for empty stacks, which don't occur in
     * the item list.
     *
     * <p>Memoized per stack instance. Hashing a SkyBlock stack's component map is <em>not</em>
     * cheap — it walks {@code CustomData}'s whole {@code CompoundTag} tree and every
     * {@code ItemLore} component — and {@code appendMatchingGroups} keys the entire result
     * list on every keystroke. Stacks are immutable and identity-stable here, so the key (and
     * with it the hash) is computed once per instance and reused for the rest of the session.
     * Guava's {@code weakKeys()} compares by identity and lets stacks from a replaced index be
     * collected, exactly like {@code SkyblockIdExtractor.ID_CACHE}.</p>
     */
    public static Object dedupKey(ItemStack stack) {
        DedupKey cached = DEDUP_KEYS.get(stack);
        if (cached != null) {
            return cached;
        }
        boolean empty = stack.isEmpty();
        DedupKey key = new DedupKey(stack.getItem(), empty, empty ? null : stack.getComponents());
        DEDUP_KEYS.put(stack, key);
        return key;
    }

    private static final java.util.concurrent.ConcurrentMap<ItemStack, DedupKey> DEDUP_KEYS =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();

    /**
     * Value-equal to the record it replaces; the only difference is that the expensive
     * component-map hash is computed once at construction instead of on every lookup.
     * {@code equals} still compares by value, so two distinct instances carrying equal
     * components dedup against each other as before.
     */
    private static final class DedupKey {

        private final Item item;
        private final boolean empty;
        private final DataComponentMap components;
        private final int hash;

        DedupKey(Item item, boolean empty, DataComponentMap components) {
            this.item = item;
            this.empty = empty;
            this.components = components;
            int h = System.identityHashCode(item);
            h = 31 * h + Boolean.hashCode(empty);
            this.hash = 31 * h + (components == null ? 0 : components.hashCode());
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DedupKey other)) return false;
            return hash == other.hash
                    && item == other.item
                    && empty == other.empty
                    && Objects.equals(components, other.components);
        }
    }
}
