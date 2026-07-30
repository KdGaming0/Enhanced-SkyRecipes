package com.github.kdgaming0.skyrecipes.core.render.item;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.LegacyItemIdMapper;
import com.github.kdgaming0.skyrecipes.core.util.LegacyStringParser;
import com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor;
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
import net.minecraft.world.item.component.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts {@link NeuItem} data into Minecraft {@link ItemStack} objects.
 *
 * <p>Parses the legacy SNBT string from NEU repo and maps it to modern
 * Minecraft data components.</p>
 *
 * <p>During a pipeline cycle (between {@link #beginCycleCache()} and
 * {@link #endCycleCache()}) built templates are memoized per internal name, so
 * the same item is SNBT-parsed once per cycle instead of once per recipe slot.
 * Every {@link #build} call still returns a fresh stack.</p>
 */
public final class ItemStackBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemStackBuilder.class);

    /**
     * Non-null only while a pipeline cycle is running. Volatile so worker threads
     * (parallel stack build, recipe-gen ForkJoin tasks) see enable/drop immediately;
     * a null read simply falls back to the uncached path.
     */
    private static volatile ConcurrentHashMap<String, CycleEntry> cycleCache = null;

    /** Expanded NEU ids already reported by {@link #hypixelBaseId}, so each warns once. */
    private static final Set<String> UNMAPPED_SEMICOLON_IDS = ConcurrentHashMap.newKeySet();

    /**
     * Keeps the source item alongside its template: a task invalidated by a data
     * reload may still write into the new cycle's map, and the identity check on
     * read prevents its stale template from being served to the new cycle.
     */
    private record CycleEntry(NeuItem source, ItemStack template) {
    }

    private ItemStackBuilder() {
    }

    /**
     * Enable the per-cycle template memo. Call when a pipeline cycle's background
     * generation starts; replaces any map left over from an aborted cycle.
     */
    public static void beginCycleCache() {
        cycleCache = new ConcurrentHashMap<>();
    }

    /**
     * Drop the per-cycle template memo. Call once the cycle's results are committed
     * (or the cycle aborts) so the ~8k templates don't outlive the cycle.
     */
    public static void endCycleCache() {
        cycleCache = null;
    }

    /**
     * Build an {@link ItemStack} from a {@link NeuItem} with default count of 1.
     */
    public static ItemStack build(NeuItem item) {
        return build(item, 1);
    }

    /**
     * Build an {@link ItemStack} from a {@link NeuItem} with the specified count.
     * Always returns a stack the caller may freely mutate.
     */
    public static ItemStack build(NeuItem item, int count) {
        if (count < 1) count = 1;

        ConcurrentHashMap<String, CycleEntry> cache = cycleCache;
        String key = item.internalName();
        if (cache == null || key == null) {
            return buildUncached(item, count);
        }

        CycleEntry entry = cache.computeIfAbsent(key, _ -> new CycleEntry(item, buildUncached(item, 1)));
        if (entry.source() != item) {
            entry = new CycleEntry(item, buildUncached(item, 1));
            cache.put(key, entry);
        }
        return entry.template().copyWithCount(count);
    }

    private static ItemStack buildUncached(NeuItem item, int count) {
        String mappedId = LegacyItemIdMapper.map(item.itemId(), item.damage());
        Item itemType = resolveItem(mappedId);
        if (itemType == null) {
            LOGGER.warn("Unknown item id '{}' (mapped from '{}') for {}", mappedId, item.itemId(), item.internalName());
            itemType = Items.BARRIER;
        }

        ItemStack stack = new ItemStack(itemType, count);

        // For vanilla items, skip heavy SNBT parsing (SkullOwner, enchantments, etc.)
        // but still apply NEU display name/lore so the item is distinguishable.
        // NEU's vanilla flag means "uses a vanilla base item", not "has no custom data".
        if (item.vanilla()) {
            applyVanillaDisplay(stack, item);
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
            applyItemModel(stack, tag, item);
            applyComponents(stack, tag, item);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse NBT for {}", item.internalName(), e);
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
        // The builder is allocated only once an index prefix is actually found: most NEU
        // nbttag strings carry none, and rewriting every one of ~8.5k multi-KB strings into
        // a fresh StringBuilder plus a fresh String was tens of MB of garbage per cycle.
        // Until then the input is verbatim, so the skipped prefix can be copied in one go.
        StringBuilder result = null;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < snbt.length(); i++) {
            char c = snbt.charAt(i);

            if (escape) {
                if (result != null) result.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                if (result != null) result.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                if (result != null) result.append(c);
                continue;
            }

            // When not inside a string, after '[' or ',' skip N: prefixes
            if (!inString && (c == '[' || c == ',')) {
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
                    if (result == null) {
                        result = new StringBuilder(snbt.length());
                        result.append(snbt, 0, i);
                    }
                    result.append(c);
                    // This is an index prefix — skip it
                    i = j;
                    continue;
                }
                if (result != null) result.append(c);
                continue;
            }

            if (result != null) result.append(c);
        }
        return result == null ? snbt : result.toString();
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

    /**
     * Apply display name, lore, and minimal ExtraAttributes for items marked vanilla.
     *
     * <p>NEU marks collection items and other SkyBlock items as {@code vanilla:true}
     * when they use a vanilla base item ID. They still carry NEU-specific display data
     * and a SkyBlock ID in {@code ExtraAttributes}. This method applies those lightweight
     * components without parsing heavy SNBT like {@code SkullOwner} or enchantments.</p>
     */
    private static void applyVanillaDisplay(ItemStack stack, NeuItem item) {
        // Apply NEU display name (may be compile-time resolved, e.g. pet placeholders)
        String rawName = item.displayName();
        if (rawName != null && !rawName.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, LegacyStringParser.parse(rawName));
        }

        // Apply NEU lore (may be compile-time resolved pet stats)
        List<String> loreLines = item.lore();
        if (loreLines != null && !loreLines.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                if (!line.isEmpty()) {
                    loreComponents.add(LegacyStringParser.parse(line));
                }
            }
            if (!loreComponents.isEmpty()) {
                stack.set(DataComponents.LORE, new ItemLore(loreComponents));
            }
        }

        // Minimal ExtraAttributes parsing so SkyBlock ID is available for recipe lookups
        String nbtString = item.nbtTag();
        if (nbtString == null || nbtString.isEmpty()) {
            return;
        }
        try {
            String normalizedNbt = preprocessNeuSnbt(nbtString);
            CompoundTag tag = TagParser.parseCompoundFully(normalizedNbt);
            applyItemModel(stack, tag, item);
            applyGlint(stack, tag);
            Optional<CompoundTag> extraOpt = tag.getCompound("ExtraAttributes");
            if (extraOpt.isPresent()) {
                applyExtraAttributes(stack, extraOpt.get(), item);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse minimal NBT for vanilla item {}: {}",
                    item.internalName(), e.getMessage());
        }
    }

    /**
     * Copies NEU's {@code ExtraAttributes} into {@code CUSTOM_DATA}, rewriting NEU-style
     * internal names into the base id the Hypixel server actually sends.
     *
     * <p>NEU keys pets, enchanted books, runes, potions and attribute shards by an expanded
     * internal name and puts that name straight into {@code id} ({@code WOLF;4},
     * {@code ULTIMATE_WISE;5}). A live server stack instead carries a shared base id
     * ({@code PET}, {@code ENCHANTED_BOOK}, …) with the detail in a side field, which NEU
     * supplies too. Emitting the server's shape means every mod that already understands
     * real SkyBlock items understands ours — Skyblocker's price and value tooltips, for one,
     * key entirely off the id it derives from that shape.</p>
     *
     * <p>The rewrite is only reversible through {@link SkyblockIdExtractor#INTERNAL_NAME_KEY},
     * written alongside, because a few NEU items carry side fields that contradict their own
     * internal name.</p>
     */
    private static void applyExtraAttributes(ItemStack stack, CompoundTag extra, NeuItem item) {
        String baseId = hypixelBaseId(extra, item);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, existing -> {
            existing.merge(extra);
            if (baseId != null) {
                existing.putString("id", baseId);
                existing.putString(SkyblockIdExtractor.INTERNAL_NAME_KEY, item.internalName());
            }
        });
    }

    /**
     * The base id Hypixel sends for this item, or {@code null} to keep NEU's id as-is.
     *
     * <p>Gated on the semicolon that marks an expanded NEU name: ordinary items carry side
     * fields too (any enchanted sword has {@code enchantments}) and must not be rewritten.</p>
     */
    private static String hypixelBaseId(CompoundTag extra, NeuItem item) {
        String id = extra.getStringOr("id", "");
        if (!id.contains(";")) {
            return null;
        }
        if (extra.contains("petInfo")) return "PET";
        if (extra.contains("enchantments")) return "ENCHANTED_BOOK";
        if (extra.contains("runes")) return "RUNE";
        if (extra.contains("attributes")) return "ATTRIBUTE_SHARD";
        if (extra.contains("potion")) return "POTION";

        // No NEU item is shaped like this today. If the repo ever adds a kind we do not
        // know, the id stays in NEU's form and other mods see an id Hypixel never sends —
        // so say so once rather than let it pass silently.
        if (UNMAPPED_SEMICOLON_IDS.add(id)) {
            LOGGER.warn("NEU item {} has an expanded id '{}' with no recognised side field; "
                    + "leaving it unmapped", item.internalName(), id);
        }
        return null;
    }

    /**
     * Apply the {@code ItemModel} tag as the {@code minecraft:item_model} component.
     *
     * <p>Hypixel's custom-item system sets this component on live stacks; it points at
     * the model resource packs override to retexture SkyBlock items. Without it the
     * stack renders as its base item (often paper) and packs have no hook.</p>
     */
    private static void applyItemModel(ItemStack stack, CompoundTag tag, NeuItem item) {
        String itemModel = tag.getStringOr("ItemModel", "");
        if (itemModel.isEmpty()) {
            return;
        }
        Identifier modelId = Identifier.tryParse(itemModel);
        if (modelId == null) {
            LOGGER.warn("Invalid ItemModel '{}' for {}", itemModel, item.internalName());
            return;
        }
        stack.set(DataComponents.ITEM_MODEL, modelId);
    }

    private static void applyGlint(ItemStack stack, CompoundTag tag) {
        if (tag.contains("ench")) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    private static void applyComponents(ItemStack stack, CompoundTag tag, NeuItem item) {
        // display: {Name, Lore, color}
        CompoundTag display = tag.getCompoundOrEmpty("display");
        if (!display.isEmpty()) {
            // Custom name — prefer NeuItem.displayName() (which may have been resolved at
            // compile time, e.g. pet placeholders), fall back to SNBT display.Name.
            String rawName = item.displayName();
            if (rawName == null || rawName.isEmpty()) {
                rawName = display.getStringOr("Name", "");
            }
            if (!rawName.isEmpty()) {
                Component name = LegacyStringParser.parse(rawName);
                stack.set(DataComponents.CUSTOM_NAME, name);
            }

            // Lore — prefer NeuItem.lore() (compile-time resolved), fall back to SNBT.
            List<String> loreLines = item.lore();
            if (loreLines == null || loreLines.isEmpty()) {
                ListTag loreList = display.getListOrEmpty("Lore");
                if (!loreList.isEmpty()) {
                    loreLines = new ArrayList<>(loreList.size());
                    for (int i = 0; i < loreList.size(); i++) {
                        String rawLine = loreList.getStringOr(i, "");
                        if (!rawLine.isEmpty()) {
                            loreLines.add(rawLine);
                        }
                    }
                }
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>(loreLines.size());
                for (String rawLine : loreLines) {
                    if (!rawLine.isEmpty()) {
                        loreComponents.add(LegacyStringParser.parse(rawLine));
                    }
                }
                if (!loreComponents.isEmpty()) {
                    stack.set(DataComponents.LORE, new ItemLore(loreComponents));
                }
            }

            // Safety net: warn if unresolved pet placeholders leaked through
            if (loreLines != null) {
                for (String line : loreLines) {
                    if (line != null && line.contains("{") && line.matches(".*\\{[A-Z_]+\\}.*")) {
                        LOGGER.warn("Unresolved placeholder in lore for {}: {}",
                                item.internalName(), line);
                        break;
                    }
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
            applyExtraAttributes(stack, extraOpt.get(), item);
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

        applyGlint(stack, tag);

        // Also store the full raw NBT in custom_data as a fallback
        // This ensures SkyBlock mods can still read the original NBT
        CustomData.update(DataComponents.CUSTOM_DATA, stack, existing -> {
            for (String key : tag.keySet()) {
                if (!key.equals("display") && !key.equals("SkullOwner") && !key.equals("ExtraAttributes")
                        && !key.equals("ItemModel")) {
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
            UUID uuid = null;
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
            if (uuid == null) {
                // Only for the rare skull whose tag carries no usable Id. This used to run
                // unconditionally and was then almost always overwritten — and
                // UUID.randomUUID() draws from a shared SecureRandom whose nextBytes is
                // synchronized, so every thread of the parallel stack build queued on one
                // lock for a value it threw away.
                uuid = UUID.randomUUID();
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
            LOGGER.warn("Failed to resolve skull profile", e);
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
