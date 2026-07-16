package com.github.kdgaming0.skyrecipes.mixin.recipe;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
import com.github.kdgaming0.skyrecipes.rrv.recipe.StackGroupItemsCache;
import com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup.SkyblockFamilyStackGroup;
import com.github.kdgaming0.skyrecipes.rrv.recipe.stackgroup.SkyblockStackGroups;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Removes the per-keystroke render-thread freeze introduced by RRV 8.5.0's stack groups.
 *
 * <p><b>The problem (verified in RRV 8.6.0 sources, spark-profiled):</b> every keystroke in the
 * item list search bar runs {@code ItemViewOverlay.updateDisplayedItems} →
 * {@code StackGroupManager.appendMatchingGroups}, which for <em>every</em> stack group whose id
 * or name contains the query substring (~75 shipped groups; a 1–2 char query matches most of
 * them) calls {@code getGroupItems} — a full item-registry walk constructing a fresh
 * {@code ItemStack} per item, each paying every installed mod's ItemStack-init mixin. Each group
 * item is then deduplicated with an O(results) {@code noneMatch} stream scan, quadratic against
 * SkyBlock-sized result lists. {@code ItemSlot} additionally calls {@code getGroupItems} per
 * visible group slot per frame.</p>
 *
 * <p><b>Fixes:</b> {@code getGroupItems(AbstractStackGroup)} is memoized in
 * {@link StackGroupItemsCache} (invalidated on stack-group reload here, and on RRV cache
 * rebuilds / SkyRecipes injection cycles elsewhere), and {@code appendMatchingGroups} is
 * replaced with an equivalent implementation using a hash-set dedup keyed to
 * {@code ItemStack.isSameItemSameComponents} semantics.</p>
 *
 * <p><b>Upstream:</b> RRV bug, worth filing; remove the memoization parts if RRV caches
 * group contents.</p>
 *
 * <p>This mixin is also the integration point for SkyBlock family stack groups
 * ({@code SkyblockStackGroups}): re-injection after RRV's reload clears the group list,
 * per-stack (rather than per-{@code Item}) group resolution for stacks carrying a
 * SkyBlock ID, and tier-ordered member sorting.</p>
 */
@Mixin(value = StackGroupManager.class, remap = false)
public class StackGroupManagerMixin {

    @Shadow
    @Final
    private static Set<Identifier> nameMatchedGroups;

    @Shadow
    public static List<ItemStack> getGroupItems(AbstractStackGroup group) {
        throw new AssertionError();
    }

    @Inject(
            method = "getGroupItems(Lcc/cassian/rrv/common/recipe/stackgroup/data/AbstractStackGroup;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void skyrecipes$serveCachedGroupItems(AbstractStackGroup group,
                                                         CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> cached = StackGroupItemsCache.get(group);
        if (cached != null) {
            // Fresh mutable copy: some RRV callers (e.g. StackGroupNameWidget) expect an
            // independent list, and the memo must never be exposed for mutation.
            cir.setReturnValue(new ArrayList<>(cached));
        }
    }

    @Inject(
            method = "getGroupItems(Lcc/cassian/rrv/common/recipe/stackgroup/data/AbstractStackGroup;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static void skyrecipes$storeGroupItems(AbstractStackGroup group,
                                                   CallbackInfoReturnable<List<ItemStack>> cir) {
        StackGroupItemsCache.put(group, cir.getReturnValue());
    }

    /** RETURN (not TAIL): reload() has an early return when stack groups are disabled. */
    @Inject(method = "reload", at = @At("RETURN"))
    private static void skyrecipes$invalidateOnReload(CallbackInfo ci) {
        // reload() cleared the group list; re-add the SkyBlock family groups before the
        // prewarm so it computes their contents too.
        SkyblockStackGroups.injectInto();
        StackGroupItemsCache.invalidate();
        StackGroupItemsCache.prewarm();
    }

    /**
     * Per-stack group resolution for SkyBlock items. RRV's own lookup caches the result
     * per Minecraft {@code Item} — but nearly all SkyBlock items share a handful of
     * vanilla items (player heads, enchanted books, …), so that cache would pin every
     * player-head stack to whichever family the first one matched. SkyBlock stacks
     * resolve through an O(1) SkyBlock-ID map instead; returning null (rather than
     * falling through) also keeps them out of RRV's vanilla groups when family grouping
     * is disabled.
     */
    @Inject(method = "getGroupForItem", at = @At("HEAD"), cancellable = true)
    private static void skyrecipes$resolveSkyblockGroup(ItemStack stack,
                                                        CallbackInfoReturnable<AbstractStackGroup> cir) {
        String skyblockId = SkyblockIdExtractor.extract(stack);
        if (skyblockId != null) {
            cir.setReturnValue(SkyblockStackGroups.isActive()
                    ? SkyblockStackGroups.groupFor(skyblockId) : null);
        }
    }

    /**
     * Family groups sort by tier, not registry order — RRV's fallback ordering is keyed
     * by the vanilla {@code Item}, which is identical for every member of a family.
     * A user-configured order from RRV's group screen still wins (handled inside).
     */
    @Inject(method = "sortByGroupOrder", at = @At("HEAD"), cancellable = true)
    private static void skyrecipes$sortFamilyGroupsByTier(List<ItemStack> items, Identifier groupId,
                                                          CallbackInfo ci) {
        if (SkyblockStackGroups.sortIfFamilyGroup(items, groupId)) {
            ci.cancel();
        }
    }

    /**
     * Behavior-identical replacement for RRV's {@code appendMatchingGroups}: same group
     * matching and ordering, but group contents come from the memoized {@code getGroupItems}
     * and deduplication uses a hash set instead of an O(results) stream scan per group item.
     */
    @Inject(method = "appendMatchingGroups", at = @At("HEAD"), cancellable = true)
    private static void skyrecipes$fastAppendMatchingGroups(String query, List<ItemStack> results,
                                                            CallbackInfoReturnable<List<ItemStack>> cir) {
        nameMatchedGroups.clear();
        if (!Configs.STACK_GROUPS.areStackGroupsEnabled()) {
            cir.setReturnValue(results);
            return;
        }

        String lower = query.toLowerCase(Locale.ROOT);
        List<ItemStack> extendedResults = new ArrayList<>(results);
        Set<Object> seen = new HashSet<>(Math.max(16, results.size() * 2));
        for (ItemStack existing : results) {
            seen.add(StackGroupItemsCache.dedupKey(existing));
        }

        for (AbstractStackGroup group : StackGroupManager.stackGroups) {
            boolean match;
            if (group instanceof SkyblockFamilyStackGroup familyGroup) {
                // Family groups match on their display name only, and never on 1-char
                // queries: with 1000+ generated groups, RRV's id-substring rule would
                // append most of the item database on the first keystroke.
                match = lower.length() >= 2 && familyGroup.lowercaseName().contains(lower);
            } else {
                match = group.getId().toString().toLowerCase(Locale.ROOT).contains(lower);
                if (!match) {
                    Component groupName = group.getName();
                    match = groupName != null && groupName.getString().toLowerCase(Locale.ROOT).contains(lower);
                }
            }
            if (!match) continue;

            nameMatchedGroups.add(group.getId());
            for (ItemStack stack : getGroupItems(group)) {
                if (seen.add(StackGroupItemsCache.dedupKey(stack))) {
                    extendedResults.add(stack);
                }
            }
        }
        cir.setReturnValue(extendedResults);
    }
}
