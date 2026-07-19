package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.client.ReliableRecipeViewerClient;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import de.hysky.skyblocker.utils.hoveredItem.HoveredItemStackProvider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the RRV recipe/usage keybinds work on items shown in Skyblocker helper
 * widgets attached to container screens — currently the accessory bag helper.
 * Those widgets are not made of vanilla menu slots, so RRV's own
 * {@code hoveredSlot}-based key handling never sees their items.
 *
 * <p>Skyblocker exposes the hovered item of such widgets through its public
 * {@link HoveredItemStackProvider} interface ({@code getFocusedItem()} returns
 * the hovered entry's stack), which this mixin reads from the screen's direct
 * children. Mouse clicks are intentionally left alone: Skyblocker binds its
 * own click actions (e.g. wiki links) to these widgets.</p>
 *
 * <p>Keys are handled at {@code keyPressed} RETURN so anything else (RRV
 * overlay, search fields, Skyblocker's wiki/price lookups) takes precedence.
 * The inject uses {@code require = 0} and the handler self-disables on any
 * throw, so a future Skyblocker API change degrades this feature silently
 * instead of crashing the screen.</p>
 */
@Mixin(AbstractContainerScreen.class)
public class HelperWidgetLookupMixin {

    @Unique
    private static boolean skyrecipes$broken = false;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyrecipes$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Only act when everything else declined the key (RRV cancels at HEAD
        // when its search is focused; Skyblocker's lookups return true).
        if (skyrecipes$broken || cir.getReturnValueZ()) return;
        try {
            AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
            // A focused text field consumes chars via charTyped, not keyPressed,
            // so keyPressed can return false while the user is typing.
            if (self.getFocused() instanceof EditBox) return;

            ActionType action = null;
            if (ReliableRecipeViewerClient.RECIPE_KEYBIND.matches(event)) {
                action = ActionType.RESULT;
            } else if (ReliableRecipeViewerClient.USAGE_KEYBIND.matches(event)) {
                action = ActionType.INPUT;
            }
            if (action == null) return;

            ItemStack stack = skyrecipes$hoveredProviderStack(self);
            if (stack == null) return;
            ItemViewOverlay.INSTANCE.openRecipeView(stack, action);
            cir.setReturnValue(true);
        } catch (Throwable t) {
            skyrecipes$broken = true;
            SkyRecipes.LOGGER.warn(
                    "Skyblocker helper widget lookup integration disabled (Skyblocker API changed?)", t);
        }
    }

    @Unique
    private static ItemStack skyrecipes$hoveredProviderStack(AbstractContainerScreen<?> screen) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof HoveredItemStackProvider provider)) continue;
            if (child instanceof AbstractWidget widget && !widget.visible) continue;
            ItemStack stack = provider.getFocusedItem();
            if (stack != null && !stack.isEmpty()) return stack;
        }
        return null;
    }
}
