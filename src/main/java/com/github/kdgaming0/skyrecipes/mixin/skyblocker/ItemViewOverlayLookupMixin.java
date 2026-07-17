package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.overlay.ItemSlot;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import com.mojang.datafixers.util.Either;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.item.ItemPrice;
import de.hysky.skyblocker.skyblock.item.wikilookup.WikiLookupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes Skyblocker's hovered-item lookup keybinds to RRV item-list entries.
 *
 * <p>Skyblocker's wiki and price (AH/BZ) lookups live in its
 * {@code AbstractContainerScreenMixin#skyblocker$keyPressed}, which reads the
 * vanilla {@code hoveredSlot} field. RRV's item list is not made of vanilla menu
 * {@link net.minecraft.world.inventory.Slot}s — each entry is an {@link ItemSlot}
 * rendered as an overlay — so {@code hoveredSlot} is {@code null} over the list and
 * Skyblocker's handler bails via its {@code if (hoveredSlot == null) return} guard.</p>
 *
 * <p>This mixin taps RRV's own overlay key path ({@link ItemViewOverlay#keyPressed})
 * where the hovered {@link ItemSlot} is available, and forwards the hovered stack to
 * Skyblocker's {@link ItemStack}-based lookup entry points. When a lookup fires the
 * event is consumed, so the empty {@code hoveredSlot} path never double-runs (and it
 * is a clean no-op regardless of mixin apply order thanks to Skyblocker's null guard).</p>
 *
 * <p>Only applied when Skyblocker is loaded (see {@code SkyRecipesMixinPlugin}).</p>
 */
@Mixin(value = ItemViewOverlay.class, remap = false)
public class ItemViewOverlayLookupMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrecipes$skyblockerLookupKeybinds(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack stack = skyrecipes$hoveredStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // Wiki lookup: handleWikiLookup checks its own keybinds (official + independent)
        // and the enable flag, returning true only when it actually opened the wiki.
        if (WikiLookupManager.handleWikiLookup(Either.right(stack), player, event)) {
            cir.setReturnValue(true);
            return;
        }

        // Price (AH/BZ) lookup: the keybind + enable check live in Skyblocker's screen
        // mixin, not in itemPriceLookup, so replicate them here.
        if (SkyblockerConfigManager.get().helpers.itemPrice.enableItemPriceLookup
                && ItemPrice.ITEM_PRICE_LOOKUP.matches(event)) {
            ItemPrice.itemPriceLookup(player, stack);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private ItemStack skyrecipes$hoveredStack() {
        for (ItemSlot slot : ((ItemViewOverlay) (Object) this).itemSlots()) {
            if (slot.isHovered()) {
                return slot.getStack();
            }
        }
        return null;
    }
}
