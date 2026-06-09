package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Suppresses server container packet processing for {@code player.containerMenu}
 * while a {@link RecipeViewScreen} is open.
 *
 * <p>RRV does not set {@code player.containerMenu} to its own menu; it leaves the
 * parent's menu as the active container. Server packets intended for the parent
 * menu are still processed while RRV is open. Third-party mod event handlers
 * that fire on every {@code Slot.set()} may then access hardcoded slot indices on
 * the parent menu and crash if the parent has fewer slots than expected.
 *
 * <p>This mixin silently drops {@code initializeContents} and {@code setItem}
 * calls for the active container menu when RRV is on screen. Inventory updates
 * ({@code containerId == 0}) are never suppressed.
 */
@Mixin(AbstractContainerMenu.class)
public class RecipeViewMenuPacketSuppressMixin {

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
            // Only suppress the currently-active container menu (not the inventory)
            return menu == mc.player.containerMenu && menu.containerId != 0;
        }
        return false;
    }
}
