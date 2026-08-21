package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import de.hysky.skyblocker.skyblock.item.background.ItemBackgroundManager;
import de.hysky.skyblocker.utils.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Skyblocker's item backgrounds (rarity, Jacob medal, legacy attribute) behind
 * items in RRV's recipe grid.
 *
 * <p>{@code RecipeViewScreen#extractSlot} renders a slot's item through one of
 * two mutually exclusive calls:</p>
 *
 * <pre>{@code
 * if (slot.isFake()) graphics.fakeItem(itemStack, x, y, seed);
 * else               graphics.item(itemStack, x, y, seed);
 * }</pre>
 *
 * <p>Skyblocker's {@code AbstractContainerScreenMixin#skyblocker$drawOnItem} injects only
 * at the {@code item} call. RRV's recipe slots render through the {@code fakeItem} branch,
 * so Skyblocker's injection point is never reached.
 * The backgrounds silently do not draw — nothing errors, the hook simply never fires.</p>
 *
 * <p>This mixin covers the other branch. Because the two branches cannot both run for a
 * single slot, this can never double-draw against Skyblocker's own hook. It mirrors that
 * hook exactly — same {@link ItemBackgroundManager#drawBackgrounds} call, same
 * {@link Utils#isOnSkyblock()} gate — so Skyblocker's per-background config toggles and
 * opacity/style settings keep working, since {@code drawBackgrounds} checks
 * {@code isEnabled()} on each background itself.</p>
 *
 * <p>Scoped to {@link RecipeViewScreen} so unrelated fake slots (creative search, recipe
 * book ghost items, other mods' screens) keep their current appearance. Fixing those is
 * Skyblocker's call, not ours.</p>
 *
 * <p>Applied only when Skyblocker is loaded <em>and</em> the Skyblocker/RRV classes named
 * above still exist — see {@code SkyRecipesMixinPlugin}. If either mod moves or renames
 * them, the mixin is skipped and recipe items simply render without a background, which is
 * the current behaviour anyway.</p>
 */
@Mixin(RecipeViewScreen.class)
public class RecipeSlotRarityBackgroundMixin {

    @Inject(
            method = "extractSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"
            )
    )
    private void skyrecipes$drawItemBackgroundOnFakeSlot(GuiGraphicsExtractor graphics, Slot slot, CallbackInfo ci) {
        if (Utils.isOnSkyblock()) {
            ItemBackgroundManager.drawBackgrounds(slot.getItem(), graphics, slot.x, slot.y);
        }
    }
}
