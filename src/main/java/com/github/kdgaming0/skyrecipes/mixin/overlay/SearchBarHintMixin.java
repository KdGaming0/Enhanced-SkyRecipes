package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.skyrecipes.rrv.plugin.SkyRecipesClientPlugin;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The search hint follows SkyRecipes readiness, independently of RRV's sidebar/button updates. */
@Mixin(EditBox.class)
public class SearchBarHintMixin {
    @Unique private static final Component SKYRECIPES$SEARCH = Component.translatable("rrv.search_hint");
    @Unique private static final Component SKYRECIPES$INDEXING = Component.translatable("rrv.indexing");

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void skyrecipes$refreshHint(CallbackInfo ci) {
        if ((Object) this instanceof SearchBar searchbar) {
            // A failed load has its own panel notice. Do not claim it is still indexing.
            boolean waiting = SkyRecipesClientPlugin.getSearchIndex() == null
                    && !SkyRecipesClientPlugin.isPipelineFailed();
            searchbar.setHint(waiting ? SKYRECIPES$INDEXING : SKYRECIPES$SEARCH);
        }
    }
}
