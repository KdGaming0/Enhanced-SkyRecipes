package com.github.kdgaming0.skyrecipes.mixin.accessor;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("suggestion")
    String skyrecipes$getSuggestion();

    @Accessor("displayPos")
    int skyrecipes$getDisplayPos();

    @Accessor("highlightPos")
    int skyrecipes$getHighlightPos();

    @Accessor("textX")
    int skyrecipes$getTextX();

    @Accessor("textY")
    int skyrecipes$getTextY();
}
