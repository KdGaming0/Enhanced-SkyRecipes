package com.github.kdgaming0.skyrecipes.mixin.accessor;

import cc.cassian.rrv.common.overlay.AbstractRrvOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes RRV overlay geometry and state fields for width/position adjustments.
 */
@Mixin(value = AbstractRrvOverlay.class, remap = false)
public interface AbstractRrvOverlayAccessor {

    @Accessor("x")
    int skyrecipes$getX();

    @Accessor("width")
    int skyrecipes$getWidth();

    @Accessor("effectiveX")
    int skyrecipes$getEffectiveX();

    @Accessor("effectiveWidth")
    int skyrecipes$getEffectiveWidth();

    @Accessor("enabled")
    boolean skyrecipes$isEnabledRaw();

    @Accessor("x")
    void skyrecipes$setX(int x);

    @Accessor("width")
    void skyrecipes$setWidth(int width);

    @Accessor("effectiveX")
    void skyrecipes$setEffectiveX(int effectiveX);

    @Accessor("effectiveWidth")
    void skyrecipes$setEffectiveWidth(int effectiveWidth);
}
