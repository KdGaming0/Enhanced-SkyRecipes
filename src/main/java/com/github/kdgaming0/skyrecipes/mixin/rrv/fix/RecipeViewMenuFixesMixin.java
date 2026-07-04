package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.util.SafeDummySlot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Fixes for RRV's {@link RecipeViewMenu} slot lifecycle.
 *
 * <p>Contains two independent fixes:</p>
 * <ul>
 *   <li><b>Return-to-cache guard:</b> Bounds-checks slot indices when restoring temporary
 *       simulation state, preventing {@code ArrayIndexOutOfBoundsException}.</li>
 *   <li><b>Slot padding:</b> Adds inactive dummy slots before {@code updateReferences()}
 *       so third-party mods probing fixed indices don't crash during RRV's slot rebuilds.</li>
 * </ul>
 */
@Mixin(value = RecipeViewMenu.class, remap = false)
public class RecipeViewMenuFixesMixin {

    @Unique
    private static final int SKYRECIPES$MINIMUM_SLOT_COUNT = 54;

    @Unique
    private static final SafeDummySlot[] SKYRECIPES$DUMMY_SLOTS = new SafeDummySlot[SKYRECIPES$MINIMUM_SLOT_COUNT];

    static {
        for (int i = 0; i < SKYRECIPES$MINIMUM_SLOT_COUNT; i++) {
            SKYRECIPES$DUMMY_SLOTS[i] = new SafeDummySlot();
        }
    }

    // ── Return-to-cache guard ─────────────────────────────────────────────────

    @Inject(method = "returnToCache", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$safeReturnToCache(HashMap<Integer, ItemStack> usedPlayerSlots, NonNullList<ItemStack> stackSupply, CallbackInfo ci) {
        usedPlayerSlots.forEach((playerSlot, stack) -> {
            if (playerSlot >= 0 && playerSlot < stackSupply.size()) {
                if (stackSupply.get(playerSlot).isEmpty()) {
                    stackSupply.set(playerSlot, stack);
                } else {
                    stackSupply.get(playerSlot).setCount(stackSupply.get(playerSlot).getCount() + stack.getCount());
                }
            }
        });
        ci.cancel();
    }

    // ── Slot padding ──────────────────────────────────────────────────────────

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
        if (currentSize >= SKYRECIPES$MINIMUM_SLOT_COUNT) {
            return;
        }

        menu.slots.addAll(Arrays.asList(SKYRECIPES$DUMMY_SLOTS).subList(currentSize, SKYRECIPES$MINIMUM_SLOT_COUNT));
    }
}
