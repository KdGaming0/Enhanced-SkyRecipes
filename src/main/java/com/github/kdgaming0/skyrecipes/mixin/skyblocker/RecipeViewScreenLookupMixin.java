package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.compat.skyblocker.SkyblockerLookupHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Routes Skyblocker's hovered-item lookup keybinds through RRV's recipe screen.
 *
 * <p>RRV's recipe screen is a plain {@code Screen}, so Skyblocker's
 * {@code AbstractContainerScreen} mixin cannot see its central recipe slots.</p>
 *
 * <p>The RETURN injection preserves RRV and focused-widget precedence. All optional
 * API access is guarded: a future RRV or Skyblocker change disables only this bridge
 * and logs once instead of crashing the screen.</p>
 */
@Mixin(value = RecipeViewScreen.class, remap = false)
public class RecipeViewScreenLookupMixin {

    @Unique
    private static boolean skyrecipes$broken;

    @Unique
    private static boolean skyrecipes$workstationFieldResolved;

    @Unique
    private static Field skyrecipes$workstationField;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void skyrecipes$skyblockerLookupKeybinds(KeyEvent event,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (skyrecipes$broken || cir.getReturnValueZ()) {
            return;
        }

        try {
            RecipeViewScreen screen = (RecipeViewScreen) (Object) this;
            ItemStack stack = screen.rrv$hoveredStack();
            if (stack == null || stack.isEmpty()) {
                stack = skyrecipes$hoveredWorkstationStack(screen);
            }
            if (SkyblockerLookupHandler.handle(stack, event)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            skyrecipes$broken = true;
            SkyRecipes.LOGGER.warn(
                    "RRV recipe-screen lookup integration disabled (RRV API changed?)", t);
        }
    }

    @Unique
    private static ItemStack skyrecipes$hoveredWorkstationStack(RecipeViewScreen screen) {
        if (!skyrecipes$workstationFieldResolved) {
            skyrecipes$workstationFieldResolved = true;
            try {
                Field field = RecipeViewScreen.class.getDeclaredField("workstationSlot");
                if (field.trySetAccessible()) {
                    skyrecipes$workstationField = field;
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // The footer slot is optional; central and side-panel lookups still work.
            }
        }

        Field field = skyrecipes$workstationField;
        if (field == null) {
            return ItemStack.EMPTY;
        }

        try {
            Object value = field.get(screen);
            if (value instanceof ItemSlot slot && slot.isHovered()) {
                return slot.getStack();
            }
        } catch (IllegalAccessException ignored) {
            skyrecipes$workstationField = null;
        }
        return ItemStack.EMPTY;
    }

}
