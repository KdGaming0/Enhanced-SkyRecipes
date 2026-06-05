package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the active item-category filter state for the RRV item list overlay.
 *
 * <p>Button-driven category selection is stored here. Search-bar-driven category
 * paths (e.g. {@code %ARMOR}) are parsed by {@link com.github.kdgaming0.skyrecipes.core.search.SearchQueryParser}
 * and do not touch this state.</p>
 */
public final class CategoryState {

    @Nullable
    private static SkyblockItemCategory buttonCategory = null;

    private CategoryState() {
    }

    @Nullable
    public static SkyblockItemCategory getButtonCategory() {
        return buttonCategory;
    }

    public static boolean hasButtonCategory() {
        return buttonCategory != null;
    }

    public static void toggle(SkyblockItemCategory category) {
        buttonCategory = (category == buttonCategory) ? null : category;
    }

    public static void clear() {
        buttonCategory = null;
    }
}
