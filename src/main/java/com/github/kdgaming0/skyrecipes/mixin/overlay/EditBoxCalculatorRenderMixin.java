package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorPanelRenderer;
import com.github.kdgaming0.skyrecipes.client.gui.CalculatorSessionOwner;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders calculator feedback whenever RRV's search widget itself is visible. */
@Mixin(EditBox.class)
public class EditBoxCalculatorRenderMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void skyrecipes$renderCalculatorPanel(GuiGraphicsExtractor guiGraphics,
                                                  int mouseX, int mouseY, float partialTicks,
                                                  CallbackInfo ci) {
        if (!((Object) this instanceof SearchBar searchbar)
                || !(ItemViewOverlay.INSTANCE instanceof CalculatorSessionOwner owner)) {
            return;
        }
        owner.skyrecipes$reconcileCalculatorConfig();
        CalculatorPanelRenderer.render(
                searchbar, owner.skyrecipes$getCalculatorSession(), guiGraphics, mouseX, mouseY);
    }
}
