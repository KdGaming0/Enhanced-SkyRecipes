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
 * Extracts the NEU internal name of a SkyBlock {@link ItemStack}.
 *
 * <p>SkyBlock items (both SkyRecipes-built and server-sent Hypixel items) store their
 * canonical ID inside the {@code CUSTOM_DATA} component under {@code id}. Vanilla items
 * have no {@code CUSTOM_DATA} and return {@code null}.</p>
 *
 * <p>Enchanted books, pets, runes, potions and attribute shards all report a single base
 * id ({@code ENCHANTED_BOOK}, {@code PET}, …) with the distinguishing detail held in a
 * side field ({@code enchantments}, {@code petInfo}, {@code runes}, {@code potion},
 * {@code attributes}). NEU keys those items by an expanded internal name instead
 * ({@code GROWTH;1}, {@code ENDER_DRAGON;4}), and so does every index in this mod, so
 * this class always resolves back to that expanded form — see {@link #extract}.</p>
 */
public final class SkyblockIdExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkyblockIdExtractor.class);

    /**
     * Custom-data key holding the exact NEU internal name of a SkyRecipes-built stack.
     *
     * <p>{@link com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder} writes it
     * whenever it rewrites a NEU internal name into the base id Hypixel sends, so the exact
     * name survives a rewrite that is not always reversible: a handful of NEU items carry an
     * {@code nbttag} that contradicts their own {@code internalname} (for example
     * {@code POTION_DUNGEON;3} is tagged {@code potion:"regeneration"}), and reconstructing
     * those from the side fields would silently alias them onto a different real item.</p>
     */
    public static final String INTERNAL_NAME_KEY = "skyrecipes:internal_name";

    // CustomData is immutable, so the extracted id is memoized per component instance —
    // in a field on the component itself, added by CustomDataIdCacheMixin. That replaced a
    // Guava weakKeys() map which was already identity-keyed but charged a hash, a segment
    // selection and a weak-ref dereference on every hit; extract() runs per inventory slot
    // per frame. The field's lifetime is exactly the component's, so nothing has to be
    // evicted. Sentinel for "checked, no id" so one field covers all three states.
    private static final String NO_ID = "";

    private SkyblockIdExtractor() {
    }

    /**
     * Extracts the numeric tier suffix after the last {@code ';'} in an internal
     * name ({@code "LION;4"} → 4), used by pet ids for the rarity index and by
     * kat-upgrade recipes for the tier.
     *
     * @return the suffix value, or {@code -1} when absent or non-numeric
     */
    public static int petTierSuffix(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return -1;
        }
        int semi = internalName.lastIndexOf(';');
        if (semi < 0 || semi == internalName.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(internalName.substring(semi + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Convert a NEU internal name into the id Hypixel's {@code /viewrecipe} command expects.
     *
     * <p>NEU disambiguates enchant books, pets, potions, runes and attribute shards with a
     * {@code ;tier} suffix ({@code ULTIMATE_CROP_FEVER;1}, {@code CHICKEN;0}). That suffix is a
     * NEU convention only — Hypixel keys {@code /viewrecipe} by the base id and never accepts a
     * {@code ';'}, so the suffix must be dropped ({@code ULTIMATE_CROP_FEVER}). Plain item names
     * carry no {@code ';'} and pass through unchanged, so {@code ENCHANTED_COAL} stays as-is.</p>
     *
     * <p>Attribute (Hunting) shards are the exception that needs the stack, not just its id: NEU
     * names them after the <em>bonus</em> they grant ({@code ATTRIBUTE_SHARD_FIG_COLLECTOR}), but
     * Hypixel keys the recipe by the shard creature's own id ({@code SPARROW_SHARD}), which only
     * survives in the display name ("Sparrow Shard"). So for those we derive the id from the
     * display name instead.</p>
     *
     * @return the {@code /viewrecipe} argument, or {@code null} if the stack has no SkyBlock id
     */
    public static String toViewRecipeId(ItemStack stack) {
        String internalName = extract(stack);
        if (internalName == null) {
            return null;
        }
        if (internalName.startsWith(ATTRIBUTE_SHARD_PREFIX)) {
            String shardId = shardIdFromDisplayName(stack);
            if (shardId != null) {
                return shardId;
            }
        }
        int semi = internalName.indexOf(';');
        return semi < 0 ? internalName : internalName.substring(0, semi);
    }

    private static final String ATTRIBUTE_SHARD_PREFIX = "ATTRIBUTE_SHARD_";

    /**
     * Build a shard's {@code <CREATURE>_SHARD} id from its display name ("Sparrow Shard" →
     * {@code SPARROW_SHARD}), or {@code null} when the stack has no custom name to read.
     */
    private static String shardIdFromDisplayName(ItemStack stack) {
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            return null;
        }
        String plain = name.getString().trim();
        if (plain.isEmpty()) {
            return null;
        }
        return plain.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * Extract the NEU internal name from the given stack, reconstructing it for items that
     * report a shared base id.
     *
     * <p>Resolved in three steps: the {@link #INTERNAL_NAME_KEY} written by our own stack
     * builder, then a plain {@code id} that is already an internal name, then — for live
     * Hypixel stacks, which carry no key of ours — reconstruction from the side fields.</p>
     *
     * @return the internal name (e.g. {@code "ASPECT_OF_THE_END"}, {@code "ENDER_DRAGON;4"}),
     * or {@code null} if the stack is empty, has no {@code CUSTOM_DATA}, or lacks an
     * {@code id} field.
     */
    public static String extract(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }

        SkyblockIdHolder holder = (SkyblockIdHolder) (Object) data;
        String cached = holder.skyrecipes$getCachedId();
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String id = extractUncached(data);
        holder.skyrecipes$setCachedId(id != null ? id : NO_ID);
        return id;
    }

    private static String extractUncached(CustomData data) {
        try {
            // Read-only view of the backing tag; copyTag() would deep-copy the
            // whole NBT tree on every slot every frame.
            CompoundTag tag = ((CustomDataAccessor) (Object) data).skyrecipes$getTag();

            // Authoritative for our own stacks, and exact even where reconstruction is not.
            String own = tag.getStringOr(INTERNAL_NAME_KEY, "");
            if (!own.isEmpty()) {
                return own;
            }

            // Some NEU nbttags nest these fields under ExtraAttributes; our builder and the
            // live Hypixel server both flatten them to the top level. Support both.
            CompoundTag source = tag.getCompound("ExtraAttributes").orElse(tag);
            String id = source.getStringOr("id", "");
            if (id.isEmpty() && source != tag) {
                id = tag.getStringOr("id", "");
            }
            if (id.isEmpty()) {
                return null;
            }
            if (!isSharedBaseId(id)) {
                return id;
            }
            String reconstructed = reconstructInternalName(id, source);
            return reconstructed != null ? reconstructed : id;
        } catch (Exception e) {
            LOGGER.debug("Failed to extract SkyBlock ID from stack", e);
        }

        return null;
    }

    /**
     * Base ids the Hypixel server reuses across a whole family of items, where the
     * distinguishing detail lives in a side field rather than in {@code id}.
     */
    private static boolean isSharedBaseId(String id) {
        return switch (id) {
            case "ENCHANTED_BOOK", "PET", "RUNE", "POTION", "ATTRIBUTE_SHARD" -> true;
            default -> false;
        };
    }

    private static String reconstructInternalName(String baseId, CompoundTag extra) {
        return switch (baseId) {
            case "ENCHANTED_BOOK" -> singleEntry(extra, "enchantments", "");
            case "RUNE" -> singleEntry(extra, "runes", "_RUNE");
            case "PET" -> petInternalName(extra);
            case "ATTRIBUTE_SHARD" -> {
                String attribute = singleEntry(extra, "attributes", "");
                yield attribute != null ? "ATTRIBUTE_SHARD_" + attribute : null;
            }
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
