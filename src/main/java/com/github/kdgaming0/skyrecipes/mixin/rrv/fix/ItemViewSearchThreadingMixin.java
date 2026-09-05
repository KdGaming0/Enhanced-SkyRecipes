package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.util.LatestTaskRunner;
import com.github.kdgaming0.skyrecipes.core.util.SkyRecipesExecutors;
import com.github.kdgaming0.skyrecipes.rrv.overlay.ItemViewSearch;
import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keeps RRV's query HEAD/TAIL hooks, replacing only its unsafe worker dispatch. */
@Mixin(value = ItemViewOverlay.class, remap = false)
public abstract class ItemViewSearchThreadingMixin extends AbstractRrvItemListOverlay {
    @Shadow private String currentQuery;
    @Unique private LatestTaskRunner<String, List<ItemStack>> skyrecipes$search;
    @Unique private boolean skyrecipes$refreshSidePanel;

    protected ItemViewSearchThreadingMixin() { super(-1, -1, -1, -1); }

    @Redirect(method = "updateQuery", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/TracingExecutor;execute(Ljava/lang/Runnable;)V"))
    private void skyrecipes$submitSearch(TracingExecutor executor, Runnable original, String query) {
        // The original method increments this before dispatch; our search leaves the last
        // complete UI usable while working and never mutates the slot lists from a worker.
        slotUpdaters--;
        Minecraft.getInstance().execute(() -> {
            if (!query.equals(currentQuery)) startIndex = 0;
            currentQuery = query;
            skyrecipes$refreshSidePanel = true;
            skyrecipes$requestSearch();
        });
    }

    @Inject(method = "updateDisplayedItems", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$refreshGrouping(CallbackInfo ci) {
        Minecraft.getInstance().execute(this::skyrecipes$requestSearch);
        ci.cancel();
    }

    @Unique
    private void skyrecipes$requestSearch() {
        if (skyrecipes$search == null) {
            skyrecipes$search = new LatestTaskRunner<>(SkyRecipesExecutors.worker(),
                    task -> Minecraft.getInstance().execute(task), ItemViewSearch::compute,
                    items -> {
                        availableItems = items;
                        updateSlots();
                        // Group expansion alone never refreshed the side panel in RRV.
                        // Retain this flag if a grouping request supersedes a pending search.
                        if (skyrecipes$refreshSidePanel) {
                            skyrecipes$refreshSidePanel = false;
                            SidePanelOverlay.INSTANCE.updateSidePanelIndex(SidePanelOverlay.Reason.SEARCH);
                        }
                        updateButtons();
                    }, failure -> SkyRecipes.LOGGER.error("RRV item search failed", failure));
        }
        skyrecipes$search.submit(currentQuery);
    }
}
