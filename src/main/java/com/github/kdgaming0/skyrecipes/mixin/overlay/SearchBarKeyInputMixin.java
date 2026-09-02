package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorPanelRenderer;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorResultFormatter;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSession;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSessionOwner;
import com.github.kdgaming0.skyrecipes.client.gui.SearchBarLimits;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionController;
import com.github.kdgaming0.skyrecipes.client.gui.SearchSuggestionState;
import com.github.kdgaming0.skyrecipes.mixin.accessor.EditBoxAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.math.BigDecimal;

/**
 * Intercepts keys aimed at a focused RRV search box at the outermost screen-key
 * dispatch point. This keeps autocomplete/calculator actions deterministic and
 * prevents other mods' screen-key callbacks from firing while the user types.
 *
 * <p>The priority and current-screen guards are load-bearing. Fabric dispatches
 * callbacks around {@code Screen.keyPressed}; composing outside that wrapper is
 * what allows a claimed key to skip both callback phases.</p>
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

        boolean plainCompletionKey = (event.isCycleFocus() || event.isRight())
                && !event.hasShiftDown() && !event.hasControlDownWithQuirk() && !event.hasAltDown();
        if (plainCompletionKey && skyrecipes$acceptCompletion(screen)) {
            return true;
        }
        if (skyrecipes$handleCalculatorKey(screen, event)) {
            return true;
        }

        return skyrecipes$blockKeybindsWhileTyping(screen, event, original);
    }

    private boolean skyrecipes$acceptCompletion(Screen screen) {
        if (!(screen.getFocused() instanceof SearchBar bar)) {
            return false;
        }
        EditBoxAccessor accessor = (EditBoxAccessor) bar;
        if (!CalculatorPanelRenderer.caretAtEndWithoutSelection(
                bar.getValue().length(), bar.getCursorPosition(), accessor.skyrecipes$getHighlightPos())) {
            return false;
        }

        SearchSuggestionState.Completion completion = SearchSuggestionState.getCompletion(bar);
        if (completion == null || completion.suffix().isEmpty()
                || !bar.getValue().equals(completion.input())
                || bar.getValue().length() + completion.suffix().length() > SearchBarLimits.MAX_INPUT_LENGTH) {
            return false;
        }
        if (!completion.suffix().equals(accessor.skyrecipes$getSuggestion())) {
            return false;
        }

        bar.setValue(bar.getValue() + completion.suffix());
        return true;
    }

    private boolean skyrecipes$handleCalculatorKey(Screen screen, KeyEvent event) {
        if (!(screen.getFocused() instanceof SearchBar bar)
                || !(ItemViewOverlay.INSTANCE instanceof CalculatorSessionOwner owner)) {
            return false;
        }
        CalculatorSession session = owner.skyrecipes$getCalculatorSession();
        if (!session.isActive()) {
            return false;
        }

        if (event.isEscape()) {
            if (SkyRecipesConfig.calculatorEscapeClosesMenu) {
                return false;
            }
            String restore = session.exitAndRestoreQuery();
            SearchSuggestionController.clear(bar);
            bar.setValue(restore);
            return true;
        }

        if (event.isUp()) {
            String history = session.historyUp(bar.getValue());
            if (history != null) {
                bar.setValue(history);
            }
            return true;
        }
        if (event.isDown()) {
            String history = session.historyDown();
            if (history != null) {
                bar.setValue(history);
            }
            return true;
        }

        BigDecimal result = session.successfulResult();
        if (event.isCycleFocus() && !event.hasShiftDown()
                && !event.hasControlDownWithQuirk() && !event.hasAltDown()) {
            if (result == null) {
                return false;
            }
            session.commitSuccessfulResult(SkyRecipesConfig.calculatorHistorySize);
            String exact = CalculatorResultFormatter.exact(result);
            String replacement = "=" + exact;
            if (replacement.length() > SearchBarLimits.MAX_INPUT_LENGTH) {
                skyrecipes$copyResult(exact, session);
            } else {
                bar.setValue(replacement);
            }
            return true;
        }

        if (event.isConfirmation()) {
            if (result == null) {
                return true;
            }
            session.commitSuccessfulResult(SkyRecipesConfig.calculatorHistorySize);
            String exact = CalculatorResultFormatter.exact(result);
            if (event.hasShiftDown()) {
                if (exact.length() > SearchBarLimits.MAX_INPUT_LENGTH) {
                    skyrecipes$copyResult(exact, session);
                } else {
                    session.exitForNormalQuery();
                    SearchSuggestionController.clear(bar);
                    bar.setValue(exact);
                }
            } else {
                skyrecipes$copyResult(exact, session);
            }
            return true;
        }

        if (event.isCopy() && CalculatorPanelRenderer.selectionIsCollapsed(
                bar.getValue().length(), bar.getCursorPosition(),
                ((EditBoxAccessor) bar).skyrecipes$getHighlightPos())) {
            if (result != null) {
                session.commitSuccessfulResult(SkyRecipesConfig.calculatorHistorySize);
                skyrecipes$copyResult(CalculatorResultFormatter.exact(result), session);
            }
            return true;
        }

        return false;
    }

    private static void skyrecipes$copyResult(String value, CalculatorSession session) {
        Minecraft.getInstance().keyboardHandler.setClipboard(value);
        session.markCopied();
    }

    private boolean skyrecipes$blockKeybindsWhileTyping(Screen screen, KeyEvent event, Operation<Boolean> original) {
        if (!SkyRecipesConfig.blockKeybindsWhileTyping) {
            return original.call(screen, event);
        }

        if (event.isEscape() || event.isCycleFocus()) {
            return original.call(screen, event);
        }

        // currentInfo is never cleared when a screen closes, so both checks are required before
        // isTextWidgetFocused(), which dereferences it without a null check.
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
