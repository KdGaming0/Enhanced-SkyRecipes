package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.SearchBarLimits;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvOverlayAccessor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Widens the RRV search bar when {@link SkyRecipesConfig#wideRrvSearchBar} is enabled.
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class SearchBarWidthMixin {

    @ModifyVariable(
            method = "createSearchbarElement",
            at = @At(value = "STORE"),
            remap = false,
            name = "boxWidth")
    private int skyrecipes$widenSearchBar(int boxWidth, AbstractRrvOverlay.InventoryPositionInfo info) {
        if (!SkyRecipesConfig.wideRrvSearchBar) return boxWidth;

        int minWidth = SkyRecipesConfig.rrvSearchBarWidth;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        if (Configs.CLIENT_SETTINGS.isCenterSearch()) {
            return Math.max(boxWidth, Math.min(minWidth, screenWidth - 20));
        }

        AbstractRrvOverlayAccessor overlay = (AbstractRrvOverlayAccessor) this;
        boolean wrapMode = Configs.CLIENT_SETTINGS.isItemWrapMode();

        int available = (wrapMode ? overlay.skyrecipes$getWidth() : overlay.skyrecipes$getEffectiveWidth()) - 4;
        int centerX = wrapMode
                ? overlay.skyrecipes$getX() + overlay.skyrecipes$getWidth() / 2
                : overlay.skyrecipes$getEffectiveX() + overlay.skyrecipes$getEffectiveWidth() / 2;

        int maxFromScreen = 2 * Math.min(centerX, screenWidth - centerX) - 4;
        int maxWidth = Math.min(minWidth, Math.min(available, Math.max(maxFromScreen, 0)));

        return Math.max(boxWidth, maxWidth);
    }

    @ModifyArg(
            method = "createSearchbarElement",
            at = @At(
                    value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/overlay/itemlist/view/SearchBar;setMaxLength(I)V"),
            index = 0,
            remap = false)
    private int skyrecipes$increaseMaxLength(int originalMaxLength) {
        return SearchBarLimits.MAX_INPUT_LENGTH;
    }
}
