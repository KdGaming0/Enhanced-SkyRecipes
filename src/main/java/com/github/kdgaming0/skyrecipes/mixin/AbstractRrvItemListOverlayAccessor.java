package com.github.kdgaming0.skyrecipes.mixin;

import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(AbstractRrvItemListOverlay.class)
public interface AbstractRrvItemListOverlayAccessor {

    @Accessor("availableItems")
    List<ItemStack> skyrecipes$getAvailableItems();

    @Accessor("startIndex")
    void skyrecipes$setStartIndex(int value);

    @Accessor("itemStartX")
    void skyrecipes$setItemStartX(int value);

    @Accessor("itemEndX")
    void skyrecipes$setItemEndX(int value);
}
