package com.github.kdgaming0.skyrecipes.rrv;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvOverlayAccessor;

import static cc.cassian.rrv.common.overlay.ItemSlot.ITEM_ENTRY_SIZE;

/**
 * Shared width-percent logic for the item-view and side-panel overlay width
 * mixins: shrinks an RRV item-list overlay, snapping the new width to whole
 * item columns and re-anchoring it to its screen edge.
 *
 * <p>Lives outside the mixin package because Mixin refuses to classload
 * non-mixin classes from a registered mixin package.</p>
 */
public final class OverlayWidthHelper {

    private static final int MIN_WIDTH = ITEM_ENTRY_SIZE + 4;

    private OverlayWidthHelper() {
    }

    /**
     * @param anchorRightWhenRightIndex whether the overlay sits at the right
     *                                  screen edge when RRV's index is on the
     *                                  right ({@code true} for the item list,
     *                                  {@code false} for the mirrored side panel)
     */
    public static void applyWidthPercent(AbstractRrvOverlayAccessor overlay,
                                         AbstractRrvItemListOverlayAccessor itemList,
                                         AbstractRrvOverlay.InventoryPositionInfo invInfo,
                                         int percent, boolean anchorRightWhenRightIndex) {
        if (percent >= 100) return;

        int newWidth = (overlay.skyrecipes$getWidth() * percent) / 100;
        newWidth -= (newWidth - 4) % ITEM_ENTRY_SIZE;

        if (newWidth < MIN_WIDTH) {
            newWidth = MIN_WIDTH;
        }

        overlay.skyrecipes$setWidth(newWidth);

        boolean atRightEdge = Configs.CLIENT_SETTINGS.isRightIndex() == anchorRightWhenRightIndex;
        overlay.skyrecipes$setX(atRightEdge ? invInfo.screenWidth() - newWidth : 0);

        itemList.skyrecipes$setItemStartX(overlay.skyrecipes$getX() + 2);
        itemList.skyrecipes$setItemEndX(overlay.skyrecipes$getX() + newWidth - 2);
    }
}
