package com.github.kdgaming0.skyrecipes.client.hypixel;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Constant-time cache of the player's current SkyBlock island mode.
 */
public final class HypixelLocationTracker {
    private static volatile String currentIslandCode;
    private static boolean initialized;

    private HypixelLocationTracker() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (!(packet instanceof LocationUpdateS2CPacket location)) {
                return;
            }

            boolean skyBlock = location.serverType()
                    .map(type -> "SKYBLOCK".equalsIgnoreCase(type))
                    .orElse(false);
            currentIslandCode = skyBlock
                    ? location.mode().map(HypixelLocationTracker::normalize).orElse(null)
                    : null;
        });

        Object2IntOpenHashMap<CustomPacketPayload.Type<HypixelS2CPacket>> events =
                new Object2IntOpenHashMap<>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        HypixelNetworking.registerToEvents(events);

        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> currentIslandCode = null);
    }

    @Nullable
    public static String currentIslandCode() {
        return currentIslandCode;
    }

    private static String normalize(String code) {
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
