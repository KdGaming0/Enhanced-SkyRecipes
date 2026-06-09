package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import com.github.kdgaming0.skyrecipes.rrv.safety.SafeDummySlot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents third-party mods from crashing with {@code IndexOutOfBoundsException}
 * when they query slot indices from RRV's {@link RecipeViewMenu} while its slots
 * are being cleared and rebuilt.
 *
 * <p>RRV calls {@code slots.clear()} inside {@code updateByPage()} and then
 * repopulates the list dynamically. During that window any mod that probes a
 * hardcoded slot index (e.g. Catharsis GUI matching) will crash. This mixin
 * returns a safe dummy slot for out-of-bounds indices instead of throwing.
 *
 * <p>The mixin targets {@link AbstractContainerMenu} rather than
 * {@link RecipeViewMenu} because {@code getSlot()} is inherited; Mixin cannot
 * reliably remap an injection on an inherited method when the target class is
 * a mod class.
 */
@Mixin(AbstractContainerMenu.class)
public class RecipeViewMenuSlotSafetyMixin {

    @Shadow
    public final NonNullList<Slot> slots = null;

    @Unique
    private static final Slot SKYRECIPES$SAFE_DUMMY_SLOT = new SafeDummySlot();

    @Inject(
            method = "getSlot(I)Lnet/minecraft/world/inventory/Slot;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skyrecipes$safeGetSlot(int index, CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof RecipeViewMenu
                && (index < 0 || index >= this.slots.size())) {
            cir.setReturnValue(SKYRECIPES$SAFE_DUMMY_SLOT);
        }
    }
}
