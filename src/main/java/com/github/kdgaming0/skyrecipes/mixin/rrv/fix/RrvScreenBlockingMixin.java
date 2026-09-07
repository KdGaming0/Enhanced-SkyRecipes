package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.client.util.RRVExtendedContainerScreen;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay.InventoryPositionInfo;
import com.github.kdgaming0.skyrecipes.rrv.overlay.WidgetBlockingSync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish helper exclusions before either initial layout or rendering of existing slots. */
@Mixin(value = RRVExtendedContainerScreen.class, remap = false)
public interface RrvScreenBlockingMixin {
    @Inject(method = "extractOverlay", at = @At("HEAD"))
    private static void skyrecipes$syncBlocking(InventoryPositionInfo info, GuiGraphicsExtractor graphics,
                                               int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        WidgetBlockingSync.beforeOverlay(info);
    }
}
