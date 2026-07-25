package com.github.kdgaming0.skyrecipes.rrv.recipe;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.AttributeShardData;
import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import com.google.common.collect.MapMaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves attribute shards shown in Hypixel's shard menus, which carry no
 * {@code ExtraAttributes.id} for {@link com.github.kdgaming0.skyrecipes.core.util.SkyblockIdExtractor}
 * to read.
 *
 * <p>Items in the Attribute Menu, Hunting Box, Fusion Box, Shard Fusion, Confirm Fusion and every
 * Bazaar shard page are display-only skulls: the server sends no SkyBlock id, so a recipe lookup
 * falls through to RRV's vanilla {@code Item} index and answers from the {@code minecraft:player_head}
 * bucket. This class recovers the NEU internal name from what those stacks <em>do</em> carry —
 * their display name, lore and shard ID — so fusion recipes and search highlighting work there.</p>
 *
 * <p><b>Attribution:</b> the set of shard containers, the container-title formats (including the
 * {@code (page/pages)} prefix added in the 2026 Hunting update) and the shape of the Attribute Menu
 * {@code Source:} lore line were determined by studying
 * <a href="https://github.com/hannibal002/SkyHanni">SkyHanni</a>'s {@code ItemResolutionQuery}
 * (LGPL-2.1), which solves the same problem — thanks to its authors. No SkyHanni code is reproduced
 * here; the join runs against our own {@link AttributeShardData} instead of SkyHanni's bazaar-id
 * detour, which needs a repo-maintained override table because 12 of 189 shard names do not derive
 * their bazaar id.</p>
 */
public final class ShardGuiResolver {

    /** What kind of shard container is open, and therefore how a stack in it names its shard. */
    public enum Kind {
        /** Not a shard container — nothing to resolve. */
        NONE,
        /** Attribute Menu: items are attributes; the shard is named by the {@code Source:} lore line. */
        ATTRIBUTE_MENU,
        /** Hunting Box, Fusion Box, Shard Fusion: items are shards under their bare creature name. */
        SHARD_LIST,
        /**
         * Any Bazaar page. Same as {@link #SHARD_LIST} except the {@code Shard} word is required:
         * the Bazaar shows real item names, and all 189 shard items are named
         * {@code "<Creature> Shard"} — so demanding the suffix costs nothing and stops the two
         * non-shard items that share a creature name ({@code Kiwi}, {@code Pest}) from matching.
         */
        BAZAAR,
        /** Confirm Fusion: the output shard is named by the first lore line. */
        CONFIRM_FUSION
    }

    /** Leading {@code (3/7) } page marker Hypixel added to paginated shard menu titles. */
    private static final Pattern PAGE_PREFIX = Pattern.compile("^\\(\\d+/\\d+\\)\\s+");
    /** {@code Source: Hideonring Shard (R34)} — the shard an Attribute Menu entry comes from. */
    private static final Pattern SOURCE_LINE =
            Pattern.compile("^Source:\\s*(.+?)\\s+Shards?(?:\\s*\\((?:ID\\s+)?([A-Za-z]\\d{1,3})\\))?$");
    /** {@code RARE FOREST SHARD (ID R34)} — the rarity footer carried by real shard items. */
    private static final Pattern FOOTER_ID = Pattern.compile("\\(ID\\s+([A-Za-z]\\d{1,3})\\)$");
    /** Tier suffix on an Attribute Menu entry ({@code Accessory Size IV}). */
    private static final Pattern TRAILING_TIER = Pattern.compile("\\s+[IVXL]+$");

    private static final String NEW_SHARD_MARKER = "NEW SHARD";
    /** Sentinel for "resolved to nothing" — the memo is a ConcurrentMap and cannot hold nulls. */
    private static final String NO_ID = "";

    /**
     * Shard lookup tables, rebuilt whenever the constants registry instance changes (a data reload
     * republishes it). Built once from 189 entries; all three key sets are individually unique.
     */
    private record Index(Map<String, String> byShardId,
                         Map<String, String> byShardName,
                         Map<String, String> byAbilityName) {
    }

    private record IndexHolder(ConstantsRegistry source, Index index) {
    }

    /** The classified screen, published as one object so a reader never sees a torn pair. */
    private record ScreenKind(Screen screen, Kind kind) {
    }

    private static volatile IndexHolder indexHolder;
    private static volatile ScreenKind screenKind = new ScreenKind(null, Kind.NONE);

