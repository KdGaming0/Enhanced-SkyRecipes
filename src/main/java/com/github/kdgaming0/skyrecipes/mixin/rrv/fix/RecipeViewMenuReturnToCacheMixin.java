package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * Guards RRV's {@code returnToCache} against out-of-bounds inventory access.
 *
 * <p>RRV's {@code invCheckAndFind} stores player slots keyed by
 * {@code containerMenu.findSlot(inventory, playerSlot)} — i.e. the slot index
 * inside the currently-open container menu. However, {@code returnToCache}
 * iterates those same keys and uses them to index into {@code stackSupply},
 * which is sized to the player's inventory ({@code Inventory#getContainerSize()}).
 *
 * <p>When the player has a container menu open whose slots outnumber the
 * inventory (e.g. a chest, or even the inventory menu where menu slot indices
 * are offset past the container slots), those menu-slot keys exceed
 * {@code stackSupply}'s bounds and crash with:
 * <pre>
 *   java.lang.ArrayIndexOutOfBoundsException: Index N out of bounds for length M
 * </pre>
 *
 * <p>This mixin replaces the unchecked loop with a bounds-checked one. Slots
 * outside {@code stackSupply}'s range are silently skipped; this is safe because
 * {@code returnToCache} only restores temporary simulation state, not real
 * inventory data.
 *
 * <p><b>TODO — Remove once RRV fixes the key mismatch in {@code returnToCache}.</b>
 */
@Mixin(value = RecipeViewMenu.class, remap = false)
public class RecipeViewMenuReturnToCacheMixin {

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
}
