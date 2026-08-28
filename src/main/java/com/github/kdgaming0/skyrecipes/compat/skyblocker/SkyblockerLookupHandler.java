package com.github.kdgaming0.skyrecipes.compat.skyblocker;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.mojang.datafixers.util.Either;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.item.ItemPrice;
import de.hysky.skyblocker.skyblock.item.wikilookup.WikiLookupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Shared, fail-soft bridge from RRV item stacks to Skyblocker's lookup actions. */
public final class SkyblockerLookupHandler {

    private static boolean disabled;

    private SkyblockerLookupHandler() {
    }

    public static boolean handle(ItemStack stack, KeyEvent event) {
        if (disabled || stack == null || stack.isEmpty()) {
            return false;
        }

        try {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return false;
            }

            // Skyblocker owns the wiki keybind and enable checks.
            if (WikiLookupManager.handleWikiLookup(Either.right(stack), player, event)) {
                return true;
            }

            // Skyblocker's container mixin normally owns these two checks, so RRV
            // screens must reproduce them before calling the ItemStack overload.
            if (SkyblockerConfigManager.get().helpers.itemPrice.enableItemPriceLookup
                    && ItemPrice.ITEM_PRICE_LOOKUP.matches(event)) {
                ItemPrice.itemPriceLookup(player, stack);
                return true;
            }
        } catch (Throwable t) {
            disabled = true;
            SkyRecipes.LOGGER.warn(
                    "RRV hovered-item lookup integration disabled (Skyblocker API changed?)", t);
        }

        return false;
    }
}
