package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Visible-slot UI mutation must not race result publication, pagination or rendering. */
@Mixin(value = AbstractRrvItemListOverlay.class, remap = false)
public class ItemViewSlotThreadingMixin {
    @WrapOperation(method = "updateSlots", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/TracingExecutor;execute(Ljava/lang/Runnable;)V"))
    private void skyrecipes$publishVisibleSlots(TracingExecutor executor, Runnable task,
                                               Operation<Void> original) {
        if ((Object) this instanceof ItemViewOverlay) Minecraft.getInstance().execute(task);
        else original.call(executor, task);
    }
}
