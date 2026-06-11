package com.github.kdgaming0.skyrecipes.mixin.accessor;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes ItemViewOverlay#updateQuery so the item list can be force-refreshed
 * after a new searchIndex is published (e.g. on warm-start or background reload).
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public interface ItemViewOverlayAccessor {

    @Invoker("updateQuery")
    void skyrecipes$updateQuery(String query);
}