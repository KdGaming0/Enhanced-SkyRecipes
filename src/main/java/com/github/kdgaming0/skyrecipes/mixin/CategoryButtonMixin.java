package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryIconButton;
import com.github.kdgaming0.skyrecipes.client.gui.CategoryState;
import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * <p>Buttons are rendered as 16×16 item icons. Clicking toggles the category
 * filter via {@link CategoryState} and refreshes the overlay.</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class CategoryButtonMixin {

    @Shadow private SearchBar searchbar;

    @Unique
    private final List<CategoryIconButton> skyrecipes$categoryButtons = new ArrayList<>();

    @Unique
    private String skyrecipes$previousQuery = "";

    @Inject(method = "placeWidgets", at = @At("TAIL"), remap = false)
    private void skyrecipes$addCategoryButtons(cc.cassian.rrv.common.overlay.AbstractRrvOverlay.ScreenContext ctx, CallbackInfo ci) {
        if (!SkyRecipesConfig.searchCategoryButtonsVisible) return;
        if (searchbar == null) return;

        skyrecipes$categoryButtons.clear();

        List<SkyblockItemCategory> categories = SkyblockItemCategory.BUTTON_CATEGORIES;
        int btnSize = 18;
        int btnGap = 2;
        int count = categories.size();
        int totalWidth = count * btnSize + (count - 1) * btnGap;
        int startX = searchbar.getX() + (searchbar.getWidth() - totalWidth) / 2;
        int btnY = searchbar.getY() - btnSize - 2;

        SkyblockItemCategory active = CategoryState.getButtonCategory();

        int x = startX;
        for (SkyblockItemCategory category : categories) {
            ItemStack icon = resolveIcon(category);
            CategoryIconButton btn = new CategoryIconButton(
                x, btnY, category, icon,
                category == active,
                () -> skyrecipes$onToggle(category)
            );
            btn.setToggled(category == active);
            skyrecipes$categoryButtons.add(btn);
            ctx.addRenderable(btn);
            x += btnSize + btnGap;
        }
    }

    @Unique
    private void skyrecipes$onToggle(SkyblockItemCategory category) {
        CategoryState.toggle(category);
        OverlayManager.INSTANCE.updateOverlaysAndWidgets(true);
    }

    /**
     * Reset pagination to page 1 whenever the search query changes.
     */
    @Inject(method = "updateQuery", at = @At("HEAD"), remap = false)
    private void skyrecipes$captureOldQuery(String newQuery, CallbackInfo ci) {
        skyrecipes$previousQuery = ((ItemViewOverlay) (Object) this).getCurrentQuery();
    }

    @Inject(method = "updateQuery", at = @At("TAIL"), remap = false)
    private void skyrecipes$resetPagination(String newQuery, CallbackInfo ci) {
        if (!newQuery.equals(skyrecipes$previousQuery)) {
            ((AbstractRrvItemListOverlayAccessor) (Object) this).skyrecipes$setStartIndex(0);
        }
    }

    @Unique
    private static ItemStack resolveIcon(SkyblockItemCategory category) {
        String itemId = category.getIconItemId();
        if (itemId == null || itemId.isEmpty()) {
            return Items.BARRIER.getDefaultInstance();
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return Items.BARRIER.getDefaultInstance();
        }
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER);
        return item.getDefaultInstance();
    }
}
