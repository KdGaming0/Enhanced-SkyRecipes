package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides side-panel visibility behaviour for SkyRecipes config options.
 */
@Mixin(value = SidePanelOverlay.class, remap = false)
public class SidePanelOverlayMixin {

    /**
     * Hides the bookmark panel when it contains no items.
     * Implemented visually (override {@code isEnabled}) rather than mutating RRV config.
     */
    @Inject(method = "isEnabled", at = @At("RETURN"), cancellable = true, remap = false)
    private void skyrecipes$hideEmptyBookmarkPanel(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()
                && SkyRecipesConfig.hideEmptyBookmarkPanel
                && SidePanelOverlay.showBookmarks()
                && ((SidePanelOverlay) (Object) this).availableItems().isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
