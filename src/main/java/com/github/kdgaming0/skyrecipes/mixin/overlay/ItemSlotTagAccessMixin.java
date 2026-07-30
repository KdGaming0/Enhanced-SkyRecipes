package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.ItemSlot;
import com.github.kdgaming0.skyrecipes.mixin.accessor.CustomDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Drops the deep NBT copies from {@code ItemSlot}'s per-frame marker-key reads.
 *
 * <p><b>The problem (verified in RRV 8.6.4 sources):</b> {@code ItemSlot} reads two marker
 * keys — {@code rrv_result} and {@code rrv_stack_group_id} — via
 * {@code stack.get(CUSTOM_DATA).copyTag()}, a full {@code CompoundTag.copy()} of the whole
 * tree. Every SkyRecipes stack carries {@code CUSTOM_DATA} (that is exactly the predicate
 * {@code ItemFiltersMixin} keeps items on), so <em>every</em> visible slot pays a deep copy
 * on <em>every</em> frame: ~45–90 slots at 60 fps is several thousand NBT tree copies per
 * second. {@code hasGroupNeighbor} multiplies it further — it scans all current-frame slots,
 * and is itself called per direction per expanded group slot.</p>
 *
 * <p>All four call sites only {@code contains}/{@code get} on the result, so the
 * non-copying view is exact. Same technique (and same reasoning) as the
 * {@code applyGrouping} redirect in {@code StackGroupManagerMixin}.</p>
 *
 * <p>{@code require = 0} throughout, and one injector per target method: these are pure
 * optimizations, so a future RRV moving or renaming any one of them should silently drop
 * that redirect rather than fail the class transformation and take the whole item list
 * with it.</p>
 *
 * <p><b>Upstream:</b> RRV bug, worth filing alongside the {@code applyGrouping} one —
 * reading a marker key should not deep-copy the tag. Remove this if RRV switches to a
 * non-copying read.</p>
 */
@Mixin(value = ItemSlot.class, remap = false)
public class ItemSlotTagAccessMixin {

    /** Per visible slot, per frame — the hottest of the four. */
    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/CustomData;copyTag()Lnet/minecraft/nbt/CompoundTag;"
            ),
            require = 0
    )
    private CompoundTag skyrecipes$readMarkerTagWithoutCopy(CustomData data) {
        return ((CustomDataAccessor) (Object) data).skyrecipes$getTag();
    }

    /** Per neighbour slot, per direction, per expanded group slot, per frame. */
    @Redirect(
            method = "hasGroupNeighbor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/CustomData;copyTag()Lnet/minecraft/nbt/CompoundTag;"
            ),
            require = 0
    )
    private CompoundTag skyrecipes$readNeighborTagWithoutCopy(CustomData data) {
        return ((CustomDataAccessor) (Object) data).skyrecipes$getTag();
    }

    /** Two sites; only per click, included so the read path is uniform. */
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/CustomData;copyTag()Lnet/minecraft/nbt/CompoundTag;"
            ),
            require = 0
    )
    private CompoundTag skyrecipes$readClickTagWithoutCopy(CustomData data) {
        return ((CustomDataAccessor) (Object) data).skyrecipes$getTag();
    }
}
