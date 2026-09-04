package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelContents;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.rrv.overlay.SkyblockCraftablesIndex;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Feeds SkyBlock craftables into RRV's Craftables side panel.
 *
 * <p>RRV computes craftables from vanilla-item ingredient matching and excludes
 * {@code isVisualOnly()} recipes — which is every SkyRecipes recipe — so the panel
 * would only ever show vanilla results. {@link SidePanelContents#filter(List)} is called
 * exactly once per craftables recomputation, on RRV's background executor, with the
 * freshly built local list <i>after</i> RRV's own scan and <i>before</i> search
 * filtering and sorting. Injecting at its head lets the SkyBlock results share RRV's
 * query filtering, alphabetical sort, and slot publishing with zero extra passes.</p>
 */
@Mixin(value = SidePanelContents.class, remap = false)
public class SidePanelContentsCraftablesMixin {

    @Inject(method = "filter(Ljava/util/List;)V", at = @At("HEAD"))
    private static void skyrecipes$appendSkyblockCraftables(List<ItemStack> availableItems, CallbackInfo ci) {
        if (SidePanelOverlay.showCraftables() && SkyRecipes.isDataReady()) {
            SkyblockCraftablesIndex.appendCraftables(availableItems);
        }
    }
}
