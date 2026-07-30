package com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup;

import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Render-thread only, like every reader and mutator of {@code stackGroups}, so no
 * synchronization. The map is rebuilt lazily on first use after an invalidation, and a
 * size check against the live list acts as a safety net for any RRV mutation path that
 * does not route through a known invalidation point — a stale map can then only miss, never
 * answer wrongly.</p>
 */
public final class StackGroupIdIndex {

    private static Map<String, AbstractStackGroup> byIdString;
    private static int indexedSize = -1;

    private StackGroupIdIndex() {
    }

    /** Drops the map. Called wherever the group list is rebuilt or spliced. */
    public static void invalidate() {
        byIdString = null;
        indexedSize = -1;
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
        List<AbstractStackGroup> groups = StackGroupManager.stackGroups;
        Map<String, AbstractStackGroup> map = byIdString;
        if (map != null && indexedSize == groups.size()) {
            return map;
        }
        // First id wins, mirroring the first-match-wins break in RRV's scan.
        map = new HashMap<>(Math.max(16, groups.size() * 2));
        for (AbstractStackGroup group : groups) {
            map.putIfAbsent(group.getId().toString(), group);
        }
        byIdString = map;
        indexedSize = groups.size();
        return map;
    }
}
