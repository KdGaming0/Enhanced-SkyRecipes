package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Visible-slot UI mutation must not race result publication, pagination or rendering. */
@Mixin(value = AbstractRrvItemListOverlay.class, remap = false)
public class ItemViewSlotThreadingMixin {
    @WrapOperation(method = "updateSlots", at = @At(value = "INVOKE",
            target = "Lcc/cassian/rrv/common/recipe/util/RrvUtil;execute(Ljava/lang/Runnable;)V"))
    private void skyrecipes$publishVisibleSlots(Runnable task, Operation<Void> original) {
        Minecraft.getInstance().execute(task);
    }
}
