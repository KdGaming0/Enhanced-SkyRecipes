package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.client.ReliableRecipeViewerClient;
import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import de.hysky.skyblocker.skyblock.museum.MuseumManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrates Skyblocker's museum overlay with RRV: registers the widget as a
 * blocking GUI component (so RRV's item list shrinks away from it) and lets
 * the RRV recipe/usage keybinds and mouse clicks on a hovered donation open
 * its recipe view.
 *
 * <p>All injects use {@code require = 0} and handler bodies self-disable on
 * any throw, so a future Skyblocker API change degrades this feature silently
 * instead of crashing the museum screen.</p>
 */
@Mixin(MuseumManager.class)
public class MuseumManagerMixin {

    @Unique
    private static final Identifier SKYRECIPES$MUSEUM_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_museum");

    @Unique
    private static boolean skyrecipes$broken = false;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void skyrecipes$onInit(CallbackInfo ci) {
        if (skyrecipes$broken) return;
        try {
            MuseumManager self = (MuseumManager) (Object) this;
            OverlayManager.INSTANCE.setGuiBlocking(new BlockingGuiComponent(
                    SKYRECIPES$MUSEUM_ID,
                    self.getX(),
                    self.getY(),
                    self.getWidth(),
                    self.getHeight()
            ));

            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                ScreenEvents.remove(screen).register(_ ->
                        OverlayManager.INSTANCE.removeGuiBlocking(SKYRECIPES$MUSEUM_ID, true)
                );
            }
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyrecipes$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Only act when Skyblocker declined the key (search box, wiki and
        // price lookup all take precedence via the original return value).
        if (skyrecipes$broken || cir.getReturnValueZ()) return;
        try {
            ActionType action = null;
            if (ReliableRecipeViewerClient.RECIPE_KEYBIND.matches(event)) {
                action = ActionType.RESULT;
            } else if (ReliableRecipeViewerClient.USAGE_KEYBIND.matches(event)) {
                action = ActionType.INPUT;
            }
            if (action != null && skyrecipes$openForFocused(action)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyrecipes$onMouseClicked(MouseButtonEvent event, boolean doubled,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (skyrecipes$broken || cir.getReturnValueZ()) return;
        try {
            ActionType action = switch (event.button()) {
                case 0 -> ActionType.RESULT;
                case 1 -> ActionType.INPUT;
                default -> null;
            };
            if (action != null && skyrecipes$openForFocused(action)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            skyrecipes$disable(t);
        }
    }

    @Unique
    private boolean skyrecipes$openForFocused(ActionType action) {
        ItemStack stack = ((MuseumManager) (Object) this).getFocusedItem();
        if (stack == null || stack.isEmpty()) return false;
        ItemViewOverlay.INSTANCE.openRecipeView(stack, action);
        return true;
    }

    @Unique
    private static void skyrecipes$disable(Throwable t) {
        skyrecipes$broken = true;
        SkyRecipes.LOGGER.warn(
                "Skyblocker museum integration disabled (Skyblocker API changed?)", t);
    }
}
