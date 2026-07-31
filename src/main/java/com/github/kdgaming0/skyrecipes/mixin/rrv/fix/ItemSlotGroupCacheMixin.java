package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.recipe.stackgroup.data.AbstractStackGroup;
import com.github.kdgaming0.skyrecipes.rrv.recipe.StackGroupItemsCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Resolves an {@code ItemSlot}'s stack-group state once per slot instead of once per frame.
 *
 * <p><b>The problem (verified in RRV 8.6.4 sources, spark-profiled):</b> {@code ItemSlot} holds
 * an immutable {@code stack}/{@code x}/{@code y}, but {@code extractRenderState} recomputes
 * everything derived from them on every frame — {@code getGroupItems(String)},
 * {@code getGroup(String)} (twice), {@code getGroupForItem(ItemStack)} and
 * {@code Identifier.parse}. With ~45–90 visible slots at 60 fps that is several thousand
 * lookups per second. Even served from SkyRecipes' memos each one still costs an id-index
 * probe, an {@code IdentityHashMap} lookup and a {@code CallbackInfoReturnable} allocation;
 * spark attributed ~1.9% of render-thread time to this cluster.</p>
 *
 * <p>Slot instances are rebuilt by {@code AbstractRrvItemListOverlay.updateSlots()} — page
 * turn, query edit, group toggle, screen resize — so every value cached here is constant for
 * the instance's whole lifetime. Caching on the slot turns per-frame work into per-rebuild
 * work.</p>
 *
 * <h2>What is deliberately <em>not</em> cached</h2>
 * <p>{@code StackGroupManager.isEffectivelyExpanded} is left alone. It reads
 * {@code searchExpandActive || expandedGroups.contains(id)}, and {@code searchExpandActive}
 * flips during a search <em>without</em> rebuilding slots — memoizing it would freeze group
 * borders and the +/- sprite in whatever state the slot was built in. Only group identity and
 * membership are cached; both change only through paths that bump the generation below.</p>
 *
 * <h2>Invalidation</h2>
 * <p>Instance lifetime alone would already be correct for every RRV path known today, but the
 * cache is additionally stamped with {@link StackGroupItemsCache#generation()} rather than
 * trusting that. All eight existing invalidation sites (stack-group reload, RRV cache rebuild,
 * SkyRecipes injection cycles) bump it, and a stamp mismatch forces re-resolution — so a
 * mutation path that does not rebuild slots makes this cache miss, never answer wrongly. Same
 * safety-net posture as {@code StackGroupIdIndex}'s size check.</p>
 *
 * <p>Each memo also stores the argument it was resolved for and re-resolves if a later call
 * passes a different one, so this stays correct even if RRV starts reusing a slot for another
 * group or stack.</p>
 *
 * <p>{@code @WrapOperation} rather than {@code @Redirect} so other RRV addons can wrap the same
 * call sites, and {@code require = 0} throughout: these are pure optimizations, so a future RRV
 * moving any one call should drop that wrapper silently rather than fail the class
 * transformation and take the whole item list with it.</p>
 */
@Mixin(value = ItemSlot.class, remap = false)
public class ItemSlotGroupCacheMixin {

    @Unique
    private int skyrecipes$generation = -1;

    @Unique
    private String skyrecipes$groupItemsKey;
    @Unique
    private List<ItemStack> skyrecipes$groupItems;

    @Unique
    private String skyrecipes$groupKey;
    @Unique
    private boolean skyrecipes$groupCached;
    @Unique
    private AbstractStackGroup skyrecipes$group;

    @Unique
    private ItemStack skyrecipes$itemGroupKey;
    @Unique
    private boolean skyrecipes$itemGroupCached;
    @Unique
    private AbstractStackGroup skyrecipes$itemGroup;

    @Unique
    private String skyrecipes$parsedIdKey;
    @Unique
    private Identifier skyrecipes$parsedId;

    /**
     * Drops the group-derived memos when anything bumped the stack-group generation.
     * The parsed {@code Identifier} is a pure function of the slot's own tag and survives.
     */
    @Unique
    private void skyrecipes$syncGeneration() {
        int current = StackGroupItemsCache.generation();
        if (current != skyrecipes$generation) {
            skyrecipes$generation = current;
            skyrecipes$groupItemsKey = null;
            skyrecipes$groupItems = null;
            skyrecipes$groupKey = null;
            skyrecipes$groupCached = false;
            skyrecipes$group = null;
            skyrecipes$itemGroupKey = null;
            skyrecipes$itemGroupCached = false;
            skyrecipes$itemGroup = null;
        }
    }

    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/recipe/stackgroup/StackGroupManager;"
                            + "getGroupItems(Ljava/lang/String;)Ljava/util/List;"
            ),
            require = 0
    )
    private List<ItemStack> skyrecipes$cacheGroupItems(String groupId, Operation<List<ItemStack>> original) {
        skyrecipes$syncGeneration();
        if (skyrecipes$groupItems == null || !groupId.equals(skyrecipes$groupItemsKey)) {
            skyrecipes$groupItems = original.call(groupId);
            skyrecipes$groupItemsKey = groupId;
        }
        return skyrecipes$groupItems;
    }

    /** Two call sites in {@code extractRenderState} (hover tooltip, expanded-group borders). */
    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/recipe/stackgroup/StackGroupManager;"
                            + "getGroup(Ljava/lang/String;)"
                            + "Lcc/cassian/rrv/common/recipe/stackgroup/data/AbstractStackGroup;"
            ),
            require = 0
    )
    private AbstractStackGroup skyrecipes$cacheGroup(String groupId, Operation<AbstractStackGroup> original) {
        skyrecipes$syncGeneration();
        if (!skyrecipes$groupCached || !groupId.equals(skyrecipes$groupKey)) {
            skyrecipes$group = original.call(groupId);
            skyrecipes$groupKey = groupId;
            skyrecipes$groupCached = true;
        }
        return skyrecipes$group;
    }

    /**
     * The non-group branch, taken by every ordinary item slot. This is the one that runs
     * SkyRecipes' own {@code skyrecipes$resolveSkyblockGroup} inject and its SkyBlock-id
     * extraction, so it is the most expensive of the four.
     */
    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/recipe/stackgroup/StackGroupManager;"
                            + "getGroupForItem(Lnet/minecraft/world/item/ItemStack;)"
                            + "Lcc/cassian/rrv/common/recipe/stackgroup/data/AbstractStackGroup;"
            ),
            require = 0
    )
    private AbstractStackGroup skyrecipes$cacheItemGroup(ItemStack stack, Operation<AbstractStackGroup> original) {
        skyrecipes$syncGeneration();
        if (!skyrecipes$itemGroupCached || skyrecipes$itemGroupKey != stack) {
            skyrecipes$itemGroup = original.call(stack);
            skyrecipes$itemGroupKey = stack;
            skyrecipes$itemGroupCached = true;
        }
        return skyrecipes$itemGroup;
    }

    /**
     * Only the parse is cached — the {@code isEffectivelyExpanded} it feeds still runs every
     * frame, which is what keeps search-expand responsive.
     */
    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/Identifier;"
                            + "parse(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
            ),
            require = 0
    )
    private Identifier skyrecipes$cacheParsedId(String id, Operation<Identifier> original) {
        if (skyrecipes$parsedId == null || !id.equals(skyrecipes$parsedIdKey)) {
            skyrecipes$parsedId = original.call(id);
            skyrecipes$parsedIdKey = id;
        }
        return skyrecipes$parsedId;
    }
}
