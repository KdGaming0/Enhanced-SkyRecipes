package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.client.util.RRVClientUtil;
import cc.cassian.rrv.common.config.Configs;
import cc.cassian.rrv.common.config.options.SidePanel;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelContents;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.recipe.stackgroup.StackGroupManager;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.util.LatestTaskRunner;
import com.github.kdgaming0.skyrecipes.core.util.SkyRecipesExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Compute replacements privately and publish only the newest completed sidebar on the client. */
@Mixin(value = SidePanelOverlay.class, remap = false)
public abstract class SidePanelRefreshMixin extends AbstractRrvItemListOverlay {
    @Unique private LatestTaskRunner<Refresh, Result> skyrecipes$refresh;
    @Unique private boolean skyrecipes$rebuildCraftables;

    protected SidePanelRefreshMixin() { super(-1, -1, -1, -1); }

    @Shadow
    private static boolean isRecipeViewScreen(Screen screen) { throw new AssertionError(); }

    @Inject(method = "updateSidePanelIndex", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$requestRefresh(SidePanelOverlay.Reason reason, CallbackInfo ci) {
        Minecraft.getInstance().execute(() -> skyrecipes$submitRefresh(reason));
        ci.cancel();
    }

    @Inject(method = "onScreenChanged", at = @At("RETURN"))
    private void skyrecipes$rewrapRetainedItems(CallbackInfo ci) {
        // Retained contents still need the new screen's geometry before its first frame.
        updateSlots();
    }

    @Unique
    private void skyrecipes$submitRefresh(SidePanelOverlay.Reason reason) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = RRVClientUtil.currentScreen();
        if (isRecipeViewScreen(screen) && reason == SidePanelOverlay.Reason.SCREEN_CHANGE) return;
        if (skyrecipes$refresh == null) {
            skyrecipes$refresh = new LatestTaskRunner<>(SkyRecipesExecutors.worker(), client::execute,
                    request -> new Result(request, List.copyOf(StackGroupManager.expandGroupsInList(
                            request.panel().getStacks(request.contents())))),
                    result -> {
                        Refresh request = result.request();
                        if (request.screen() != RRVClientUtil.currentScreen()
                                || request.panel() != Configs.CLIENT_SETTINGS.getSidePanel()
                                || !request.query().equals(ItemViewOverlay.INSTANCE.getCurrentQuery())) return;
                        skyrecipes$rebuildCraftables = false;
                        availableItems = result.items();
                        updateSlots();
                    }, failure -> SkyRecipes.LOGGER.error("RRV side panel refresh failed", failure));
        }
        // A search can supersede an inventory refresh. Keep the full recomputation until
        // it is published, otherwise RRV's SEARCH shortcut may reuse an older inventory.
        if (reason != SidePanelOverlay.Reason.SEARCH) skyrecipes$rebuildCraftables = true;
        SidePanelOverlay.Reason effectiveReason = skyrecipes$rebuildCraftables
                && reason == SidePanelOverlay.Reason.SEARCH ? SidePanelOverlay.Reason.OTHER : reason;
        skyrecipes$refresh.submit(new Refresh(screen, Configs.CLIENT_SETTINGS.getSidePanel(),
                ItemViewOverlay.INSTANCE.getCurrentQuery(),
                new SidePanelContents(effectiveReason, client.player, screen instanceof CreativeModeInventoryScreen)));
    }

    @Unique
    private record Refresh(Screen screen, SidePanel panel, String query, SidePanelContents contents) { }

    @Unique
    private record Result(Refresh request, List<ItemStack> items) { }
}
