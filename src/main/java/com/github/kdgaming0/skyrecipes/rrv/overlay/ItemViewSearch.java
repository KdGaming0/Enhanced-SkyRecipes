package com.github.kdgaming0.skyrecipes.rrv.overlay;

import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemFilters;
import cc.cassian.rrv.common.overlay.itemlist.view.PrefixedFilter;
import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** RRV 8.10 filtering/grouping, using job-owned lists instead of shared overlay fields. */
public final class ItemViewSearch {
    private ItemViewSearch() { }

    public static List<ItemStack> compute(String query) {
        List<ItemStack> items;
        if (query.contains(" ")) {
            String[] clauses = query.split(" ");
            List<String> ordinary = new ArrayList<>();
            for (String clause : clauses) {
                if (!PrefixedFilter.startsWithPrefix(clause)) ordinary.add(clause);
            }
            items = new ArrayList<>(ItemFilters.defaultFilter(String.join(" ", ordinary).strip()));
            for (String clause : clauses) ItemFilters.advancedFilter(items, clause);
        } else {
            items = new ArrayList<>(ItemFilters.filter(query));
        }
        items.removeIf(ItemView::isExcludedItem);
        if (Configs.STACK_GROUPS.areStackGroupsEnabled()) {
            boolean searching = !query.isEmpty();
            if (searching) items = StackGroupManager.appendMatchingGroups(query, items);
            items = StackGroupManager.applyGrouping(items, searching);
        }
        // Grouping may hand back a cached list. Never mutate an upstream-owned result.
        items = new ArrayList<>(items);
        items.removeIf(ItemView::isExcludedItem);
        return List.copyOf(items);
    }
}