    // Identity-keyed (ItemStack has no equals/hashCode override in MC 26.1.2) and weak, so stacks
    // from a refreshed container are collected rather than pinned. Concurrent because the recipe
    // lookup and the highlighting pass can both reach it.
    private static final ConcurrentMap<ItemStack, String> RESOLVED =
            new MapMaker().weakKeys().makeMap();

    private ShardGuiResolver() {
    }

    /**
     * Resolves the NEU internal name of a shard shown in the currently open screen.
     *
     * @return the internal name (e.g. {@code "ATTRIBUTE_SHARD_ACCESSORY_SIZE;1"}), or {@code null}
     * when no shard container is open, the stack is not a known shard, or data is not loaded yet
     */
    public static String resolveCurrent(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : resolve(stack, client.screen);
    }

    /**
     * Resolves the NEU internal name of a shard shown in {@code screen}.
     *
     * @see #resolveCurrent(ItemStack)
     */
    public static String resolve(ItemStack stack, Screen screen) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Kind kind = classify(screen);
        if (kind == Kind.NONE) {
            return null;
        }
        Index index = index();
        if (index == null) {
            return null;
        }

        String cached = RESOLVED.get(stack);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String resolved = resolveUncached(stack, kind, index);
        RESOLVED.put(stack, resolved != null ? resolved : NO_ID);
        return resolved;
    }

    /**
     * Classifies the open screen, memoized on screen identity: the title regexes run once per
     * opened container, and every later call is a reference comparison.
     */
    private static Kind classify(Screen screen) {
        ScreenKind cached = screenKind;
        if (cached.screen() == screen) {
            return cached.kind();
        }
        Kind kind = classifyTitle(screen);
        screenKind = new ScreenKind(screen, kind);
        // Entries only ever describe stacks of the screen that produced them.
        RESOLVED.clear();
        return kind;
    }

    private static Kind classifyTitle(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return Kind.NONE;
        }
        return classifyTitle(TextUtil.stripColorCodes(screen.getTitle().getString()));
    }

    /** Split out from {@link #classifyTitle(Screen)} so the title formats can be tested directly. */
    static Kind classifyTitle(String strippedTitle) {
        if (strippedTitle == null || strippedTitle.isEmpty()) {
            return Kind.NONE;
        }
        String title = PAGE_PREFIX.matcher(strippedTitle).replaceFirst("");
        return switch (title) {
            case "Attribute Menu" -> Kind.ATTRIBUTE_MENU;
            case "Hunting Box", "Fusion Box", "Shard Fusion" -> Kind.SHARD_LIST;
            case "Confirm Fusion" -> Kind.CONFIRM_FUSION;
            default -> {
                // Every Bazaar page that can show a shard: the category listing
                // ("Woods & Fishes ➜ Shards"), the product page ("Shards ➜ Starborn Shard"),
                // and anything reached from the Bazaar itself — search results
                // ("Bazaar ➜ Starborn"), order management, and pages we have not seen.
                // Casting this wide is safe because a stack still has to match one of the
                // 189 known shard names to resolve; anything else falls through unchanged.
                if (title.endsWith(" Shard") || title.endsWith(" Shards") || title.contains("Bazaar")) {
                    yield Kind.BAZAAR;
                }
                yield Kind.NONE;
            }
        };
    }

    private static String resolveUncached(ItemStack stack, Kind kind, Index index) {
        List<Component> lore = lore(stack);

        String resolved = switch (kind) {
            case ATTRIBUTE_MENU -> {
                String bySource = fromSourceLine(lore, index);
                if (bySource != null) {
                    yield bySource;
                }
                // Fall back to the entry's own name, which is the ability, not the shard.
                yield fromAbilityName(stack, index);
            }
            case SHARD_LIST -> byShardName(index, stack.getHoverName().getString(), false);
            case BAZAAR -> byShardName(index, stack.getHoverName().getString(), true);
            case CONFIRM_FUSION -> {
                String fromLore = lore.isEmpty()
                        ? null
                        : byShardName(index, lore.getFirst().getString(), false);
                yield fromLore != null
                        ? fromLore
                        : byShardName(index, stack.getHoverName().getString(), false);
            }
            case NONE -> null;
        };
        if (resolved != null) {
            return resolved;
        }

        // Last resort: real shard items end their lore with "… SHARD (ID R34)". Only the final
        // line is considered, so an id mentioned mid-lore (a fusion input) can never win.
        return fromFooterId(lore, index);
    }

    private static List<Component> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines();
    }

    private static String fromSourceLine(List<Component> lore, Index index) {
        for (Component line : lore) {
            String raw = line.getString();
            // Cheap reject before the strip+regex allocation; every source line contains this.
            if (!raw.contains("Source:")) {
                continue;
            }
            Matcher matcher = SOURCE_LINE.matcher(TextUtil.stripColorCodes(raw));
            if (!matcher.matches()) {
                continue;
            }
            String shardId = matcher.group(2);
            if (shardId != null) {
                String byId = index.byShardId().get(shardId.toUpperCase(Locale.ROOT));
                if (byId != null) {
                    return byId;
                }
            }
            String byName = index.byShardName().get(normalize(matcher.group(1)));
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    private static String fromAbilityName(ItemStack stack, Index index) {
        String name = normalize(TextUtil.stripColorCodes(stack.getHoverName().getString()));
        String direct = index.byAbilityName().get(name);
        if (direct != null) {
            return direct;
        }
        // Attribute Menu entries append the unlocked tier ("Accessory Size IV"). Tried second so a
        // shard whose own name ends in a numeral (Barbarian Duke X) is never truncated.
        String withoutTier = TRAILING_TIER.matcher(name).replaceFirst("");
        return withoutTier.equals(name) ? null : index.byAbilityName().get(withoutTier);
    }

    private static String fromFooterId(List<Component> lore, Index index) {
        if (lore.isEmpty()) {
            return null;
        }
        String raw = lore.getLast().getString();
        if (!raw.contains("(ID ")) {
            return null;
        }
        Matcher matcher = FOOTER_ID.matcher(TextUtil.stripColorCodes(raw));
        return matcher.find() ? index.byShardId().get(matcher.group(1).toUpperCase(Locale.ROOT)) : null;
    }

    /**
     * Looks a displayed name up as a shard creature name.
     *
     * @param requireShardWord when {@code true}, only a name that actually ends in
     *                         {@code Shard}/{@code Shards} may match
     */
    private static String byShardName(Index index, String displayName, boolean requireShardWord) {
        String key = shardNameKey(displayName, requireShardWord);
        return key == null ? null : index.byShardName().get(key);
    }

    /**
     * Normalises a shard's displayed name to its lookup key, dropping the decorations Hypixel adds
     * around it: bazaar order prefixes ({@code SELL}/{@code BUY}), the {@code NEW SHARD} marker and
     * the trailing {@code Shard} word.
     *
     * @return the lookup key, or {@code null} when {@code requireShardWord} is set and the name
     * does not end in {@code Shard}/{@code Shards}
     */
    private static String shardNameKey(String displayName, boolean requireShardWord) {
        String plain = TextUtil.stripColorCodes(displayName);
        if (plain.startsWith("SELL ")) {
            plain = plain.substring(5);
        } else if (plain.startsWith("BUY ")) {
            plain = plain.substring(4);
        }
        if (plain.endsWith(NEW_SHARD_MARKER)) {
            plain = plain.substring(0, plain.length() - NEW_SHARD_MARKER.length()).trim();
        }
        if (plain.endsWith(" Shards")) {
            plain = plain.substring(0, plain.length() - 7);
        } else if (plain.endsWith(" Shard")) {
            plain = plain.substring(0, plain.length() - 6);
        } else if (requireShardWord) {
            return null;
        }
        return normalize(plain);
    }

    private static String normalize(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    private static Index index() {
        ConstantsRegistry registry = SkyRecipes.getConstantsRegistry();
        if (registry == null) {
            return null;
        }
        IndexHolder holder = indexHolder;
        if (holder != null && holder.source() == registry) {
            return holder.index();
        }
        Index built = buildIndex(registry);
        indexHolder = new IndexHolder(registry, built);
        return built;
    }

    private static Index buildIndex(ConstantsRegistry registry) {
        Map<String, AttributeShardData> shards = registry.getAllAttributeShards();
        int capacity = Math.max(16, shards.size() * 2);
        Map<String, String> byShardId = new HashMap<>(capacity);
        Map<String, String> byShardName = new HashMap<>(capacity);
        Map<String, String> byAbilityName = new HashMap<>(capacity);
        for (AttributeShardData shard : shards.values()) {
            String internalName = shard.internalName();
            if (internalName == null || internalName.isEmpty()) {
                continue;
            }
            putIfPresent(byShardId, shard.shardId(), internalName);
            putIfPresent(byShardName, shard.shardName(), internalName);
            putIfPresent(byAbilityName, shard.abilityName(), internalName);
        }
        return new Index(byShardId, byShardName, byAbilityName);
    }

    private static void putIfPresent(Map<String, String> map, String key, String internalName) {
        if (key != null && !key.isEmpty()) {
            map.putIfAbsent(normalize(key), internalName);
        }
    }
}
