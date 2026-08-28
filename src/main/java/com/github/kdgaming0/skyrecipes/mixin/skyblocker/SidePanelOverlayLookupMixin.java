package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.overlay.itemlist.panel.SidePanelOverlay;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.compat.skyblocker.SkyblockerLookupHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes Skyblocker's hovered-item lookup keybinds through RRV's left-side
 * bookmarks/craftables panel on every screen where that overlay is active.
 */
@Mixin(value = SidePanelOverlay.class, remap = false)
public class SidePanelOverlayLookupMixin {

    @Unique
    private static boolean skyrecipes$broken;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void skyrecipes$skyblockerLookupKeybinds(KeyEvent event,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (skyrecipes$broken) {
            return;
        }

        try {
            if (SkyblockerLookupHandler.handle(skyrecipes$hoveredStack(), event)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            skyrecipes$broken = true;
            SkyRecipes.LOGGER.warn(
                    "RRV side-panel lookup integration disabled (RRV API changed?)", t);
        }
    }

    @Unique
    private ItemStack skyrecipes$hoveredStack() {
        for (ItemSlot slot : ((SidePanelOverlay) (Object) this).itemSlots()) {
            if (slot != null && slot.isHovered()) {
                return slot.getStack();
            }
        }
        return ItemStack.EMPTY;
    }
}
