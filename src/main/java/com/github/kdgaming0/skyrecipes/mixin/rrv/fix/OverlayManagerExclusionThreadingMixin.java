package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Makes RRV 8.10's exclusion-area collection safe for its new worker-thread slot layout.
 *
 * <p>RRV mutates this list from screen/render callbacks while
 * {@code AbstractRrvOverlay.isPositionBlocked} streams it on the background executor. A plain
 * {@code ArrayList} throws {@link java.util.ConcurrentModificationException}. Copy-on-write is
 * a good fit: layout reads are hot and lock-free, while exclusion areas are few and writes are
 * comparatively rare.</p>
 */
@Mixin(value = OverlayManager.class, remap = false)
public class OverlayManagerExclusionThreadingMixin {

    @Shadow
    @Final
    @Mutable
    private List<BlockingGuiComponent> exclusionAreas;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void skyrecipes$useThreadSafeExclusionAreas(CallbackInfo ci) {
        exclusionAreas = new CopyOnWriteArrayList<>(exclusionAreas);
    }
}
