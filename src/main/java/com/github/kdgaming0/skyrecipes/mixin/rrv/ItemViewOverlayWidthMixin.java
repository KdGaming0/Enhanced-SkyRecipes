package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.config.instances.ClientConfig;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvOverlayAccessor;
import com.github.kdgaming0.skyrecipes.rrv.OverlayWidthHelper;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shrinks the RRV item-list overlay width according to
 * {@link SkyRecipesConfig#rrvItemListWidthPercent}.
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class ItemViewOverlayWidthMixin {

    /**
     * SkyRecipes sizes this overlay from the space beside the inventory. RRV's
     * item-count cap has to be disabled before the percentage adjustment at
     * {@code initForScreen} tail runs, otherwise that adjustment only sees the
     * capped width.
     */
    @Redirect(
            method = "initForScreen",
            at = @At(value = "INVOKE", target = "Lcc/cassian/rrv/common/config/instances/ClientConfig;getItemViewPanelMaxWidth()I"),
            require = 2,
            remap = false)
    private int skyrecipes$useAvailableScreenWidth(ClientConfig config) {
        return 0; // RRV's "Auto" value: do not impose an item-column cap.
    }

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
