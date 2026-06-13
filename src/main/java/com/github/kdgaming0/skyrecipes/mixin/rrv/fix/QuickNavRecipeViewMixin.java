package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.mixin.accessor.ScreenAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Hides Skyblocker's QuickNav buttons on RRV's {@link RecipeViewScreen}.
 *
 * <p>Skyblocker adds QuickNav widgets via {@code Screen.addWidget()} and renders them
 * through custom {@code extractBackground}/{@code extractRenderStateWithTooltipAndSubtitles}
 * hooks that iterate a private {@code quickNavButtons} list. Simply removing widgets from
 * {@code Screen.children} makes them unclickable but leaves them visible because Skyblocker's
 * render hooks bypass the standard {@code renderables} list.</p>
 *
 * <p>This mixin runs after Skyblocker (priority 1100) and:</p>
 * <ol>
 *   <li>Removes QuickNav widgets from {@code Screen.children} so they aren't interactive.</li>
 *   <li>Nulls Skyblocker's {@code quickNavButtons} field so its render hooks draw nothing.</li>
 * </ol>
 *
 * <p><b>TODO — Remove this mixin once Skyblocker or RRV handles QuickNav visibility
 * on recipe screens upstream.</b></p>
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1100)
public class QuickNavRecipeViewMixin {

    @Unique
    private static void removeQuickNavWidgets(RecipeViewScreen screen) {
        ScreenAccessor accessor = (ScreenAccessor) screen;
        List.copyOf(screen.children()).stream()
                .filter(w -> w.getClass().getName().startsWith("de.hysky.skyblocker.skyblock.quicknav.QuickNav"))
                .forEach(accessor::skyrecipes$removeWidget);
    }

    @Unique
    private static void nullQuickNavButtons(Object screen) {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("quickNavButtons");
            field.setAccessible(true);
            field.set(screen, null);
        } catch (NoSuchFieldException e) {
            // Field may have been renamed by @Unique — scan for it
            scanAndNullQuickNavList(screen);
        } catch (IllegalAccessException e) {
            SkyRecipes.LOGGER.warn("Failed to clear Skyblocker QuickNav buttons", e);
        }
    }

    @Unique
    private static void scanAndNullQuickNavList(Object screen) {
        try {
            for (Field field : AbstractContainerScreen.class.getDeclaredFields()) {
                if (field.getType() != List.class) continue;
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof List<?> list && !list.isEmpty()
                        && list.getFirst().getClass().getName()
                        .startsWith("de.hysky.skyblocker.skyblock.quicknav.QuickNav")) {
                    field.set(screen, null);
                    return;
                }
            }
        } catch (IllegalAccessException e) {
            SkyRecipes.LOGGER.warn("Failed to clear Skyblocker QuickNav buttons", e);
        }
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void skyrecipes$removeQuickNavFromRecipeView(CallbackInfo ci) {
        if (!FabricLoader.getInstance().isModLoaded("rrv")) return;
        if (!((Object) this instanceof RecipeViewScreen screen)) return;

        removeQuickNavWidgets(screen);
        nullQuickNavButtons(this);
    }
}
