package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.api.recipe.ItemView;
import com.github.kdgaming0.skyrecipes.rrv.overlay.ItemExclusionCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Serves {@code ItemView.isExcludedItem(ItemStack)} from {@link ItemExclusionCache}.
 *
 * <p>See that class for why the memo pays off and how both invalidation channels are
 * covered. This mixin only supplies the half that needs privileged access: the combined
 * size of the four exclusion collections, which is the change signature for every
 * {@code excludeX} call.</p>
 *
 * <p><b>Version dependency:</b> the four {@code @Shadow} fields are a hard dependency on
 * RRV's field names — the same class of coupling as {@code StackGroupManagerMixin}'s
 * {@code nameMatchedGroups} and {@code ClientRecipeCacheAccessor}'s {@code stackSensitives}.
 * A rename fails loudly at startup rather than silently serving a stale {@code false}, which
 * would show an item RRV means to hide. Verified against RRV 8.6.4: all four are
 * {@code private static final ArrayList} with no remove or clear path anywhere.</p>
 */
@Mixin(value = ItemView.class, remap = false)
public class ItemViewExclusionCacheMixin {

    @Shadow
    @Final
    private static List<?> EXCLUDED_ITEMS;
    @Shadow
    @Final
    private static List<?> EXCLUDED_ITEM_STACKS;
    @Shadow
    @Final
    private static List<?> EXCLUDED_POTIONS;
    @Shadow
    @Final
    private static List<?> EXCLUDED_ENCHANTMENTS;

    @Unique
    private static int skyrecipes$exclusionSignature() {
        return EXCLUDED_ITEMS.size() + EXCLUDED_ITEM_STACKS.size()
                + EXCLUDED_POTIONS.size() + EXCLUDED_ENCHANTMENTS.size();
    }

    @Inject(
            method = "isExcludedItem(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void skyrecipes$serveCachedVerdict(ItemStack stack,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!ItemExclusionCache.checkSignature(skyrecipes$exclusionSignature())) {
            return;
        }
        Boolean cached = ItemExclusionCache.get(stack);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    /** Unreachable when the inject above serves a hit — RRV's body is then skipped. */
    @Inject(
            method = "isExcludedItem(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"),
            require = 0
    )
    private static void skyrecipes$storeVerdict(ItemStack stack,
                                                CallbackInfoReturnable<Boolean> cir) {
        ItemExclusionCache.put(stack, cir.getReturnValueZ());
    }
}
