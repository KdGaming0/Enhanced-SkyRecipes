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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps an RRV {@link BlockingGuiComponent} synchronized with a screen
 * element's bounds and visibility, so RRV's item list and side panel shrink
 * away from the element while it is shown.
 *
 * <p>Skyblocker's helper widgets and overlays can change position, size, and
 * visibility after initialization, so the rectangle is re-synced from a
 * per-screen tick event and only pushed to RRV when it actually changed.
 * Changes are batched once per screen tick into a full overlay re-layout via
 * {@link OverlayManager#updateOverlaysAndWidgets}, because
 * {@code setExclusionArea} alone only queues a lighter widget update that does
 * not re-wrap the item list mid-screen.</p>
 *
 * <p>The tick handler self-disables on any throw, so a Skyblocker or RRV API
 * change degrades this feature silently instead of crashing the screen.</p>
 */
public final class WidgetBlockingSync {

    private static boolean broken = false;
    private static final Map<Screen, Map<Identifier, WidgetBlockingSync>> SCREENS = new WeakHashMap<>();

    static {
        // Fabric replaces per-screen events on every init/resize. Drop old registrations
        // and regions before the new widgets install their single shared tick callback.
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> clearScreen(screen));
    }

    private static void clearScreen(Screen screen) {
        Map<Identifier, WidgetBlockingSync> regions = SCREENS.remove(screen);
        if (regions == null) return;
        for (Identifier id : regions.keySet()) {
            OverlayManager.INSTANCE.removeExclusionArea(id, false);
        }
        regions.clear();
    }

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
        Map<Identifier, WidgetBlockingSync> entries = SCREENS.get(screen);
        if (entries == null) {
            entries = new LinkedHashMap<>();
            SCREENS.put(screen, entries);
            Map<Identifier, WidgetBlockingSync> regions = entries;
            ScreenEvents.afterTick(screen).register(_ -> {
                boolean changed = false;
                for (WidgetBlockingSync region : regions.values()) changed |= region.sync();
                if (changed) OverlayManager.INSTANCE.updateOverlaysAndWidgets(true);
            });
            ScreenEvents.remove(screen).register(_ -> {
                // The incoming screen will rebuild its overlays. Do not launch searches
                // for the screen being torn down, once per removed region.
                clearScreen(screen);
            });
        }
        // Screen init can recreate widgets on resize. Replace their old tick observers.
        WidgetBlockingSync previous = entries.put(sync.id, sync);
        if (previous != null) {
            sync.blockingSet = previous.blockingSet;
            sync.lastX = previous.lastX;
            sync.lastY = previous.lastY;
            sync.lastWidth = previous.lastWidth;
            sync.lastHeight = previous.lastHeight;
        }
    }

    private boolean sync() {
        if (broken) return false;
        try {
            if (widget != null) {
                if (!widget.visible) {
                    return clearBlocking();
                }
                return syncBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            }

            Rect2i bounds = boundsSupplier.get();
            if (bounds == null) {
                return clearBlocking();
            }
            return syncBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        } catch (Throwable t) {
            broken = true;
            SkyRecipes.LOGGER.warn(
                    "Skyblocker widget blocking integration disabled (Skyblocker API changed?)", t);
            return false;
        }
    }

    private boolean syncBounds(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return clearBlocking();
        }
        if (blockingSet
                && x == lastX && y == lastY
                && width == lastWidth && height == lastHeight) {
            return false;
        }
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;
        OverlayManager.INSTANCE.setExclusionArea(new BlockingGuiComponent(
                id, lastX, lastY, lastWidth, lastHeight));
        blockingSet = true;
        return true;
    }

    private boolean clearBlocking() {
        if (!blockingSet) return false;
        OverlayManager.INSTANCE.removeExclusionArea(id, false);
        blockingSet = false;
        return true;
    }
}
