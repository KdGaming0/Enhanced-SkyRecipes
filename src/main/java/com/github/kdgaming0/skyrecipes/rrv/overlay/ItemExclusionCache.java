package com.github.kdgaming0.skyrecipes.rrv.overlay;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

/**
 * Per-stack memo of {@code ItemView.isExcludedItem(ItemStack)}.
 *
 * <p><b>Why:</b> {@code ItemViewOverlay.updateQuery} runs
 * {@code filteredItems.removeIf(ItemView::isExcludedItem)} and then
 * {@code updateDisplayedItems} runs it again over the grouping output — two full passes over
 * a ~8.5k-entry list. Each call allocates an {@code ItemStackTemplate} <em>and</em> an
 * {@code AtomicBoolean} before doing any useful work, so a single {@code updateQuery} burns
 * ~34k short-lived objects. And {@code updateQuery} fires on every container open, resize,
 * and widget rebuild, not just on keystrokes (see {@code GroupingResultCache}).</p>
 *
 * <p><b>Correctness.</b> The verdict depends on two things, and both invalidation channels
 * are covered:</p>
 * <ul>
 *   <li>The four {@code ItemView} exclusion collections, which are <b>append-only</b> — the
 *       class exposes {@code excludeX} adders and no remove or clear path. Their combined
 *       size is therefore a complete change signature, checked by the caller on every lookup
 *       via {@link #checkSignature(int)}.</li>
 *   <li>The {@code rrv:excluded_potions} / {@code rrv:excluded_enchantments} tags, which
 *       change on a resource reload and would <em>not</em> move that signature. Covered by
 *       {@link #invalidate()} from RRV's own {@code ItemFilters.clearCaches()} — the same
 *       signal RRV uses to drop its item index, so this memo can never outlive the list it
 *       describes.</li>
 * </ul>
 *
 * <p>Stacks are immutable and identity-stable, so an entry is otherwise valid for the
 * session. Guava's {@code weakKeys()} compares by identity and lets stacks from a replaced
 * index be collected, like {@code SkyblockIdExtractor.ID_CACHE}. Concurrent because RRV
 * reaches {@code isExcludedItem} from both the render thread and its background executor.</p>
 */
public final class ItemExclusionCache {

    private static final ConcurrentMap<ItemStack, Boolean> VERDICTS =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();

    /** Combined size of the exclusion collections the cached verdicts were computed against. */
    private static volatile int signature = -1;

    private ItemExclusionCache() {
    }

    /** Drops every cached verdict. Called from RRV's {@code ItemFilters.clearCaches()}. */
    public static void invalidate() {
        VERDICTS.clear();
        signature = -1;
    }

    /**
     * Reconciles the cache with the current exclusion-collection signature.
     *
     * @return {@code true} when the cache is valid for this signature; {@code false} when it
     * was just dropped, so the caller must recompute.
     */
    public static boolean checkSignature(int currentSignature) {
        if (currentSignature != signature) {
            VERDICTS.clear();
            signature = currentSignature;
            return false;
        }
        return true;
    }

    @Nullable
    public static Boolean get(@Nullable ItemStack stack) {
        return stack == null ? null : VERDICTS.get(stack);
    }

    public static void put(@Nullable ItemStack stack, boolean excluded) {
        // RRV excludes null stacks; they must never become keys in the memo.
        if (stack != null) {
            VERDICTS.put(stack, excluded);
        }
    }
}
