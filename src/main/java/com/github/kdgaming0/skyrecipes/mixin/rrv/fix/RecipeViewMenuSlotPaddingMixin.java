package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.safety.SafeDummySlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pads {@link RecipeViewMenu}'s {@code slots} list with inactive dummy slots before
 * {@code updateReferences()} is called inside {@code updateByPage()}, so that
 * third-party mods doing hardcoded direct access (e.g. {@code menu.slots.get(29)})
 * do not crash with {@code IndexOutOfBoundsException}.
 *
 * <p>RRV calls {@code slots.clear()} inside {@code updateByPage()} and rebuilds the
 * list from scratch. During that window — and for recipes that simply don't need many
 * slots — the list can shrink to as few as 1 entry. Many SkyBlock mods probe fixed
 * indices assuming a chest-like layout. This mixin adds harmless off-screen dummy slots
 * until the list reaches a safe minimum size.
 *
 * <p>The padding is injected <b>before</b> {@code updateReferences()} because that
 * method calls {@code Slot.set()} which triggers mod event handlers that immediately
 * access the slot list. Padding at {@code RETURN} is too late.
 *
 * <p>The dummy slots are skipped by vanilla rendering ({@code isActive() == false}),
 * ignored by RRV's recipe binding (which uses per-type slot counts), and reject all
 * item placement, so they have no functional impact on gameplay.
 */
@Mixin(RecipeViewMenu.class)
public class RecipeViewMenuSlotPaddingMixin {

    @Unique
    private static final int SKYRECIPES$MINIMUM_SLOT_COUNT = 54;

    @Inject(
            method = "updateByPage",
            at = @At(
                    value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/recipe/inventory/RecipeViewMenu;updateReferences()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void skyrecipes$padSlotsBeforeUpdateReferences(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int currentSize = menu.slots.size();

        for (int i = currentSize; i < SKYRECIPES$MINIMUM_SLOT_COUNT; i++) {
            SafeDummySlot dummy = new SafeDummySlot();
            dummy.index = i;
            menu.slots.add(dummy);
        }
    }
}
