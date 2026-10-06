package com.github.kdgaming0.skyrecipes.mixin.overlay;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.overlay.itemlist.AbstractRrvItemListOverlay;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Splits RRV's item-list slots into several GUI strata per frame.
 *
 * <p>Vanilla's {@code GuiRenderState.findAppropriateNode} compares every new element against
 * all earlier elements of the same stratum, so drawing a few hundred slots in one stratum is
 * quadratic. A profile showed that search at roughly a third of the render thread while the
 * item list was open. Later strata draw on top, which is the order RRV already draws slots in,
 * so starting a new one every few slots keeps the picture identical.</p>
 */
@Mixin(value = AbstractRrvItemListOverlay.class, remap = false)
public class ItemListStrataMixin {

    @Unique
    private static final int SLOTS_PER_STRATUM = 16;

    /** Only touched from the render thread, inside one {@code extractSlots} call at a time. */
    @Unique
    private static int skyrecipes$slotsInStratum;

    @Inject(
            method = "extractSlots(Ljava/util/List;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private static void skyrecipes$resetStratumCount(List<ItemSlot> slots, GuiGraphicsExtractor graphics,
                                                     int mouseX, int mouseY, float partialTicks,
                                                     CallbackInfo ci) {
        skyrecipes$slotsInStratum = 0;
    }

    @WrapOperation(
            method = "extractSlots(Ljava/util/List;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lcc/cassian/rrv/common/overlay/ItemSlot;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
            require = 0
    )
    private static void skyrecipes$stratumPerSlotGroup(ItemSlot slot, GuiGraphicsExtractor graphics,
                                                       int mouseX, int mouseY, float partialTicks,
                                                       Operation<Void> original) {
        if (SkyRecipesConfig.optimizeItemListRendering && ++skyrecipes$slotsInStratum > SLOTS_PER_STRATUM) {
            skyrecipes$slotsInStratum = 1;
            graphics.nextStratum();
        }
        original.call(slot, graphics, mouseX, mouseY, partialTicks);
    }
}
