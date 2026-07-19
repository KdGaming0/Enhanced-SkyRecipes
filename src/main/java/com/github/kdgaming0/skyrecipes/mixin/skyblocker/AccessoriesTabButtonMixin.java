package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.rrv.overlay.WidgetBlockingSync;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the tab button of Skyblocker's accessory bag helper as an RRV
 * blocking GUI component (kept in sync by {@link WidgetBlockingSync}). The
 * button sits beside the container even while the helper panel is closed, so
 * without its own rectangle RRV's item list renders on top of it.
 *
 * <p>The inject uses {@code require = 0} and the handler self-disables on any
 * throw, so a future Skyblocker API change degrades this feature silently
 * instead of crashing the accessory bag screen.</p>
 */
@Mixin(targets = "de.hysky.skyblocker.skyblock.accessories.AccessoriesHelperWidget$TabButton", remap = false)
public class AccessoriesTabButtonMixin {

    @Unique
    private static final Identifier SKYRECIPES$ACCESSORIES_TAB_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_accessories_tab");

    @Unique
    private static boolean skyrecipes$broken = false;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void skyrecipes$onInit(CallbackInfo ci) {
        if (skyrecipes$broken) return;
        try {
            WidgetBlockingSync.install((AbstractWidget) (Object) this, SKYRECIPES$ACCESSORIES_TAB_ID);
        } catch (Throwable t) {
            skyrecipes$broken = true;
            SkyRecipes.LOGGER.warn(
                    "Skyblocker accessory tab blocking integration disabled (Skyblocker API changed?)", t);
        }
    }
}
