package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryIconButton;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
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
        if (!SkyRecipesConfig.searchCategoryButtonsVisible) return;
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
        int startX = searchbar.getX() + (searchbar.getWidth() - totalWidth) / 2;
        int btnY = searchbar.getY() - btnSize - 2;

        SkyblockItemCategory active = CategoryState.getButtonCategory();

        int x = startX;
        for (SkyblockItemCategory category : categories) {
            String spriteName = category.getSpriteName();
            if (spriteName == null) continue;
            CategoryIconButton btn = new CategoryIconButton(
                    x, btnY, btnSize,
                    spriteName,
                    category == active,
                    b -> skyrecipes$onToggle(category)
            );
            btn.setToggled(category == active);
            btn.setTooltipText(category.getDisplayName());
            skyrecipes$categoryButtons.add(btn);
            ctx.addRenderable(btn);
            x += btnSize + btnGap;
        }

        // Register blocking component so RRV avoids rendering over the button row
        BlockingGuiComponent rowBlocking =
                new BlockingGuiComponent(skyrecipes$ROW_ID, startX, btnY, totalWidth, btnSize + 2);
        OverlayManager.INSTANCE.setGuiBlocking(rowBlocking);
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
            ((AbstractRrvItemListOverlayAccessor) (Object) this).skyrecipes$setStartIndex(0);
        }
    }
}
