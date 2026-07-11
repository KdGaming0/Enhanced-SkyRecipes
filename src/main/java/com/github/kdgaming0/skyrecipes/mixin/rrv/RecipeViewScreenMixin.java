package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import com.github.kdgaming0.skyrecipes.rrv.recipe.AbstractSkyblockClientRecipe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides RRV's "Share Recipe" button on every recipe.
 * Sharing does not function on Hypixel (no server-side recipe sync),
 * so SkyRecipes replaces it with a wiki button where a wiki URL is available.
 *
 * <p>Also injects {@link AbstractSkyblockClientRecipe#renderOverlay} after slot items
 * have been rendered, so count text can draw on top of item sprites.</p>
 */
@Mixin(RecipeViewScreen.class)
public class RecipeViewScreenMixin {

    /**
     * RRV consumes mouse-wheel events anywhere over the recipe GUI to flip recipe
     * pages, so scrollable recipe widgets (e.g. the reforge rarity table) never
     * receive them through vanilla routing. Give a hovered, actually-scrollable
     * widget first refusal; when its content fits, fall through to page flipping.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$scrollHoveredWidget(double mouseX, double mouseY, double scrollX, double scrollY,
                                                CallbackInfoReturnable<Boolean> cir) {
        RecipeViewScreen screen = (RecipeViewScreen) (Object) this;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractScrollArea area && area.isMouseOver(mouseX, mouseY)) {
                if (area.maxScrollAmount() > 0 && area.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    cir.setReturnValue(true);
                }
                return;
            }
        }
    }

    @Inject(method = "checkGui", at = @At("RETURN"))
    private void hideShareButtons(CallbackInfo ci) {
        for (Button btn : ((RecipeViewScreen) (Object) this).shareButtons) {
            btn.visible = false;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void skyrecipes$renderOverlays(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        RecipeViewScreen screen = (RecipeViewScreen) (Object) this;
        RecipeViewMenu menu = screen.getMenu();
        int guiLeft = screen.getLeftPos() + menu.guiOffsetLeft();

        for (int i = 0; i < menu.getCurrentDisplay().size(); i++) {
            ReliableClientRecipe recipe = menu.getCurrentDisplay().get(i);
            if (recipe instanceof AbstractSkyblockClientRecipe skyRecipe) {
                int guiTop = screen.getTopPos() + menu.guiOffsetTop(i);
                ReliableClientRecipe.RecipePosition pos = new ReliableClientRecipe.RecipePosition(
                        guiLeft, guiTop, recipe.getType().getDisplayWidth(), recipe.getType().getDisplayHeight());

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(guiLeft, guiTop);
                skyRecipe.renderOverlay(screen, pos, guiGraphics, mouseX - guiLeft, mouseY - guiTop, partialTicks);
                guiGraphics.pose().popMatrix();
            }
        }
    }
}
