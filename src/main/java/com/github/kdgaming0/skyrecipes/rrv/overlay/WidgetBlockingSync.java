package com.github.kdgaming0.skyrecipes.rrv.overlay;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * Keeps an RRV {@link BlockingGuiComponent} synchronized with a screen
 * widget's bounds and visibility, so RRV's item list and side panel shrink
 * away from the widget while it is shown.
 *
 * <p>Skyblocker's helper widgets change position and visibility after
 * construction (tab toggles, screen shifts), so instead of injecting into
 * remap-sensitive overrides the rectangle is re-synced from a per-screen tick
 * event and only pushed to RRV when it actually changed. Changes force a full
 * overlay re-layout via {@link OverlayManager#updateOverlaysAndWidgets},
 * because {@code setGuiBlocking} alone only queues a lighter widget update
 * that does not re-wrap the item list mid-screen.</p>
 *
 * <p>The tick handler self-disables on any throw, so a Skyblocker or RRV API
 * change degrades this feature silently instead of crashing the screen.</p>
 */
public final class WidgetBlockingSync {

    private static boolean broken = false;

    private final AbstractWidget widget;
    private final Identifier id;
    private boolean blockingSet = false;
    private int lastX, lastY, lastWidth, lastHeight;

    private WidgetBlockingSync(AbstractWidget widget, Identifier id) {
        this.widget = widget;
        this.id = id;
    }

    /**
     * Installs the sync on the screen currently being initialized. The
     * blocking rectangle is removed when the screen closes; Fabric drops the
     * tick handler with the screen instance.
     */
    public static void install(AbstractWidget widget, Identifier id) {
        if (broken) return;
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        WidgetBlockingSync sync = new WidgetBlockingSync(widget, id);
        ScreenEvents.afterTick(screen).register(_ -> sync.sync());
        ScreenEvents.remove(screen).register(_ ->
                OverlayManager.INSTANCE.removeGuiBlocking(id, true)
        );
    }

    private void sync() {
        if (broken) return;
        try {
            if (!widget.visible || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
                if (blockingSet) {
                    OverlayManager.INSTANCE.removeGuiBlocking(id, true);
                    blockingSet = false;
                }
                return;
            }
            if (blockingSet
                    && widget.getX() == lastX && widget.getY() == lastY
                    && widget.getWidth() == lastWidth && widget.getHeight() == lastHeight) {
                return;
            }
            lastX = widget.getX();
            lastY = widget.getY();
            lastWidth = widget.getWidth();
            lastHeight = widget.getHeight();
            OverlayManager.INSTANCE.setGuiBlocking(new BlockingGuiComponent(
                    id, lastX, lastY, lastWidth, lastHeight));
            OverlayManager.INSTANCE.updateOverlaysAndWidgets(true);
            blockingSet = true;
        } catch (Throwable t) {
            broken = true;
            SkyRecipes.LOGGER.warn(
                    "Skyblocker widget blocking integration disabled (Skyblocker API changed?)", t);
        }
    }
}
