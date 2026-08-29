package com.github.kdgaming0.skyrecipes.compat.skyocean;

import cc.cassian.rrv.api.overlay.OverlayView;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Fail-soft bridge from RRV's hovered items to SkyOcean's item-list event API. */
public final class SkyOceanItemListCompat {

    private static boolean registered;
    private static boolean disabled;
    private static EventApi eventApi;

    private SkyOceanItemListCompat() {
    }

    /**
     * Registers RRV's item-list, bookmark, and craftables slots once.
     *
     * <p>The caller loads this class only when SkyOcean is present. Every optional API call is
     * still guarded because a future SkyOcean, Meowdding Lib, SkyBlock API, or RRV update may
     * change after Fabric has already established that the mod ids themselves are installed.</p>
     */
    public static void registerOverlayHandler() {
        if (registered || disabled) {
            return;
        }

        try {
            // Validate every reflected SkyOcean-side entry point before installing a callback.
            // If an update moves one of them, the bridge is disabled during initialization.
            resolveEventApi();
            OverlayView.registerGlobalOverlayKeybindSlotHandler((event, slot, overlay) -> {
                if (disabled) {
                    return false;
                }

                try {
                    Screen screen = Minecraft.getInstance().screen;
                    if (screen != null) {
                        postHoveredItem(screen, slot.getStack(), event);
                    }
                } catch (Throwable t) {
                    disable("RRV overlay API changed?", t);
                }

                // SkyOcean's event is intentionally non-cancellable. Preserve RRV's own
                // recipe/usage keybind handling after giving SkyOcean the hovered stack.
                return false;
            });
            registered = true;
        } catch (Throwable t) {
            disable("RRV overlay registration API changed?", t);
        }
    }

    /** Posts the same generic event Meowdding Lib uses for its other item-list integrations. */
    public static void postHoveredItem(Screen screen, ItemStack stack, KeyEvent event) {
        if (disabled || screen == null || stack == null || stack.isEmpty()) {
            return;
        }

        try {
            EventApi api = resolveEventApi();
            Object hoveredItemEvent = api.constructor().newInstance(screen, stack, event);
            Object eventBus = api.eventBusGetter().invoke(null);
            api.post().invoke(hoveredItemEvent, eventBus);
        } catch (Throwable t) {
            disable("SkyOcean item-list event API changed?", t);
        }
    }

    private static EventApi resolveEventApi() throws ReflectiveOperationException {
        EventApi resolved = eventApi;
        if (resolved != null) {
            return resolved;
        }

        ClassLoader loader = SkyOceanItemListCompat.class.getClassLoader();
        Class<?> eventClass = Class.forName(
                "me.owdding.lib.events.ItemListEvent$HoveredItemKeyPress", false, loader);
        Class<?> eventBusClass = Class.forName(
                "tech.thatgravyboat.skyblockapi.api.events.base.EventBus", false, loader);
        Class<?> skyBlockApiClass = Class.forName(
                "tech.thatgravyboat.skyblockapi.api.SkyBlockAPI", false, loader);

        Constructor<?> constructor = eventClass.getConstructor(
                Screen.class, ItemStack.class, KeyEvent.class);
        Method eventBusGetter = skyBlockApiClass.getMethod("getEventBus");
        Method post = eventClass.getMethod("post", eventBusClass);

        resolved = new EventApi(constructor, eventBusGetter, post);
        eventApi = resolved;
        return resolved;
    }

    private static void disable(String reason, Throwable t) {
        if (disabled) {
            return;
        }
        disabled = true;
        SkyRecipes.LOGGER.warn(
                "RRV hovered-item integration for SkyOcean disabled ({}); other features remain available.",
                reason, t);
    }

    private record EventApi(Constructor<?> constructor, Method eventBusGetter, Method post) {
    }
}
