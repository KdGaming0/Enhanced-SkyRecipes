package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.github.kdgaming0.skyrecipes.rrv.util.SafeDummySlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Safety fixes for {@link AbstractContainerMenu} when RRV's {@link RecipeViewMenu} is active.
 *
 * <p>Contains two independent fixes:</p>
 * <ul>
 *   <li><b>Slot safety:</b> Returns a dummy slot for out-of-bounds indices during RRV's
 *       slot rebuilds, preventing {@code IndexOutOfBoundsException} from third-party mods.</li>
 *   <li><b>Packet suppression:</b> Silently drops {@code initializeContents} and {@code setItem}
 *       calls for the active container menu while RRV is open, avoiding crashes from mod
 *       event handlers that probe hardcoded slot indices.</li>
 * </ul>
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuFixesMixin {

    @Shadow
    public final NonNullList<Slot> slots = null;

    @Unique
    private static final Slot SKYRECIPES$SAFE_DUMMY_SLOT = new SafeDummySlot();

    // ── Slot safety ───────────────────────────────────────────────────────────

    @Inject(
            method = "getSlot(I)Lnet/minecraft/world/inventory/Slot;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skyrecipes$safeGetSlot(int index, CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof RecipeViewMenu
                && (index < 0 || index >= this.slots.size())) {
            cir.setReturnValue(SKYRECIPES$SAFE_DUMMY_SLOT);
        }
    }

    // ── Packet suppression ────────────────────────────────────────────────────

    @Inject(method = "initializeContents", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$suppressInitializeContentsWhenRrvOpen(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        if (skyrecipes$shouldSuppress((AbstractContainerMenu) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$suppressSetItemWhenRrvOpen(int slot, int stateId, ItemStack stack, CallbackInfo ci) {
        if (skyrecipes$shouldSuppress((AbstractContainerMenu) (Object) this)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean skyrecipes$shouldSuppress(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RecipeViewScreen && mc.player != null) {
            return menu == mc.player.containerMenu && menu.containerId != 0;
        }
        return false;
    }
}
