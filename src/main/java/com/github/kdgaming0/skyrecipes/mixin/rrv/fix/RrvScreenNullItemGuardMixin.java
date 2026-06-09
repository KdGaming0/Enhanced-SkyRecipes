package com.github.kdgaming0.skyrecipes.mixin.rrv.fix;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporary guard: prevents crashes when a null {@link ItemStack} is passed to
 * {@link GuiGraphicsExtractor#fakeItem(ItemStack, int, int)} while a
 * {@link RecipeViewScreen} is open.
 *
 * <p>RRV's {@code ReliableClientRecipeType.renderIcon()} calls
 * {@code fakeItem(this.getIcon(), x, y)} directly. If any registered recipe type
 * returns {@code null} from {@code getIcon()}, vanilla's
 * {@code GuiGraphicsExtractor} eventually dereferences the stack with
 * {@code itemStack.isEmpty()} and crashes with:
 * <pre>
 *   java.lang.NullPointerException: Cannot invoke "net.minecraft.world.item.ItemStack.isEmpty()"
 *   because "itemStack" is null
 * </pre>
 *
 * <p>All SkyRecipes recipe types return non-null icons, but the recipe view shows
 * buttons for every recipe type that has matches for the current item, including
 * types added by other mods. This mixin silently drops the render call instead of
 * letting the null stack propagate into vanilla rendering code.
 *
 * <p>Runs at high priority so the null stack is caught as early as possible.
 *
 * <p><b>TODO — Remove once RRV validates {@code getIcon()} upstream.</b>
 */
@Mixin(value = GuiGraphicsExtractor.class, priority = 500)
public class RrvScreenNullItemGuardMixin {

    @Inject(
            method = "fakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skyrecipes$skipNullFakeItemOnRrvScreen(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null && Minecraft.getInstance().screen instanceof RecipeViewScreen) {
            ci.cancel();
        }
    }
}
