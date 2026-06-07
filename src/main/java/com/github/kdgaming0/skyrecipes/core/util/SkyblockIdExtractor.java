package com.github.kdgaming0.skyrecipes.core.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the SkyBlock internal name ({@code ExtraAttributes.id}) from an {@link ItemStack}.
 *
 * <p>SkyBlock items (both NEU-generated and server-sent Hypixel items) store their
 * canonical ID inside the {@code CUSTOM_DATA} component under {@code ExtraAttributes.id}.
 * Vanilla items have no {@code CUSTOM_DATA} and return {@code null}.</p>
 */
public final class SkyblockIdExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockIdExtractor.class);

    private SkyblockIdExtractor() {
    }

    /**
     * Extract the SkyBlock internal name from the given stack.
     *
     * @return the SkyBlock ID (e.g. {@code "ASPECT_OF_THE_END"}), or {@code null} if the stack
     * is empty, has no {@code CUSTOM_DATA}, or lacks an {@code ExtraAttributes.id} field.
     */
    public static String extract(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }

        try {
            CompoundTag tag = data.copyTag();

            // Primary: ExtraAttributes.id (present on all NEU-built and Hypixel-server stacks)
            CompoundTag extra = tag.getCompound("ExtraAttributes").orElse(null);
            if (extra != null) {
                String id = extra.getStringOr("id", "");
                if (!id.isEmpty()) {
                    return id;
                }
            }

            // Fallback: direct id key in custom_data (future-proofing)
            String id = tag.getStringOr("id", "");
            if (!id.isEmpty()) {
                return id;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract SkyBlock ID from stack", e);
        }

        return null;
    }
}
