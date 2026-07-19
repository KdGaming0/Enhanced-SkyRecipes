package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import de.hysky.skyblocker.skyblock.garden.GardenPlotsWidget;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Skyblocker's GardenPlotsWidget as an RRV blocking GUI component.
 *
 * <p>RRV's item list and side panel shrink away from any rectangle registered
 * in {@link OverlayManager}. This mixin keeps such a rectangle synchronized
 * with the widget's bounds and removes it when the inventory screen closes.</p>
 *
 * <p>All injects use {@code require = 0} and handler bodies self-disable on
 * any throw, so a future Skyblocker API change degrades this feature silently
 * instead of crashing the garden plots screen.</p>
 */
@Mixin(GardenPlotsWidget.class)
public class GardenPlotsWidgetMixin {

    @Unique
    private static final Identifier SKYRECIPES$GARDEN_PLOTS_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_garden_plots");

    @Unique
    private static boolean skyrecipes$broken = false;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void skyrecipes$onInit(CallbackInfo ci) {
        if (skyrecipes$broken) return;
        try {
            GardenPlotsWidget self = (GardenPlotsWidget) (Object) this;
            skyrecipes$updateBlocking(self);

            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                ScreenEvents.remove(screen).register(_ ->
                        OverlayManager.INSTANCE.removeGuiBlocking(SKYRECIPES$GARDEN_PLOTS_ID, true)
                );
            }
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Inject(method = "setX", at = @At("TAIL"), require = 0)
    private void skyrecipes$onSetX(int x, CallbackInfo ci) {
        if (skyrecipes$broken) return;
        try {
            skyrecipes$updateBlocking((GardenPlotsWidget) (Object) this);
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Inject(method = "setY", at = @At("TAIL"), require = 0)
    private void skyrecipes$onSetY(int y, CallbackInfo ci) {
        if (skyrecipes$broken) return;
        try {
            skyrecipes$updateBlocking((GardenPlotsWidget) (Object) this);
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Unique
    private void skyrecipes$updateBlocking(GardenPlotsWidget widget) {
        OverlayManager.INSTANCE.setGuiBlocking(new BlockingGuiComponent(
                SKYRECIPES$GARDEN_PLOTS_ID,
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight()
        ));
    }

    @Unique
    private static void skyrecipes$disable(Throwable t) {
        skyrecipes$broken = true;
        SkyRecipes.LOGGER.warn(
                "Skyblocker garden plots integration disabled (Skyblocker API changed?)", t);
    }
}
