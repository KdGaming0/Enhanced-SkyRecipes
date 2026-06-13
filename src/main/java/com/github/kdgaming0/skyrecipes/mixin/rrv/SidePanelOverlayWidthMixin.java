package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvOverlayAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static cc.cassian.rrv.common.overlay.ItemSlot.ITEM_ENTRY_SIZE;

/**
 * Shrinks the RRV side-panel overlay width according to
 * {@link SkyRecipesConfig#rrvSidePanelWidthPercent}.
 */
@Mixin(value = SidePanelOverlay.class, remap = false)
public class SidePanelOverlayWidthMixin {

    @Unique
    private static final int MIN_WIDTH = ITEM_ENTRY_SIZE + 4;

    @Inject(
            method = "initForScreen",
            at = @At("TAIL"),
            remap = false)
    private void skyrecipes$applyWidthPercent(
            AbstractContainerScreen<? extends AbstractContainerMenu> screen,
            AbstractRrvOverlay.InventoryPositionInfo invInfo,
            CallbackInfo ci) {

        int percent = SkyRecipesConfig.rrvSidePanelWidthPercent;
        if (percent >= 100) return;

        AbstractRrvOverlayAccessor overlay = (AbstractRrvOverlayAccessor) this;
        AbstractRrvItemListOverlayAccessor itemList = (AbstractRrvItemListOverlayAccessor) this;

        int newWidth = (overlay.skyrecipes$getWidth() * percent) / 100;
        newWidth -= (newWidth - 4) % ITEM_ENTRY_SIZE;

        if (newWidth < MIN_WIDTH) {
            newWidth = MIN_WIDTH;
        }

        overlay.skyrecipes$setWidth(newWidth);

        if (Configs.CLIENT_SETTINGS.isRightIndex()) {
            overlay.skyrecipes$setX(0);
        } else {
            overlay.skyrecipes$setX(invInfo.screenWidth() - newWidth);
        }

        itemList.skyrecipes$setItemStartX(overlay.skyrecipes$getX() + 2);
        itemList.skyrecipes$setItemEndX(overlay.skyrecipes$getX() + newWidth - 2);
    }
}
