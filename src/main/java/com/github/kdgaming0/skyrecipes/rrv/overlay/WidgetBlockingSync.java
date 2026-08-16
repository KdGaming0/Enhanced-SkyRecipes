package com.github.kdgaming0.skyrecipes.rrv.overlay;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * Keeps an RRV {@link BlockingGuiComponent} synchronized with a screen
 * element's bounds and visibility, so RRV's item list and side panel shrink
 * away from the element while it is shown.
 *
 * <p>Skyblocker's helper widgets and overlays can change position, size, and
 * visibility after initialization, so the rectangle is re-synced from a
 * per-screen tick event and only pushed to RRV when it actually changed.
 * Changes force a full overlay re-layout via
 * {@link OverlayManager#updateOverlaysAndWidgets}, because
 * {@code setGuiBlocking} alone only queues a lighter widget update that does
 * not re-wrap the item list mid-screen.</p>
 *
 * <p>The tick handler self-disables on any throw, so a Skyblocker or RRV API
 * change degrades this feature silently instead of crashing the screen.</p>
 */
public final class WidgetBlockingSync {

    private static boolean broken = false;

    private final AbstractWidget widget;
    private final Supplier<Rect2i> boundsSupplier;
    private final Identifier id;
    private boolean blockingSet = false;
    private int lastX, lastY, lastWidth, lastHeight;

    private WidgetBlockingSync(AbstractWidget widget, Identifier id) {
        this.widget = widget;
        this.boundsSupplier = null;
        this.id = id;
    }

    private WidgetBlockingSync(Supplier<Rect2i> boundsSupplier, Identifier id) {
        this.widget = null;
        this.boundsSupplier = boundsSupplier;
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
        install(screen, new WidgetBlockingSync(widget, id));
    }

    /**
     * Installs synchronization for a non-widget screen region. Returning
     * {@code null} from the supplier temporarily removes its blocking region.
     */
    public static void install(Screen screen, Identifier id, Supplier<Rect2i> boundsSupplier) {
        if (broken) return;
        install(screen, new WidgetBlockingSync(boundsSupplier, id));
    }

    private static void install(Screen screen, WidgetBlockingSync sync) {
        ScreenEvents.afterTick(screen).register(_ -> sync.sync());
        ScreenEvents.remove(screen).register(_ ->
                OverlayManager.INSTANCE.removeGuiBlocking(sync.id, true)
        );
    }

    private void sync() {
        if (broken) return;
        try {
            if (widget != null) {
                if (!widget.visible) {
                    clearBlocking();
                    return;
                }
                syncBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
                return;
            }

            Rect2i bounds = boundsSupplier.get();
            if (bounds == null) {
                clearBlocking();
                return;
            }
            syncBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        } catch (Throwable t) {
            broken = true;
            SkyRecipes.LOGGER.warn(
                    "Skyblocker widget blocking integration disabled (Skyblocker API changed?)", t);
        }
    }

    private void syncBounds(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            clearBlocking();
            return;
        }
        if (blockingSet
                && x == lastX && y == lastY
                && width == lastWidth && height == lastHeight) {
            return;
        }
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;
        OverlayManager.INSTANCE.setGuiBlocking(new BlockingGuiComponent(
                id, lastX, lastY, lastWidth, lastHeight));
        OverlayManager.INSTANCE.updateOverlaysAndWidgets(true);
        blockingSet = true;
    }

    private void clearBlocking() {
        if (!blockingSet) return;
        OverlayManager.INSTANCE.removeGuiBlocking(id, true);
        blockingSet = false;
    }
}
