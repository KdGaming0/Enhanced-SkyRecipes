package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Safety fixes for {@link AbstractContainerMenu} when RRV's {@link RecipeViewMenu} is active.
 *
 * <p>Contains two independent fixes:</p>
 * <ul>
 *   <li><b>Slot safety:</b> Returns a dummy slot for out-of-bounds indices during RRV's
 *       slot rebuilds, preventing {@code IndexOutOfBoundsException} from third-party mods.</li>
 *   <li><b>Packet suppression + replay:</b> Defers {@code initializeContents} and {@code setItem}
 *       calls for the active container menu while RRV is open, avoiding crashes from mod
 *       event handlers that probe hardcoded slot indices. The latest suppressed state is
 *       buffered and replayed once the recipe view closes, so the parent container is not
 *       left stale with a broken stateId chain.</li>
 * </ul>
 *
 * <p>All buffering/replay runs on the client main thread (packet application is rescheduled
 * there, and so is the end-of-tick hook), so plain static state is safe.</p>
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

    // ── Packet suppression + replay ───────────────────────────────────────────

    @Unique
    private static AbstractContainerMenu skyrecipes$bufferedMenu;
    @Unique
    private static List<ItemStack> skyrecipes$bufferedContents;
    @Unique
    private static ItemStack skyrecipes$bufferedCarried;
    @Unique
    private static final Map<Integer, ItemStack> skyrecipes$bufferedSlots = new HashMap<>();
    @Unique
    private static int skyrecipes$bufferedStateId;
    @Unique
    private static boolean skyrecipes$replayHookRegistered;

    @Inject(method = "initializeContents", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$suppressInitializeContentsWhenRrvOpen(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (skyrecipes$shouldSuppress(self)) {
            skyrecipes$bindBuffer(self);
            skyrecipes$bufferedContents = List.copyOf(items);
            skyrecipes$bufferedCarried = carried;
            // full snapshot supersedes earlier per-slot updates
            skyrecipes$bufferedSlots.clear();
            skyrecipes$bufferedStateId = stateId;
            ci.cancel();
        } else if (self == skyrecipes$bufferedMenu) {
            // a full snapshot makes any buffered state obsolete
            skyrecipes$clearBuffer();
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void skyrecipes$suppressSetItemWhenRrvOpen(int slot, int stateId, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (skyrecipes$shouldSuppress(self)) {
            skyrecipes$bindBuffer(self);
            skyrecipes$bufferedSlots.put(slot, stack);
            skyrecipes$bufferedStateId = stateId;
            ci.cancel();
        } else if (self == skyrecipes$bufferedMenu) {
            // live update arrived before the tick flush: replay older buffered state first
            skyrecipes$flushBuffer(Minecraft.getInstance());
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

    @Unique
    private static void skyrecipes$bindBuffer(AbstractContainerMenu menu) {
        if (skyrecipes$bufferedMenu != menu) {
            // menu was swapped while the recipe view stayed open; old state is dead
            skyrecipes$clearBuffer();
            skyrecipes$bufferedMenu = menu;
        }
        if (!skyrecipes$replayHookRegistered) {
            skyrecipes$replayHookRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (skyrecipes$bufferedMenu != null && !(mc.screen instanceof RecipeViewScreen)) {
                    skyrecipes$flushBuffer(mc);
                }
            });
        }
    }

    /**
     * Replays the buffered updates onto the menu they were captured for, via the real
     * {@code initializeContents}/{@code setItem} so the stateId chain matches the server
     * again. Drops the buffer if that menu is no longer the player's active container.
     */
    @Unique
    private static void skyrecipes$flushBuffer(Minecraft mc) {
        AbstractContainerMenu menu = skyrecipes$bufferedMenu;
        List<ItemStack> contents = skyrecipes$bufferedContents;
        ItemStack carried = skyrecipes$bufferedCarried;
        Map<Integer, ItemStack> slotUpdates = Map.copyOf(skyrecipes$bufferedSlots);
        int stateId = skyrecipes$bufferedStateId;
        // clear before replaying so re-entry into the injectors above is a no-op
        skyrecipes$clearBuffer();
        if (mc.player == null || mc.player.containerMenu != menu) {
            return;
        }
        if (contents != null) {
            menu.initializeContents(stateId, contents, carried);
        }
        slotUpdates.forEach((slot, stack) -> menu.setItem(slot, stateId, stack));
    }

    @Unique
    private static void skyrecipes$clearBuffer() {
        skyrecipes$bufferedMenu = null;
        skyrecipes$bufferedContents = null;
        skyrecipes$bufferedCarried = null;
        skyrecipes$bufferedSlots.clear();
        skyrecipes$bufferedStateId = 0;
    }
}
