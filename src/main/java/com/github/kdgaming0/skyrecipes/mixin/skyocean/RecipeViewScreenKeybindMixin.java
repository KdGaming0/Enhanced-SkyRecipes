package com.github.kdgaming0.skyrecipes.mixin.skyocean;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.compat.skyocean.SkyOceanItemListCompat;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/** Routes SkyOcean's hovered-item keybinds through RRV's central recipe slots. */
@Mixin(value = RecipeViewScreen.class, remap = false)
public class RecipeViewScreenKeybindMixin {

    @Unique
    private static boolean skyrecipes$broken;

    @Unique
    private static boolean skyrecipes$workstationFieldResolved;

    @Unique
    private static Field skyrecipes$workstationField;

    /**
     * Runs only when RRV and focused widgets declined the key. The mixin plugin verifies the
     * target descriptors and optional API classes before this mixin is applied; the runtime
     * guard is the final fallback for changes that cannot be detected from class names alone.
     */
    @Inject(method = "keyPressed", at = @At("RETURN"), require = 0, remap = false)
    private void skyrecipes$skyOceanKeybind(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (skyrecipes$broken || cir.getReturnValueZ()) {
            return;
        }

        try {
            RecipeViewScreen screen = (RecipeViewScreen) (Object) this;
            ItemStack stack = screen.rrv$hoveredStack();
            if (stack == null || stack.isEmpty()) {
                stack = skyrecipes$hoveredWorkstationStack(screen);
            }
            SkyOceanItemListCompat.postHoveredItem(screen, stack, event);
        } catch (Throwable t) {
            skyrecipes$broken = true;
            SkyRecipes.LOGGER.warn(
                    "RRV recipe-screen integration for SkyOcean disabled (RRV API changed?); "
                            + "other features remain available.", t);
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
                // The footer slot is optional; central and overlay items still work.
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
