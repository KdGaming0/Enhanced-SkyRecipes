package com.github.kdgaming0.skyrecipes.rrv.util;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A harmless dummy slot returned when third-party mods request an out-of-bounds
 * index from {@link cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu} during
 * its transient slot rebuilds.
 *
 * <p>The slot is positioned offscreen, reports as inactive, and ignores all
 * mutations, making it safe for any mod to query or cache.
 */
public final class SafeDummySlot extends Slot {

    private static final SimpleContainer SHARED_CONTAINER = new SimpleContainer(1);

    public SafeDummySlot() {
        super(SHARED_CONTAINER, 0, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2);
    }

    @Override
    public boolean hasItem() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        // no-op — immutable dummy
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
