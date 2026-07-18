package com.github.kdgaming0.skyrecipes.core.util;

import com.github.kdgaming0.skyrecipes.core.model.SkyblockRarity;
import com.github.kdgaming0.skyrecipes.mixin.accessor.CustomDataAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Extracts the SkyBlock internal name ({@code ExtraAttributes.id}) from an {@link ItemStack}.
 *
 * <p>SkyBlock items (both NEU-generated and server-sent Hypixel items) store their
 * canonical ID inside the {@code CUSTOM_DATA} component under {@code ExtraAttributes.id}.
 * Vanilla items have no {@code CUSTOM_DATA} and return {@code null}.</p>
 */
public final class SkyblockIdExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockIdExtractor.class);

    // CustomData is immutable, so the extracted id can be memoized per component
    // instance. Guava's weakKeys() compares keys by identity — O(1) hashing with no
    // deep NBT comparisons — and lets components from replaced stacks be collected,
    // replacing the old clear-at-cap eviction (which forced a re-extraction burst
    // across every slot). The map is concurrent because extraction runs on the
    // render thread, the pipeline workers, and parallel rebuild streams alike.
    private static final String NO_ID = "";
    private static final java.util.concurrent.ConcurrentMap<CustomData, String> ID_CACHE =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();

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

        String cached = ID_CACHE.get(data);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String id = extractUncached(data);
        ID_CACHE.put(data, id != null ? id : NO_ID);
        return id;
    }

    private static String extractUncached(CustomData data) {
        try {
            // Read-only view of the backing tag; copyTag() would deep-copy the
            // whole NBT tree on every slot every frame.
            CompoundTag tag = ((CustomDataAccessor) (Object) data).skyrecipes$getTag();

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

    /**
     * Extract the <b>NEU internal name</b> from the given stack, reconstructing it for
     * items that share a generic {@code ExtraAttributes.id} on the live Hypixel server.
     *
     * <p>Enchanted books, pets, runes and potions all report a single base id
     * ({@code ENCHANTED_BOOK}, {@code PET}, {@code RUNE}, {@code POTION}) on real inventory
     * stacks, with the distinguishing detail held in a side field ({@code enchantments},
     * {@code petInfo}, {@code runes}, {@code potion}/{@code potion_level}). NEU keys these
     * items by their expanded internal name (e.g. {@code GROWTH;1}, {@code ENDER_DRAGON;4}),
     * so a raw {@link #extract(ItemStack)} of the base id never matches the recipe index.
     * This method rebuilds that internal name so R/U lookups resolve.</p>
     *
     * <p>For every other item this returns exactly what {@link #extract(ItemStack)} returns.
     * Applied on both the index and lookup sides of {@code SkyblockRecipeCache}, so the two
     * always agree regardless of which id form a given NEU {@code nbttag} happens to use.</p>
     *
     * @return the NEU internal name, or {@code null} if the stack carries no SkyBlock id
     */
    public static String extractInternalName(ItemStack stack) {
        String id = extract(stack);
        if (id == null) {
            return null;
        }
        if (!isSharedBaseId(id)) {
            return id;
        }
        try {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return id;
            }
            CompoundTag tag = ((CustomDataAccessor) (Object) data).skyrecipes$getTag();
            // NEU-built stacks nest these fields under ExtraAttributes; live Hypixel
            // items flatten them to the top level of custom_data. Support both.
            CompoundTag source = tag.getCompound("ExtraAttributes").orElse(tag);
            String reconstructed = reconstructInternalName(id, source);
            return reconstructed != null ? reconstructed : id;
        } catch (Exception e) {
            LOGGER.debug("Failed to reconstruct internal name for base id '{}'", id, e);
            return id;
        }
    }

    private static boolean isSharedBaseId(String id) {
        return switch (id) {
            case "ENCHANTED_BOOK", "PET", "RUNE", "POTION" -> true;
            default -> false;
        };
    }

    private static String reconstructInternalName(String baseId, CompoundTag extra) {
        return switch (baseId) {
            case "ENCHANTED_BOOK" -> singleEntry(extra, "enchantments", "");
            case "RUNE" -> singleEntry(extra, "runes", "_RUNE");
            case "PET" -> petInternalName(extra);
            case "POTION" -> {
                String potion = extra.getStringOr("potion", "");
                if (potion.isEmpty()) {
                    yield null;
                }
                int level = extra.getInt("potion_level").orElse(0);
                yield "POTION_" + potion.toUpperCase(Locale.ROOT) + ";" + level;
            }
            default -> null;
        };
    }

    /**
     * Builds {@code <KEY><suffix>;<level>} from the first (and, for books/runes, only)
     * entry of a {@code {KEY:level}} compound.
     */
    private static String singleEntry(CompoundTag extra, String field, String keySuffix) {
        CompoundTag compound = extra.getCompound(field).orElse(null);
        if (compound == null) {
            return null;
        }
        for (String key : compound.keySet()) {
            int level = compound.getInt(key).orElse(0);
            return key.toUpperCase(Locale.ROOT) + keySuffix + ";" + level;
        }
        return null;
    }

    private static String petInternalName(CompoundTag extra) {
        String petInfo = extra.getStringOr("petInfo", "");
        if (petInfo.isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(petInfo).getAsJsonObject();
            if (!obj.has("type") || !obj.has("tier")) {
                return null;
            }
            String type = obj.get("type").getAsString();
            String tier = obj.get("tier").getAsString();
            SkyblockRarity rarity = SkyblockRarity.valueOf(tier.toUpperCase(Locale.ROOT));
            return type.toUpperCase(Locale.ROOT) + ";" + rarity.ordinal();
        } catch (Exception e) {
            LOGGER.debug("Failed to parse petInfo '{}'", petInfo, e);
            return null;
        }
    }
}
