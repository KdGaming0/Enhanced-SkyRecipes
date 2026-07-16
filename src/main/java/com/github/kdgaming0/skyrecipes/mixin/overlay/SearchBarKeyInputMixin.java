package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionState;
import com.github.kdgaming0.skyrecipes.mixin.accessor.EditBoxAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts keys aimed at a focused RRV search box, at the outermost point available.
 *
 * <p>This wraps the same {@code Screen.keyPressed} call site inside {@code KeyboardHandler.keyPress}
 * that Fabric's own {@code KeyboardHandlerMixin} wraps, at a priority above Fabric's default (1000)
 * so MixinExtras composes this handler as the <em>outermost</em> wrapper. That position is what makes
 * both behaviours below possible: skipping {@code original.call} skips Fabric's entire screen-key
 * dispatch ({@code allowKeyPress}, {@code beforeKeyPress}, {@code afterKeyPress}) along with RRV's own
 * {@code AbstractContainerScreen.keyPressed} injector.
 *
 * <p>Because RRV's injector is skipped along with everything else, any key this class claims must be
 * forwarded to the focused box by hand, mirroring what RRV does. Only editing keys (backspace, arrows,
 * Ctrl+A) travel this path; text insertion happens on the separate {@code charTyped} route and is
 * untouched.
 *
 * <h2>Tab accepts the autocomplete completion</h2>
 * When the search bar shows an autocomplete completion as ghost text, Tab appends it instead of
 * cycling focus; with no completion showing, Tab cycles focus as usual. Calculator ghost text is
 * deliberately not acceptable — see {@link SearchSuggestionState}.
 *
 * <h2>Other mods' keybinds do not fire while typing</h2>
 * RRV already tries to claim these keys: its injector feeds the key to the focused {@link EditBox}
 * and returns {@code true} to mark it handled. That only stops callers that respect the return value
 * of {@code keyPressed}, and mods hooking Fabric's screen-key events do not. Fabric dispatches like so:
 *
 * <pre>
 *   if (!allowKeyPress(screen).invoker().allowKeyPress(screen, event))
 *       return true;                                 // runs before RRV ever sees the key
 *   beforeKeyPress(screen).invoker()...;
 *   boolean result = operation.call(screen, event);  // RRV's suppression happens in here
 *   afterKeyPress(screen).invoker()...;              // fires regardless of result
 *   return result;
 * </pre>
 *
 * <p>Both routes bypass RRV in opposite directions, so no return value from inside
 * {@code Screen.keyPressed} can stop them; claiming the key out here does. This covers Skyblocker's
 * Estimated Value Breakdown ("i", via {@code afterKeyPress}) and anything else built on these events.
 *
 * <p><b>Known gap:</b> mods that read keys straight from {@code KeyboardHandler.keyPress} rather than
 * through screen dispatch are still out of reach, because they run upstream of this call site —
 * SkyHanni's {@code MixinKeyboard} injects at {@code HEAD} and posts to its own event bus, and
 * SkyOcean's {@code KeyboardHandlerMixin} injects at the earlier {@code screen != null} check. Neither
 * consults text-field focus at all, so they fire over any focused text box (Skyblocker's own Ctrl+F
 * search, the anvil rename field, ...), not just RRV's. That is an upstream gap in those mods rather
 * than something to fix from here; preempting them would mean cancelling {@code keyPress} wholesale
 * and desynchronising SkyHanni's key up/down bookkeeping.
 *
 * <p><b>TODO — the keybind guard can go once RRV suppresses these keys upstream.</b> It duplicates the
 * typing handling in RRV's {@code MixinAbstractContainerScreen}, so it will drift if RRV changes it.
 * The Tab behaviour above is ours and stays regardless.
 */
@Mixin(value = KeyboardHandler.class, priority = 1500)
public class SearchBarKeyInputMixin {

    @WrapOperation(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean skyrecipes$onScreenKeyPressed(Screen screen, KeyEvent event, Operation<Boolean> original) {
        if (screen == null) {
            return original.call(screen, event);
        }

        if (event.isCycleFocus() && skyrecipes$acceptCompletion(screen)) {
            return true;
        }

        return skyrecipes$blockKeybindsWhileTyping(screen, event, original);
    }

    /**
     * Appends the ghost-text completion to the search bar, if one is showing.
     *
     * @return true if a completion was accepted and the key should be consumed
     */
    private boolean skyrecipes$acceptCompletion(Screen screen) {
        if (!(screen.getFocused() instanceof SearchBar bar)) {
            return false;
        }

        String completion = SearchSuggestionState.getCompletion();
        if (completion == null || completion.isEmpty()) {
            return false;
        }

        // The bar can be rebuilt or cleared independently of the recorded state, so only act on a
        // completion that is still the ghost text actually on screen.
        if (!completion.equals(((EditBoxAccessor) bar).skyrecipes$getSuggestion())) {
            return false;
        }

        // setValue moves the cursor to the end and fires RRV's responder, which re-runs the query
        // and recomputes the ghost text.
        bar.setValue(bar.getValue() + completion);
        return true;
    }

    private boolean skyrecipes$blockKeybindsWhileTyping(Screen screen, KeyEvent event, Operation<Boolean> original) {
        if (!SkyRecipesConfig.blockKeybindsWhileTyping) {
            return original.call(screen, event);
        }

        // Let the user always unfocus the box or close the screen.
        if (event.isEscape() || event.isCycleFocus()) {
            return original.call(screen, event);
        }

        // isTextWidgetFocused() dereferences currentInfo() without a null check, and RRV only ever
        // calls it from inside a container screen where it is set. This runs for every key on every
        // screen, so both checks below are load-bearing: currentInfo() is never cleared when a screen
        // closes, so it goes stale and would otherwise report focus belonging to a previous screen.
        OverlayManager overlays = OverlayManager.INSTANCE;
        if (overlays.currentInfo() == null || overlays.currentInfo().screen() != screen) {
            return original.call(screen, event);
        }

        if (!overlays.isTextWidgetFocused() || !(screen.getFocused() instanceof EditBox box)) {
            return original.call(screen, event);
        }

        box.keyPressed(event);
        return true;
    }
}
