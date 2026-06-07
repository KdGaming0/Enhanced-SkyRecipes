package com.github.kdgaming0.skyrecipes.rrv.recipe;

import cc.cassian.rrv.api.ActionType;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to open a specific recipe in RRV while preserving parent-screen
 * state and view history so that the back button works correctly.
 */
public final class RecipeViewOpener {

    private RecipeViewOpener() {
    }

    public static void open(ReliableClientRecipe recipe) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Screen current = Minecraft.getInstance().screen;
        Screen parent = current;
        ArrayList<RecipeViewScreen> viewHistory = new ArrayList<>();

        if (current instanceof RecipeViewScreen existing) {
            parent = existing.getMenu().getParentScreen();
            viewHistory = existing.getMenu().getViewHistory();
        }

        int containerId = parent instanceof AbstractContainerScreen<?> cs
                ? cs.getMenu().containerId
                : 0;

        Minecraft.getInstance().setScreen(new RecipeViewScreen(
                new RecipeViewMenu(
                        parent, containerId, player.getInventory(),
                        List.of(recipe), ItemStack.EMPTY, ActionType.ANY, viewHistory),
                player.getInventory(),
                Component.empty()));
    }
}
