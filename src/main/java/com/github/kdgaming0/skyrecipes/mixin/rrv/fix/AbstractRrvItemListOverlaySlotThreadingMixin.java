package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces RRV's item-list slot rebuild to run on the render thread.
 *
 * <p>RRV 8.4.0 moved the craftables side-panel computation off-thread
 * ({@code SidePanelOverlay.updateSidePanelIndex} now runs inside
 * {@code Util.backgroundExecutor().execute(...)}). That worker-thread path ends
 * in {@code updateSlots()}, which mutates two collections that the render thread
 * also touches:</p>
 * <ul>
 *   <li>{@code OverlayManager.guiBlockings}, read by {@code isPositionBlocked()}
 *       while the render thread mutates it via {@code setGuiBlocking}/
 *       {@code removeGuiBlocking}; and</li>
 *   <li>the overlay's {@code itemSlots} list, which the render thread iterates in
 *       {@code keyPressed}/{@code mouseClicked}/render.</li>
 * </ul>
 *
 * <p>Both are plain {@link java.util.ArrayList}s, so the concurrent access throws
 * {@link java.util.ConcurrentModificationException} (see RRV crash in
 * {@code AbstractRrvOverlay.isPositionBlocked}).</p>
 *
 * <p>Slot building is the cheap, render-only step that always ran on the main
 * thread before 8.4.0; only the expensive recipe scan/filter/sort stays off-thread
 * in RRV's lambda. Rescheduling just {@code updateSlots()} back to the render
 * thread therefore removes both data races without giving back the performance win.
 * Main-thread callers (scroll/page) pass the {@code isSameThread()} guard and run
 * inline, so behaviour is unchanged for them. As a bonus it also re-unifies the
 * {@code fittingPerPage}/{@code startIndex} fields {@code updateSlots} writes with
 * the {@code scrollMouse}/{@code prevPage}/{@code nextPage} callers that read them.</p>
 *
 * <p><b>Upstream:</b> this is an RRV 8.4.0 regression (changelog: "Craftables logic
 * has been moved off-thread"). The clean long-term fix is RRV making the off-thread
 * craftables path not touch render-thread collections; this mixin should be removed
 * once that lands upstream.</p>
 */
@Mixin(value = AbstractRrvItemListOverlay.class, remap = false)
public class AbstractRrvItemListOverlaySlotThreadingMixin {

    @Inject(method = "updateSlots", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$ensureSlotsBuiltOnMainThread(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(((AbstractRrvItemListOverlay) (Object) this)::updateSlots);
            ci.cancel();
        }
    }
}
