package com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup;

import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * O(1) {@code "namespace:path"} → group lookup over {@link StackGroupManager#stackGroups}.
 *
 * <p><b>The problem (verified in RRV 8.6.4 sources):</b> RRV's {@code getGroup(String)} and
 * {@code getGroupItems(String)} both resolve an id with a linear scan that calls
 * {@code group.getId().toString()} on every candidate — and {@code Identifier.toString()}
 * builds {@code namespace + ':' + path} fresh each call. RRV ships ~75 groups, so that is
 * cheap for RRV. SkyRecipes injects 1000+ family groups, and {@code ItemSlot} resolves ids
 * this way <em>per group-representative slot per frame</em> (contents, hover tooltip, and
 * the expanded-group border pass) — roughly a thousand throwaway Strings per lookup per
 * frame.</p>
 *
 * <p>RRV 8.10 also resolves groups on background workers. An immutable snapshot is published
 * atomically, while a generation check prevents a rebuild racing {@link #invalidate()} from
 * publishing groups that have just been replaced. The live-list size remains a safety net for
 * any RRV mutation path that does not route through a known invalidation point.</p>
 */
public final class StackGroupIdIndex {

    private record Snapshot(int generation, int indexedSize,
                            Map<String, AbstractStackGroup> byIdString) {
    }

    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Snapshot snapshot;

    private StackGroupIdIndex() {
    }

    /** Drops the map. Called wherever the group list is rebuilt or spliced. */
    public static void invalidate() {
        GENERATION.incrementAndGet();
        snapshot = null;
    }

    /**
     * @return the group with this exact {@code Identifier.toString()} form, or {@code null} —
     * matching RRV's own scan semantics.
     */
    @Nullable
    public static AbstractStackGroup get(String idString) {
        if (idString == null) {
            return null;
        }
        return index().get(idString);
    }

    private static Map<String, AbstractStackGroup> index() {
        Snapshot current = snapshot;
        int generation = GENERATION.get();
        if (current != null
                && current.generation() == generation
                && current.indexedSize() == StackGroupManager.stackGroups.size()) {
            return current.byIdString();
        }
        return rebuild();
    }

    private static synchronized Map<String, AbstractStackGroup> rebuild() {
        for (int attempt = 0; attempt < 2; attempt++) {
            int generation = GENERATION.get();
            Snapshot current = snapshot;
            if (current != null
                    && current.generation() == generation
                    && current.indexedSize() == StackGroupManager.stackGroups.size()) {
                return current.byIdString();
            }

            // ArrayList.toArray gives readers a fixed traversal target without iterator CMEs.
            Object[] groups = StackGroupManager.stackGroups.toArray();
            Map<String, AbstractStackGroup> map = new HashMap<>(Math.max(16, groups.length * 2));
            for (Object value : groups) {
                if (value instanceof AbstractStackGroup group) {
                    // First id wins, mirroring the first-match-wins break in RRV's scan.
                    map.putIfAbsent(group.getId().toString(), group);
                }
            }
            if (generation == GENERATION.get()) {
                Map<String, AbstractStackGroup> published = Map.copyOf(map);
                snapshot = new Snapshot(generation, groups.length, published);
                return published;
            }
        }
        // A group-list replacement won both attempts. One transient miss is safer than
        // publishing stale identities; the next lookup rebuilds from the settled list.
        return Map.of();
    }
}
