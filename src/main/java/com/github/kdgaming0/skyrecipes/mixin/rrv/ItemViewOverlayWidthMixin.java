package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvOverlayAccessor;
import com.github.kdgaming0.skyrecipes.rrv.OverlayWidthHelper;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shrinks the RRV item-list overlay width according to
 * {@link SkyRecipesConfig#rrvItemListWidthPercent}.
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class ItemViewOverlayWidthMixin {

    @Inject(
            method = "initForScreen",
            at = @At("TAIL"),
            remap = false)
    private void skyrecipes$applyWidthPercent(Screen screen, AbstractRrvOverlay.InventoryPositionInfo invInfo, CallbackInfo ci) {
        OverlayWidthHelper.applyWidthPercent(
                (AbstractRrvOverlayAccessor) this,
                (AbstractRrvItemListOverlayAccessor) this,
                invInfo,
                SkyRecipesConfig.rrvItemListWidthPercent,
                true);
    }
}
