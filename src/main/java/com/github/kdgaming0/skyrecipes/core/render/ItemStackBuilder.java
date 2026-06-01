package com.github.kdgaming0.skyrecipes.core.render;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.LegacyItemIdMapper;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts {@link NeuItem} data into Minecraft {@link ItemStack} objects.
 *
 * <p>Parses the legacy SNBT string from NEU repo and maps it to modern
 * Minecraft data components.</p>
 */
public final class ItemStackBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemStackBuilder.class);

    private ItemStackBuilder() {}

    /**
     * Build an {@link ItemStack} from a {@link NeuItem} with default count of 1.
     */
    public static ItemStack build(NeuItem item) {
        return build(item, 1);
    }

    /**
     * Build an {@link ItemStack} from a {@link NeuItem} with the specified count.
     */
    public static ItemStack build(NeuItem item, int count) {
        if (count < 1) count = 1;

        String mappedId = LegacyItemIdMapper.map(item.itemId(), item.damage());
        Item itemType = resolveItem(mappedId);
        if (itemType == null) {
            LOGGER.warn("Unknown item id '{}' (mapped from '{}') for {}", mappedId, item.itemId(), item.internalName());
            itemType = Items.BARRIER;
        }

        ItemStack stack = new ItemStack(itemType, count);

        // For vanilla items, skip custom NBT parsing
        if (item.vanilla()) {
            return stack;
        }

        // Parse SNBT
        String nbtString = item.nbtTag();
        if (nbtString == null || nbtString.isEmpty()) {
            return stack;
        }

        try {
            String normalizedNbt = preprocessNeuSnbt(nbtString);
            CompoundTag tag = TagParser.parseCompoundFully(normalizedNbt);
            applyComponents(stack, tag, item);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse NBT for {}: {}", item.internalName(), e.getMessage());
        }

        return stack;
    }

    /**
     * Preprocess NEU SNBT strings to remove numeric list indices.
     *
     * <p>NEU uses a legacy/pre-1.20 SNBT format where list elements are prefixed
     * with numeric indices: {@code [0:"...", 1:"..."]}. Modern Minecraft's
     * {@link TagParser} cannot parse this format. This method strips the indices
     * to produce standard SNBT: {@code ["...", "..."]}.</p>
     */
    private static String preprocessNeuSnbt(String snbt) {
        if (snbt == null || snbt.isEmpty()) {
            return snbt;
        }
        StringBuilder result = new StringBuilder(snbt.length());
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < snbt.length(); i++) {
            char c = snbt.charAt(i);

            if (escape) {
                result.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                result.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }

            // When not inside a string, after '[' or ',' skip N: prefixes
            if (!inString && (c == '[' || c == ',')) {
                result.append(c);
                int j = i + 1;
                // Skip whitespace
                while (j < snbt.length() && Character.isWhitespace(snbt.charAt(j))) {
                    j++;
                }
                // Skip digits
                int digitStart = j;
                while (j < snbt.length() && Character.isDigit(snbt.charAt(j))) {
                    j++;
                }
                if (j > digitStart && j < snbt.length() && snbt.charAt(j) == ':') {
                    // This is an index prefix — skip it
                    i = j;
                }
                continue;
            }

            result.append(c);
        }
        return result.toString();
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static void applyComponents(ItemStack stack, CompoundTag tag, NeuItem item) {
        // display: {Name, Lore, color}
        CompoundTag display = tag.getCompoundOrEmpty("display");
        if (!display.isEmpty()) {
            // Custom name
            String rawName = display.getStringOr("Name", "");
            if (!rawName.isEmpty()) {
                Component name = LegacyStringParser.parse(rawName);
                stack.set(DataComponents.CUSTOM_NAME, name);
            }

            // Lore
            ListTag loreList = display.getListOrEmpty("Lore");
            if (!loreList.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (int i = 0; i < loreList.size(); i++) {
                    String rawLine = loreList.getStringOr(i, "");
                    if (!rawLine.isEmpty()) {
                        loreComponents.add(LegacyStringParser.parse(rawLine));
                    }
                }
                if (!loreComponents.isEmpty()) {
                    stack.set(DataComponents.LORE, new ItemLore(loreComponents));
                }
            }

            // Dyed color (for leather armor)
            Optional<Integer> colorOpt = display.getInt("color");
            colorOpt.ifPresent(color -> stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color)));
        }

        // SkullOwner -> PROFILE
        Optional<CompoundTag> skullOwnerOpt = tag.getCompound("SkullOwner");
        if (skullOwnerOpt.isPresent()) {
            ResolvableProfile profile = resolveProfile(skullOwnerOpt.get());
            if (profile != null) {
                stack.set(DataComponents.PROFILE, profile);
            }
        }

        // ExtraAttributes -> CUSTOM_DATA
        Optional<CompoundTag> extraOpt = tag.getCompound("ExtraAttributes");
        if (extraOpt.isPresent()) {
            CompoundTag extra = extraOpt.get();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, existing -> {
                existing.merge(extra);
            });
        }

        // Unbreakable
        Optional<Byte> unbreakableOpt = tag.getByte("Unbreakable");
        if (unbreakableOpt.isPresent() && unbreakableOpt.get() != 0) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }

        // HideFlags -> TOOLTIP_DISPLAY
        Optional<Integer> hideFlagsOpt = tag.getInt("HideFlags");
        if (hideFlagsOpt.isPresent() && hideFlagsOpt.get() != 0) {
            applyHideFlags(stack, hideFlagsOpt.get());
        }

        // Enchantments — deferred to later milestone due to modern registry complexity

        // Also store the full raw NBT in custom_data as a fallback
        // This ensures SkyBlock mods can still read the original NBT
        CustomData.update(DataComponents.CUSTOM_DATA, stack, existing -> {
            for (String key : tag.keySet()) {
                if (!key.equals("display") && !key.equals("SkullOwner") && !key.equals("ExtraAttributes")) {
                    net.minecraft.nbt.Tag val = tag.get(key);
                    if (val != null) {
                        existing.put(key, val);
                    }
                }
            }
        });
    }

    private static ResolvableProfile resolveProfile(CompoundTag skullOwner) {
        try {
            UUID uuid = UUID.randomUUID();
            Optional<String> idStrOpt = skullOwner.getString("Id");
            if (idStrOpt.isPresent() && !idStrOpt.get().isEmpty()) {
                uuid = UUID.fromString(idStrOpt.get());
            } else {
                Optional<int[]> intArrayOpt = skullOwner.getIntArray("Id");
                if (intArrayOpt.isPresent()) {
                    int[] intArray = intArrayOpt.get();
                    if (intArray.length == 4) {
                        uuid = new UUID(
                            ((long) intArray[0] << 32) | (intArray[1] & 0xFFFFFFFFL),
                            ((long) intArray[2] << 32) | (intArray[3] & 0xFFFFFFFFL)
                        );
                    }
                }
            }

            String name = skullOwner.getStringOr("Name", "");

            // Extract texture property
            Optional<CompoundTag> propertiesOpt = skullOwner.getCompound("Properties");
            if (propertiesOpt.isPresent()) {
                CompoundTag properties = propertiesOpt.get();
                ListTag textures = properties.getListOrEmpty("textures");
                if (!textures.isEmpty()) {
                    CompoundTag firstTexture = textures.getCompoundOrEmpty(0);
                    String value = firstTexture.getStringOr("Value", "");
                    if (!value.isEmpty()) {
                        Multimap<String, Property> props = ArrayListMultimap.create();
                        props.put("textures", new Property("textures", value));
                        GameProfile profile = new GameProfile(uuid, name.isEmpty() ? "SkyBlock" : name, new PropertyMap(props));
                        return ResolvableProfile.createResolved(profile);
                    }
                }
            }

            // No texture property found; create unresolved profile by name or UUID
            if (!name.isEmpty()) {
                return ResolvableProfile.createUnresolved(name);
            }
            return ResolvableProfile.createUnresolved(uuid);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve skull profile: {}", e.getMessage());
            return null;
        }
    }

    private static void applyHideFlags(ItemStack stack, int hideFlags) {
        // HideFlags bit mask:
        // 1 = enchantments, 2 = attribute modifiers, 4 = unbreakable, 8 = can destroy,
        // 16 = can place on, 32 = stored enchantments, 64 = dye, 128 = trim
        TooltipDisplay current = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        LinkedHashSet<net.minecraft.core.component.DataComponentType<?>> hidden = new LinkedHashSet<>();
        if (current.hiddenComponents() != null) {
            hidden.addAll(current.hiddenComponents());
        }

        if ((hideFlags & 1) != 0) hidden.add(DataComponents.ENCHANTMENTS);
        if ((hideFlags & 2) != 0) hidden.add(DataComponents.ATTRIBUTE_MODIFIERS);
        if ((hideFlags & 4) != 0) hidden.add(DataComponents.UNBREAKABLE);
        if ((hideFlags & 8) != 0) hidden.add(DataComponents.CAN_BREAK);
        if ((hideFlags & 16) != 0) hidden.add(DataComponents.CAN_PLACE_ON);
        if ((hideFlags & 32) != 0) hidden.add(DataComponents.STORED_ENCHANTMENTS);
        if ((hideFlags & 64) != 0) hidden.add(DataComponents.DYED_COLOR);
        if ((hideFlags & 128) != 0) hidden.add(DataComponents.TRIM);

        if (!hidden.isEmpty()) {
            stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(current.hideTooltip(), hidden));
        }
    }
}
