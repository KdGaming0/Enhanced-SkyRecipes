package com.github.kdgaming0.skyrecipes.rrv.recipe.util;

import net.minecraft.client.Minecraft;

/**
 * Sends chat commands from recipe-card buttons, guarding against
 * no-world/no-connection states.
 */
public final class ClientCommandSender {

    private ClientCommandSender() {
    }

    /**
     * Send a chat command (without leading slash) if the player is in a world.
     */
    public static void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }
}
