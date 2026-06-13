package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.OverlayDisplay;
import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryIconButton;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import com.github.kdgaming0.skyrecipes.mixin.accessor.AbstractRrvItemListOverlayAccessor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects category toggle buttons above the RRV search bar.
 *
 * <p>Buttons are rendered as 16×16 sprite icons. Clicking toggles the category
 * filter via {@link CategoryState} and refreshes the overlay.</p>
 *
 * <p>When the search bar is too narrow to fit all buttons in one row, they wrap
 * into multiple rows so they remain clickable.</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class CategoryButtonMixin {

    @Unique
    private static final Identifier skyrecipes$ROW_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "category_buttons_row");
    @Shadow
    private SearchBar searchbar;
    @Unique
    private List<CategoryIconButton> skyrecipes$categoryButtons;
    @Unique
    private String skyrecipes$previousQuery = "";
    @Unique
    private SkyblockItemCategory skyrecipes$previousCategory = null;

    @Inject(method = "placeWidgets", at = @At("TAIL"), remap = false)
    private void skyrecipes$addCategoryButtons(cc.cassian.rrv.common.overlay.AbstractRrvOverlay.ScreenContext ctx, CallbackInfo ci) {
        if (SkyRecipesConfig.hideCategoryButtons) return;
        if (searchbar == null) return;

        if (skyrecipes$categoryButtons == null) {
            skyrecipes$categoryButtons = new ArrayList<>();
        } else {
            skyrecipes$categoryButtons.clear();
        }

        List<SkyblockItemCategory> categories = SkyblockItemCategory.BUTTON_CATEGORIES;
        int btnSize = 16;
        int btnGap = 2;
        int count = categories.size();
        int totalWidth = count * btnSize + (count - 1) * btnGap;

        // Determine how many buttons fit per row based on search bar width
        int availableWidth = searchbar.getWidth();
        int buttonsPerRow;
        if (totalWidth <= availableWidth) {
            buttonsPerRow = count;
        } else {
            buttonsPerRow = Math.max(1, (availableWidth + btnGap) / (btnSize + btnGap));
        }

        int rows = (count + buttonsPerRow - 1) / buttonsPerRow;
        int rowWidth = buttonsPerRow * btnSize + (buttonsPerRow - 1) * btnGap;

        // Centre the grid horizontally on the search bar; left-align when wrapping
        int startX;
        if (totalWidth <= availableWidth) {
            startX = searchbar.getX() + (availableWidth - totalWidth) / 2;
        } else {
            startX = searchbar.getX();
        }

        // Start above the search bar; each additional row pushes us further up
        int firstRowY = searchbar.getY() - btnSize - 2 - (rows - 1) * (btnSize + btnGap);

        SkyblockItemCategory active = CategoryState.getButtonCategory();

        int idx = 0;
        for (SkyblockItemCategory category : categories) {
            String spriteName = category.getSpriteName();
            if (spriteName == null) continue;

            int col = idx % buttonsPerRow;
            int row = idx / buttonsPerRow;
            int x = startX + col * (btnSize + btnGap);
            int y = firstRowY + row * (btnSize + btnGap);

            CategoryIconButton btn = new CategoryIconButton(
                    x, y, btnSize,
                    spriteName,
                    category == active,
                    b -> skyrecipes$onToggle(category)
            );
            btn.setToggled(category == active);
            btn.setTooltipText(category.getDisplayName());
            skyrecipes$categoryButtons.add(btn);
            ctx.addRenderable(btn);
            idx++;
        }

        // Blocking component must cover the full button grid
        int actualTotalWidth = (totalWidth <= availableWidth) ? totalWidth : rowWidth;
        int blockHeight = rows * btnSize + (rows - 1) * btnGap + 2;
        BlockingGuiComponent rowBlocking =
                new BlockingGuiComponent(skyrecipes$ROW_ID, startX, firstRowY, actualTotalWidth, blockHeight);
        OverlayManager.INSTANCE.setGuiBlocking(rowBlocking);

        skyrecipes$updateCategoryButtonVisibility();
    }

    @Unique
    private void skyrecipes$onToggle(SkyblockItemCategory category) {
        CategoryState.toggle(category);
        // Reset pagination immediately so the user doesn't land on an empty page
        AbstractRrvItemListOverlay self = (AbstractRrvItemListOverlay) (Object) this;
        ((AbstractRrvItemListOverlayAccessor) self).skyrecipes$setStartIndex(0);
        OverlayManager.INSTANCE.updateOverlaysAndWidgets(true);
    }

    /**
     * Reset pagination to page 1 whenever the search query or category changes.
     */
    @Inject(method = "updateQuery", at = @At("HEAD"), remap = false)
    private void skyrecipes$captureOldQuery(String newQuery, CallbackInfo ci) {
        skyrecipes$previousQuery = ((ItemViewOverlay) (Object) this).getCurrentQuery();
        skyrecipes$previousCategory = CategoryState.getButtonCategory();
    }

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void skyrecipes$resetPagination(String newQuery, CallbackInfo ci) {
        boolean queryChanged = !newQuery.equals(skyrecipes$previousQuery);
        boolean categoryChanged = CategoryState.getButtonCategory() != skyrecipes$previousCategory;
        if (queryChanged || categoryChanged) {
            ((AbstractRrvItemListOverlayAccessor) this).skyrecipes$setStartIndex(0);
        }
    }

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void skyrecipes$onQueryChanged(String newQuery, CallbackInfo ci) {
        skyrecipes$updateCategoryButtonVisibility();
    }

    @Inject(method = "setEnabled", at = @At("TAIL"), remap = false)
    private void skyrecipes$onEnabledChanged(boolean enabled, CallbackInfo ci) {
        skyrecipes$updateCategoryButtonVisibility();
    }

    @Unique
    private void skyrecipes$updateCategoryButtonVisibility() {
        if (skyrecipes$categoryButtons == null || skyrecipes$categoryButtons.isEmpty()) return;

        ItemViewOverlay self = (ItemViewOverlay) (Object) this;
        OverlayDisplay rrvMode = Configs.CLIENT_SETTINGS.isShowItemView();

        boolean visible;
        if (SkyRecipesConfig.hideCategoryButtons) {
            visible = false;
        } else if (rrvMode == OverlayDisplay.WHEN_SEARCHING) {
            visible = !SkyRecipesConfig.hideCategoryButtonsWhenNotSearching || self.isSearching();
        } else {
            visible = rrvMode != OverlayDisplay.DISABLED;
        }

        for (CategoryIconButton btn : skyrecipes$categoryButtons) {
            btn.visible = visible;
        }
    }
}
