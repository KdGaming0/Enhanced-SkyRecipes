package com.github.kdgaming0.skyrecipes.rrv.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-entry memo for RRV's {@code StackGroupManager.applyGrouping(List, boolean)}.
 *
 * <p><b>The problem (verified in RRV 8.6.3 sources, spark-profiled):</b> RRV's
 * {@code MixinAbstractContainerScreen.injectOverlay} runs
 * {@code OverlayManager.checkForScreenChange} on every container-screen render frame, and
 * {@code AbstractContainerScreen.init()} (screen open, resize, widget rebuild) sets
 * {@code newScreenQueued}, so the next frame runs
 * {@code updateOverlaysAndWidgets → onScreenChanged → ItemViewOverlay.updateQuery}.
 * {@code updateQuery} has <em>no early-out for an unchanged query</em>: it always re-runs the
 * filter and then {@code updateDisplayedItems → applyGrouping} over the whole item list.
 * At SkyBlock scale (~8,000 stacks, 1,000+ family groups) that pass costs hundreds of
 * milliseconds on the render thread — a visible freeze on every container open.</p>
 *
 * <p>The grouped output is a pure function of the input list, the {@code searchExpandActive}
 * flag, and stack-group state. Group state changes only in {@code StackGroupManager.reload()}
 * (the sole writer of {@code AbstractStackGroup.isEnabled} and of the group list) and in
 * {@code toggleGroup()} (the sole mutator of {@code expandedGroups}); both call
 * {@link #invalidate()}. {@code areStackGroupsEnabled()} is re-read on every lookup rather
 * than cached, and the flag is part of the key — so a stale result cannot be served.</p>
 *
 * <p>Inputs are compared by <em>element identity</em>, not equality: {@code ItemStack} has no
 * {@code equals}/{@code hashCode} override (MC 26.1.2), and the stacks RRV re-filters come from
 * the same memoized sources each pass, so a reference-wise scan is both exact and ~8k pointer
 * compares. Nothing is served when the identity scan fails, so a genuinely different list
 * always recomputes.</p>
 *
 * <p>Render-thread only (see the caller's {@code isSameThread()} guard) — plain fields, no
 * synchronization. The stored list is never handed out: callers get a fresh mutable copy,
 * because RRV mutates the returned list ({@code availableItems.removeIf(...)} in
 * {@code updateDisplayedItems}).</p>
 *
 * <p><b>Upstream:</b> RRV bug, worth filing — {@code updateQuery} should early-out when the
 * query is unchanged. Remove this memo if that lands.</p>
 */
public final class GroupingResultCache {

    private static ItemStack[] inputSnapshot;
    private static boolean inputFlag;
    private static List<ItemStack> groupedResult;

    private GroupingResultCache() {
    }

    /** Drops the memo. Called from every stack-group mutation point. */
    public static void invalidate() {
        inputSnapshot = null;
        groupedResult = null;
    }

    /**
     * @return a fresh mutable copy of the memoized grouping for this exact input, or
     * {@code null} when nothing is cached for it and the caller must recompute.
     */
    public static List<ItemStack> get(List<ItemStack> items, boolean searchExpandActive) {
        if (groupedResult == null || inputSnapshot == null || searchExpandActive != inputFlag) {
            return null;
        }
        if (items == null || items.size() != inputSnapshot.length || !sameElements(items)) {
            return null;
        }
        return new ArrayList<>(groupedResult);
    }

    /**
     * Stores the grouping computed for {@code items}. Both the input snapshot and the result
     * are copied: RRV refills the same {@code filteredItems} list instance every pass, and
     * mutates the returned list right after {@code applyGrouping} returns.
     */
    public static void put(List<ItemStack> items, boolean searchExpandActive, List<ItemStack> grouped) {
        inputSnapshot = items.toArray(new ItemStack[0]);
        inputFlag = searchExpandActive;
        groupedResult = new ArrayList<>(grouped);
    }

    private static boolean sameElements(List<ItemStack> items) {
        ItemStack[] snapshot = inputSnapshot;
        int i = 0;
        for (ItemStack stack : items) {
            if (stack != snapshot[i++]) {
                return false;
            }
        }
        return true;
    }
}
