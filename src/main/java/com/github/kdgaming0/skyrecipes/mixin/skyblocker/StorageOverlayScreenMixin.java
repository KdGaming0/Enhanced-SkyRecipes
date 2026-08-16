package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.rrv.overlay.WidgetBlockingSync;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Registers Skyblocker's storage overlay and its action buttons as separate RRV
 * blocking regions. The target and its exclusion-zone methods are resolved
 * without Skyblocker types so older versions can skip this integration safely.
 */
@Mixin(targets = "de.hysky.skyblocker.skyblock.storageoverlay.StorageOverlayScreen", remap = false)
public class StorageOverlayScreenMixin {

    @Unique
    private static final Identifier SKYRECIPES$STORAGE_MAIN_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_storage_main");
    @Unique
    private static final Identifier SKYRECIPES$STORAGE_BUTTONS_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_storage_buttons");

    @Unique
    private static boolean skyrecipes$broken = false;

    @Unique
    private boolean skyrecipes$blockingInstalled = false;

    @Inject(method = "init", at = @At("TAIL"), require = 0, remap = false)
    private void skyrecipes$onInit(CallbackInfo ci) {
        if (skyrecipes$broken || skyrecipes$blockingInstalled) return;
        try {
            Class<?> targetClass = ((Object) this).getClass();
            Method mainExclusionZone = targetClass.getMethod("getMainExclusionZone");
            Method buttonsExclusionZone = targetClass.getMethod("getButtonsExclusionZone");
            if (!Rect2i.class.isAssignableFrom(mainExclusionZone.getReturnType())
                    || !Rect2i.class.isAssignableFrom(buttonsExclusionZone.getReturnType())) {
                throw new NoSuchMethodException("Storage overlay exclusion zones no longer return Rect2i");
            }

            Screen screen = (Screen) (Object) this;
            WidgetBlockingSync.install(screen, SKYRECIPES$STORAGE_MAIN_ID,
                    () -> skyrecipes$invokeZone(mainExclusionZone));
            WidgetBlockingSync.install(screen, SKYRECIPES$STORAGE_BUTTONS_ID,
                    () -> skyrecipes$invokeZone(buttonsExclusionZone));
            skyrecipes$blockingInstalled = true;
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Unique
    private Rect2i skyrecipes$invokeZone(Method method) {
        if (skyrecipes$broken) return null;
        try {
            Object result = method.invoke(this);
            if (result instanceof Rect2i bounds) {
                return bounds;
            }
            throw new IllegalStateException("Storage overlay exclusion zone returned "
                    + (result == null ? "null" : result.getClass().getName()));
        } catch (Throwable t) {
            skyrecipes$disable(t);
            return null;
        }
    }

    @Unique
    private static void skyrecipes$disable(Throwable t) {
        if (skyrecipes$broken) return;
        skyrecipes$broken = true;
        SkyRecipes.LOGGER.warn(
                "Skyblocker storage overlay blocking integration disabled (Skyblocker API changed?)", t);
    }
}
