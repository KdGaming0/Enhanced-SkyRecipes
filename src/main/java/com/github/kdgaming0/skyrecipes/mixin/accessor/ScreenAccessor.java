package com.github.kdgaming0.skyrecipes.mixin.accessor;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Screen#removeWidget} for use outside the screen hierarchy.
 *
 * <p>Used by {@code QuickNavRecipeViewMixin} to strip Skyblocker QuickNav buttons
 * from RRV's {@code RecipeViewScreen}.</p>
 */
@SuppressWarnings("JavadocReference")
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("removeWidget")
    void skyrecipes$removeWidget(GuiEventListener listener);
}
